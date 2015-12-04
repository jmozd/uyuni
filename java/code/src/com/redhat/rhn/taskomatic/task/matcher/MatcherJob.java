/**
 * Copyright (c) 2015 SUSE LLC
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

package com.redhat.rhn.taskomatic.task.matcher;

import com.redhat.rhn.taskomatic.task.RhnJavaJob;
import com.redhat.rhn.taskomatic.task.gatherer.GathererJob;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * Taskomatic job for running subscription matcher and processing its results.
 */
public class MatcherJob extends RhnJavaJob {

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute(JobExecutionContext jobExecutionContext)
            throws JobExecutionException {
        if (jobExecutionContext.getJobDetail().getJobDataMap()
                .containsKey(GathererJob.VHM_LABEL)) {
            log.warn("Gatherer-matcher bunch was run for a specific Virtual Host " +
                    "Manager. NOT running matcher.");
            return;
        }

        log.warn("TODO: run matcher here!");
    }

}
