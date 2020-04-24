/**
 * Copyright (c) 2020 SUSE LLC
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
package com.suse.manager.xmlrpc.maintenance;

import com.redhat.rhn.domain.user.User;
import com.redhat.rhn.frontend.xmlrpc.BaseHandler;
import com.redhat.rhn.frontend.xmlrpc.EntityNotExistsFaultException;

import com.suse.manager.maintenance.MaintenanceManager;
import com.suse.manager.model.maintenance.MaintenanceSchedule;
import com.suse.manager.model.maintenance.MaintenanceSchedule.ScheduleType;

import java.util.List;
import java.util.Optional;

/**
 * Maintenance Schedule XMLRPC Handler
 *
 * @xmlrpc.namespace maintenance
 * @xmlrpc.doc Provides methods to access and modify Maintenance Schedules related entities
 */
public class MaintenanceHandler extends BaseHandler {

    private final MaintenanceManager mm = MaintenanceManager.instance();

    /**
     * List Schedule Names visible to user
     *
     * @param loggedInUser the user
     * @return list of schedule names
     *
     * @xmlrpc.doc List Schedule Names visible to user
     * @xmlrpc.param #session_key()
     * @xmlrpc.returntype #array_single("string", "maintenance schedule names")
     */
    public List<String> listScheduleNames(User loggedInUser) {
        return mm.listScheduleNamesByUser(loggedInUser);
    }

    /**
     * Lookup a specific Maintenance Schedule
     *
     * @param loggedInUser the user
     * @param name schedule name
     * @throws EntityNotExistsFaultException when Maintenance Schedule does not exist
     * @return the Maintenance Schedule
     *
     * @xmlrpc.doc Lookup a specific Maintenance Schedule
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "name", "Maintenance Schedule Name")
     * @xmlrpc.returntype
     * #array_begin()
     * $MaintenanceScheduleSerializer
     * #array_end()
     */
    public MaintenanceSchedule lookupSchedule(User loggedInUser, String name) {
        return mm.lookupMaintenanceScheduleByUserAndName(loggedInUser, name)
                .orElseThrow(() -> new EntityNotExistsFaultException(name));
    }

    /**
     * Create a new Maintenance Schedule
     *
     * @param loggedInUser the user
     * @param name schedule name
     * @param type schedule type
     * @return the new Maintenance Schedule
     *
     * @xmlrpc.doc Create a new Maintenance Schedule
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "name", "Maintenance Schedule Name")
     * @xmlrpc.param #param_desc("string", "type", "Schedule type: single, multi")
     * @xmlrpc.returntype
     * #array_begin()
     * $MaintenanceScheduleSerializer
     * #array_end()
     */
    public MaintenanceSchedule createSchedule(User loggedInUser, String name, String type) {
        return mm.createMaintenanceSchedule(loggedInUser, name, ScheduleType.lookupByLabel(type),
                Optional.empty());
    }

    /**
     * Update a Maintenance Schedule
     *
     * @param loggedInUser the user
     * @param name schedule name
     * @return the changed Maintenance Schedule
     *
     * @xmlrpc.doc Update a Maintenance Schedule
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "name", "Maintenance Schedule Name")
     * @xmlrpc.returntype
     * #array_begin()
     * $MaintenanceScheduleSerializer
     * #array_end()
     */
    public MaintenanceSchedule updateSchedule(User loggedInUser, String name) {
        // TODO: needs implementation
        return null;
    }

    /**
     * Remove a Maintenance Schedule
     *
     * @param loggedInUser the user
     * @param name schedule name
     * @throws EntityNotExistsFaultException when Maintenance Schedule does not exist
     * @return number of removed objects
     *
     * @xmlrpc.doc Remove a Maintenance Schedule
     * @xmlrpc.param #session_key()
     * @xmlrpc.param #param_desc("string", "name", "Maintenance Schedule Name")
     * @xmlrpc.returntype #return_int_success()
     */
    public int removeSchedule(User loggedInUser, String name) {
        Optional<MaintenanceSchedule> schedule = mm.lookupMaintenanceScheduleByUserAndName(loggedInUser, name);
        mm.remove(schedule.orElseThrow(() -> new EntityNotExistsFaultException(name)));
        return 1;
    }
}
