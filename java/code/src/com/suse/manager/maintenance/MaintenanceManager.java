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

    /**
     * Save a MaintenanceSchedule
     * @param schedule the schedule
     */
    public void save(MaintenanceSchedule schedule) {
        getSession().save(schedule);
    }

    /**
     * Remove a MaintenanceSchedule
     * @param schedule the schedule
     */
    public void remove(MaintenanceSchedule schedule) {
        getSession().remove(schedule);
    }

    /**
     * Save a MaintenanceCalendar
     * @param calendar the calendar
     */
    public void save(MaintenanceCalendar calendar) {
        getSession().save(calendar);
    }

    /**
     * Remove a MaintenanceCalendar
     * @param calendar the calendar
     */
    public void remove(MaintenanceCalendar calendar) {
        getSession().remove(calendar);
    }

    /**
     * List Maintenance Schedule Names belong to the given User
     * @param user the user
     * @return a list of Schedule names
     */
    public List<String> listScheduleNamesByUser(User user) {
        @SuppressWarnings("unchecked")
        Stream<String> names = getSession()
            .createQuery("SELECT name FROM MaintenanceSchedule WHERE org = :org")
            .setParameter("org", user.getOrg())
            .stream();
        return names.collect(Collectors.toList());
    }

    /**
     * Lookup a MaintenanceSchedule by user and name
     * @param user the user
     * @param name the schedule name
     * @return Optional Maintenance Schedule
     */
    @SuppressWarnings("unchecked")
    public Optional<MaintenanceSchedule> lookupMaintenanceScheduleByUserAndName(User user, String name) {
        return getSession().createNamedQuery("MaintenanceSchedule.lookupByUserAndName")
            .setParameter("orgId", user.getOrg().getId())
            .setParameter("name", name)
            .uniqueResultOptional();
    }

    /**
     * Create a Maintenance Scheudle
     * @param user the creator
     * @param name the schedule name
     * @param type the schedule type
     * @param calendar and optional Maintenance Calendar
     * @return the created Maintenance Schedule
     */
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

    /**
     * List Maintenance Calendar Labels belonging to the given User
     * @param user the user
     * @return a list of Calender labels
     */
    public List<String> listCalendarLabelsByUser(User user) {
        Stream<String> labels = getSession()
                .createQuery("SELECT label FROM MaintenanceCalendar WHERE org = :org")
                .setParameter("org", user.getOrg())
                .stream();
        return labels.collect(Collectors.toList());
    }

    /**
     * Lookup Maintenance Calendar by User and Label
     * @param user the user
     * @param label the label of the calendar
     * @return Optional Maintenance Calendar
     */
    public Optional<MaintenanceCalendar> lookupCalendarByUserAndLabel(User user, String label) {
        return getSession().createNamedQuery("MaintenanceCalendar.lookupByUserAndName")
                .setParameter("orgId", user.getOrg().getId())
                .setParameter("label", label).uniqueResultOptional();
    }

    /**
     * Create a MaintenanceCalendar
     * @param user the creator
     * @param label the label for the calendar
     * @param ical the Calendar data in ICal format
     * @return the created Maintenance Calendar
     */
    public MaintenanceCalendar createMaintenanceCalendar(User user, String label, String ical) {
        MaintenanceCalendar mc = new MaintenanceCalendar();
        mc.setOrg(user.getOrg());
        mc.setLabel(label);
        mc.setIcal(ical);
        save(mc);
        return mc;
    }

}
