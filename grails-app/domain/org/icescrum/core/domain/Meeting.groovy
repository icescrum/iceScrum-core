/*
 * Copyright (c) 2018 Kagilum SAS.
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException

class Meeting implements Serializable {

    String subject
    String videoLink
    String phone
    String pinCode
    String provider
    String providerEventId

    Date startDate
    Date endDate

    Long contextId
    String contextType

    static belongsTo = [
            owner    : User,
            project  : Project,
            portfolio: Portfolio
    ]

    Date dateCreated
    Date lastUpdated

    static constraints = {
        subject blank: false
        videoLink nullable: true
        phone nullable: true
        pinCode nullable: true
        providerEventId nullable: true

        project nullable: true
        portfolio nullable: true

        endDate nullable: true

        contextId nullable: true
        contextType nullable: true
    }

    static mapping = {
        cache true
    }

    static List<Meeting> withMeetings(def params, def id = 'id', String workspaceType = WorkspaceType.PROJECT) {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Meeting> meetings = ids ? getAll(ids).findAll { Meeting meeting ->
            meeting && (meeting."$workspaceType".id == params."$workspaceType".toLong())
        } : null
        if (!meetings) {
            throw new ObjectNotFoundException(ids, 'Meeting')
        }
        return meetings
    }

    int hashCode() {
        final int prime = 32
        int result = 1
        result = prime * result + ((!subject) ? 0 : subject.hashCode())
        result = prime * result + ((!phone) ? 0 : phone.hashCode())
        result = prime * result + ((!videoLink) ? 0 : videoLink.hashCode())
        return result
    }

}