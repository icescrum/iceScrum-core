/*
 * Copyright (c) 2015 Kagilum SAS
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 */


package org.icescrum.core.domain

import org.icescrum.core.utils.ServicesUtils

class TimeBox implements Comparable<TimeBox>, Serializable {

    static final long serialVersionUID = -88023090297032383L

    Date startDate
    Date endDate
    String goal
    int orderNumber = 0
    String description
    Date dateCreated
    Date todoDate = new Date()
    Date lastUpdated

    Set<MetaData> metaDatas
    SortedSet<Activity> activities

    static hasMany = [
            cliches   : Cliche,
            metaDatas : MetaData,
            activities: Activity
    ]

    static mappedBy = [
            cliches: "parentTimeBox"
    ]

    static constraints = {
        goal(nullable: true)
        description(nullable: true)
    }

    static transients = ['duration']

    static mapping = {
        cache true
        table 'is_timebox'
        goal type: 'text'
        description type: 'text'
        tablePerHierarchy false
        cliches cascade: 'all-delete-orphan', cache: true
        metaDatas cascade: 'delete-orphan', cache: true
        activities cascade: 'delete-orphan', cache: true
        sort:
        orderNumber: 'asc'
    }

    def beforeValidate() {
        startDate = startDate.clearTime()
        endDate = endDate.clearTime()
        goal = ServicesUtils.cleanXml(goal)
    }

    Integer getDuration() {
        def days = 0
        if (startDate != null && endDate != null) {
            days = this.endDate - this.startDate + 1
        }
        return days
    }

    int compareTo(TimeBox o) {
        return orderNumber <=> o?.orderNumber ?: id <=> o?.id
    }

    static List<String> validateStartDate(Date newStartDate, Date endDate) {
        if (newStartDate == endDate) {
            return ['equals.endDate']
        } else if (newStartDate.after(endDate)) {
            return ['after.endDate']
        }
        return []
    }

    static List<String> validateEndDate(Date startDate, Date newEndDate) {
        if (newEndDate == startDate) {
            return ['equals.startDate']
        } else if (newEndDate.before(startDate)) {
            return ['before.startDate']
        }
        return []
    }
}
