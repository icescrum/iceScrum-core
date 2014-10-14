/*
 * Copyright (c) 2010 iceScrum Technologies.
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

import grails.plugin.fluxiable.Fluxiable

class TimeBox implements Comparable<TimeBox>, Fluxiable, Serializable {

    static final long serialVersionUID = -88023090297032383L

    Date startDate
    Date endDate
    String goal
    int orderNumber = 0
    String description
    Date dateCreated
    Date lastUpdated

    static hasMany = [
            cliches: Cliche
    ]

    static mappedBy = [
            cliches: "parentTimeBox"
    ]

    static constraints = {
        startDate(validator:{ val, obj ->
            if(val == obj.endDate){
                return ['equals.endDate']
            }
            if(val.after(obj.endDate)){
                return ['after.endDate']
            }
            return true
        })
        endDate(validator:{ val, obj ->
            if(val == obj.startDate){
                return ['equals.startDate']
            }
            if(val.before(obj.startDate)){
                return ['before.startDate']
            }
            return true
        })
        goal(nullable: true)
        description(nullable: true)
    }

    static transients = ['duration']

    static mapping = {
        cache true
        table 'icescrum2_timebox'
        goal type: 'text'
        description type: 'text'
        tablePerHierarchy false
        cliches cascade: 'all-delete-orphan', sort: 'datePrise', order: 'asc', cache: true
        sort: 'orderNumber'
    }

    Integer getDuration() {
        def days = 0
        if (startDate != null && endDate != null){
            days = this.endDate - this.startDate + 1
        }
        return days
    }

    int compareTo(TimeBox o) {
        return orderNumber <=> o?.orderNumber
    }
}
