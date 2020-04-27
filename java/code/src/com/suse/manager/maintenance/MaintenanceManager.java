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
package com.suse.manager.maintenance;

import static com.redhat.rhn.common.hibernate.HibernateFactory.getSession;

import com.redhat.rhn.domain.user.User;

import com.suse.manager.model.maintenance.MaintenanceSchedule;
import com.suse.manager.model.maintenance.MaintenanceSchedule.ScheduleType;
import com.suse.manager.model.maintenance.MaintenanceCalendar;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * MaintenanceManager
 */
public class MaintenanceManager {

    private static volatile MaintenanceManager instance = null;

    /**
     * Instantiate Maintenance Manager object
     *
     * @return MaintenanceManager object
     */
    public static MaintenanceManager instance() {
        if (instance == null) {
            synchronized (MaintenanceManager.class) {
                if (instance == null) {
                    instance = new MaintenanceManager();
                }
            }
        }
        return instance;
    }

    public void save(MaintenanceSchedule schedule) {
        getSession().save(schedule);
    }

    public void remove(MaintenanceSchedule schedule) {
        getSession().remove(schedule);
    }

    public void save(MaintenanceCalendar scheduleData) {
        getSession().save(scheduleData);
    }

    public void remove(MaintenanceCalendar scheduleData) {
        getSession().remove(scheduleData);
    }

    public List<String> listScheduleNamesByUser(User user) {
        @SuppressWarnings("unchecked")
        Stream<String> names = getSession()
            .createQuery("SELECT name FROM MaintenanceSchedule WHERE org = :org")
            .setParameter("org", user.getOrg())
            .stream();
        return names.collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    public Optional<MaintenanceSchedule> lookupMaintenanceScheduleByUserAndName(User user, String name) {
        return getSession().createNamedQuery("MaintenanceSchedule.lookupByUserAndName")
            .setParameter("orgId", user.getOrg().getId())
            .setParameter("name", name)
            .uniqueResultOptional();
    }

    public MaintenanceSchedule createMaintenanceSchedule(User user, String name, ScheduleType type,
            Optional<MaintenanceCalendar> calendar) {
        MaintenanceSchedule ms = new MaintenanceSchedule();
        ms.setOrg(user.getOrg());
        ms.setName(name);
        ms.setScheduleType(type);
        calendar.ifPresent(ms::setCalendar);
        save(ms);
        return ms;
    }

    public List<String> listCalendarLabelsByUser(User user) {
        Stream<String> labels = getSession()
                .createQuery("SELECT label FROM MaintenanceCalendar WHERE org = :org")
                .setParameter("org", user.getOrg())
                .stream();
        return labels.collect(Collectors.toList());
    }

    public Optional<MaintenanceCalendar> lookupCalendarByUserAndLabel(User user, String label) {
        return getSession().createNamedQuery("MaintenanceCalendar.lookupByUserAndName")
                .setParameter("orgId", user.getOrg().getId())
                .setParameter("label", label).uniqueResultOptional();
    }

    public MaintenanceCalendar createMaintenanceCalendar(User user, String label, String ical) {
        MaintenanceCalendar mc = new MaintenanceCalendar();
        mc.setOrg(user.getOrg());
        mc.setLabel(label);
        mc.setIcal(ical);
        save(mc);
        return mc;
    }

}
