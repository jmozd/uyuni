/**
 * Copyright (c) 2016 SUSE LLC
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package com.redhat.rhn.taskomatic.task.sshpush;

import com.redhat.rhn.common.db.datasource.DataResult;
import com.redhat.rhn.common.hibernate.HibernateFactory;
import com.redhat.rhn.domain.action.Action;
import com.redhat.rhn.domain.action.ActionFactory;
import com.redhat.rhn.domain.action.server.ServerAction;
import com.redhat.rhn.domain.server.MinionServer;
import com.redhat.rhn.domain.server.MinionServerFactory;
import com.redhat.rhn.frontend.dto.SystemPendingEventDto;
import com.redhat.rhn.manager.action.ActionManager;
import com.redhat.rhn.manager.system.SystemManager;
import com.redhat.rhn.taskomatic.task.threaded.QueueWorker;
import com.redhat.rhn.taskomatic.task.threaded.TaskQueue;

import com.suse.manager.reactor.messaging.JobReturnEventMessageAction;
import com.suse.manager.webui.services.SaltServerActionService;
import com.suse.manager.webui.services.impl.SaltService;
import com.suse.salt.netapi.calls.LocalCall;
import com.suse.salt.netapi.calls.modules.Test;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;

import org.apache.log4j.Logger;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static com.redhat.rhn.domain.action.ActionFactory.STATUS_COMPLETED;
import static com.redhat.rhn.domain.action.ActionFactory.STATUS_FAILED;
import static java.util.Optional.ofNullable;

/**
 * SSH push worker executing scheduled actions via Salt SSH.
 */
public class SSHPushWorkerSalt implements QueueWorker {

    private Logger log;
    private SSHPushSystem system;
    private TaskQueue parentQueue;

    private boolean packageListRefreshNeeded = false;
    private SaltService saltService;

    /**
     * Constructor.
     * @param logger Logger for this instance
     * @param systemIn the system to work with
     */
    public SSHPushWorkerSalt(Logger logger, SSHPushSystem systemIn) {
        log = logger;
        system = systemIn;
        saltService = SaltService.INSTANCE;
    }

    /**
     * Constructor.
     * @param logger Logger for this instance
     * @param systemIn the system to work with
     * @param saltServiceIn the salt service to work with
     */
    public SSHPushWorkerSalt(Logger logger, SSHPushSystem systemIn,
            SaltService saltServiceIn) {
        log = logger;
        system = systemIn;
        saltService = saltServiceIn;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setParentQueue(TaskQueue queue) {
        parentQueue = queue;
    }

    /**
     * Get pending actions for the given minion server and execute those where the schedule
     * date and time has come.
     */
    @Override
    public void run() {
        try {
            parentQueue.workerStarting();

            MinionServerFactory.lookupById(system.getId()).ifPresent(m -> {
                log.info("Executing actions for minion: " + m.getMinionId());

                // Get pending actions and reverse to put them in ascending order
                // TODO: consider prerequisites
                DataResult<SystemPendingEventDto> pendingEvents = SystemManager
                        .systemPendingEvents(m.getId(), null);
                Collections.reverse(pendingEvents);
                log.debug("Number of pending actions: " + pendingEvents.size());
                boolean checkinNeeded = true;

                for (SystemPendingEventDto event : pendingEvents) {
                    log.debug("Looking at pending action: " + event.getActionName());

                    ZonedDateTime now = ZonedDateTime.now();
                    ZonedDateTime scheduleDate = event.getScheduledFor().toInstant()
                            .atZone(ZoneId.systemDefault());

                    if (scheduleDate.isAfter(now)) {
                        // Nothing left to do at the moment, get out of here
                        break;
                    }
                    else {
                        log.info("Executing action (id=" + event.getId() + "): " +
                                event.getActionName());
                        Action action = ActionFactory.lookupById(event.getId());
                        try {
                            executeAction(action, m);
                        }
                        catch (Exception e) {
                            log.error("Error executing action: " + e.getMessage(), e);
                        }
                        checkinNeeded = false;
                    }
                }

                // Perform a package profile update in the end if needed
                if (packageListRefreshNeeded) {
                    Action pkgList = ActionManager.schedulePackageRefresh(m.getOrg(), m);
                    executeAction(pkgList, m);
                }

                // Perform a check-in if there is no pending actions
                if (checkinNeeded) {
                    performCheckin(m);
                }

                log.debug("Nothing left to do for " + m.getMinionId() + ", exiting worker");
            });
        }
        catch (Exception e) {
            log.error(e.getMessage(), e);
            HibernateFactory.rollbackTransaction();
        }
        finally {
            parentQueue.workerDone();
            HibernateFactory.closeSession();

            // Finished talking to this system
            SSHPushDriver.getCurrentSystems().remove(system);
        }
    }

    private void performCheckin(MinionServer minion) {
        // Ping minion and perform check-in on success
        log.info("Performing a check-in for: " + minion.getMinionId());
        Optional<Boolean> result = SaltService.INSTANCE
                .callSync(Test.ping(), minion.getMinionId());
        if (result.isPresent()) {
            minion.updateServerInfo();
        }
    }

    /**
     * Execute action on minion.
     *
     * @param action the action to be executed
     * @param minion minion on which the action will be executed
     */
    public void executeAction(Action action, MinionServer minion) {
        Optional<ServerAction> serverAction = action.getServerActions().stream()
                .filter(sa -> sa.getServer().equals(minion))
                .findFirst();
        serverAction.ifPresent(sa -> {
            if (sa.getStatus().equals(STATUS_FAILED) ||
                    sa.getStatus().equals(STATUS_COMPLETED)) {
                log.info("Action '" + action.getName() + "' is completed or failed." +
                        " Skipping.");
                return;
            }

            if (prerequisiteFailed(sa)) {
                log.info("Failing action '" + action.getName() + "' as its prerequisity '" +
                                action.getPrerequisite().getName() + "' failed.");
                sa.setStatus(STATUS_FAILED);
                sa.setResultMsg("Prerequisite failed.");
                sa.setResultCode(-100L);
                sa.setCompletionTime(new Date());
                return;
            }

            if (sa.getRemainingTries() < 1) {
                log.info("NOT executing and failing action '" + action.getName() + "' as" +
                        " the maximum number of re-trials has been reached.");
                sa.setStatus(STATUS_FAILED);
                sa.setResultMsg("Action has been picked up multiple times" +
                        " without a successful transaction;" +
                        " This action is now failed for this system.");
                sa.setCompletionTime(new Date());
                return;
            }

            sa.setRemainingTries(sa.getRemainingTries() - 1);

            Map<LocalCall<?>, List<MinionServer>> calls = SaltServerActionService.INSTANCE
                    .callsForAction(action, Arrays.asList(minion));

            calls.keySet().forEach(call -> {
                Optional<JsonElement> result;
                // try-catch as we'd like to log the warning in case of exception
                try {
                    result = saltService
                            .callSync(new JsonElementCall(call), minion.getMinionId());
                }
                catch (RuntimeException e) {
                    log.warn("Exception for salt call for action: '" + action.getName() +
                            "'. Will be re-tried " +  sa.getRemainingTries() + " times");
                    throw e;
                }

                if (!result.isPresent()) {
                    log.error("No result for salt call for action: '" + action.getName() +
                            "'. Will be re-tried " +  sa.getRemainingTries() + " times");
                }

                result.ifPresent(r -> {
                    if (log.isTraceEnabled()) {
                        log.trace("Salt call result: " + r);
                    }
                    String function = (String) call.getPayload().get("fun");
                    updateServerAction(sa, r, function);

                    // Perform a package profile update in the end if necessary
                    if (shouldRefreshPackageList(function, result)) {
                        log.info("Scheduling a package profile update");
                        this.packageListRefreshNeeded = true;
                    }

                    // Perform a "check-in" after every executed action
                    minion.updateServerInfo();
                });
            });
        });
    }

    /**
     * Checks whether the package list should be refreshed based on the function called
     * and the result (in case of state.apply call).
     *
     * @param function the function called
     * @param result the call result
     * @return true if the package list should be refreshed
     */
    public boolean shouldRefreshPackageList(String function, Optional<JsonElement> result) {
        return JobReturnEventMessageAction
                .shouldRefreshPackageList(function, result);
    }

    /**
     * Update server action based on the result of the call.
     *
     * @param serverAction server action to be updated
     * @param result the call result
     * @param function the function called
     */
    public void updateServerAction(ServerAction serverAction, JsonElement result,
            String function) {
        JobReturnEventMessageAction.updateServerAction(serverAction, 0L, true, "n/a",
                result, function);
    }

    /**
     * Checks whether the parent action of given server action contains a failed server
     * action that is associated with the server of given server action.
     * @param serverAction server action
     * @return true if there exists a failed server action associated with the same server
     * as serverAction and parent action of serverAction
     */
    private boolean prerequisiteFailed(ServerAction serverAction) {
        Optional<Stream<ServerAction>> prerequisites =
                ofNullable(serverAction.getParentAction())
                        .map(Action::getPrerequisite)
                        .map(Action::getServerActions)
                        .map(a -> a.stream());

        return prerequisites
                .flatMap(serverActions ->
                        serverActions
                                .filter(s ->
                                        serverAction.getServer().equals(s.getServer()) &&
                                                STATUS_FAILED.equals(s.getStatus()))
                                .findAny())
                .isPresent();
    }

    /**
     * Manipulate a given {@link LocalCall} object to return a {@link JsonElement} instead
     * of the specified return type.
     */
    private class JsonElementCall extends LocalCall<JsonElement> {

        @SuppressWarnings("unchecked")
        JsonElementCall(LocalCall<?> call) {
            super((String) call.getPayload().get("fun"),
                    ofNullable((List<?>) call.getPayload().get("arg")),
                    ofNullable((Map<String, ?>) call.getPayload().get("kwarg")),
                    new TypeToken<JsonElement>() { });
        }
    }
}
