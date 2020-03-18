/*
 * Copyright (c) 2020 Kagilum SAS.
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

    String topic
    String videoLink
    String phone
    String pinCode
    String provider
    String providerEventId

    Date startDate
    Date endDate

    Long subjectId
    String subjectType

    static belongsTo = [
            owner    : User,
            project  : Project,
            portfolio: Portfolio
    ]

    Date dateCreated
    Date lastUpdated

    static constraints = {
        topic blank: false
        videoLink nullable: true
        phone nullable: true
        pinCode nullable: true
        providerEventId nullable: true
        project(nullable: true, validator: { newProject, meeting ->
            (meeting.portfolio && !newProject) || (!meeting.portfolio && newProject) ?: 'invalid'
        })
        portfolio nullable: true
        endDate nullable: true
        subjectId nullable: true
        subjectType nullable: true
    }

    static mapping = {
        cache true
        table 'is_meeting'
    }

    static Meeting withMeeting(long workspaceId, long id, String workspaceType = WorkspaceType.PROJECT) {
        Meeting meeting
        if (workspaceType == WorkspaceType.PROJECT) {
            def project = Project.load(workspaceId)
            if (project) {
                meeting = (Meeting) findByProjectAndId(project, id)
            }
        } else if (workspaceType == WorkspaceType.PORTFOLIO) {
            def portfolio = Portfolio.load(workspaceId)
            if (portfolio) {
                meeting = (Meeting) findByPortfolioAndId(portfolio, id)
            }
        }
        if (!meeting) {
            throw new ObjectNotFoundException(id, 'Meeting')
        }
        return meeting
    }

    int hashCode() {
        final int prime = 32
        int result = 1
        result = prime * result + ((!topic) ? 0 : topic.hashCode())
        result = prime * result + ((!phone) ? 0 : phone.hashCode())
        result = prime * result + ((!videoLink) ? 0 : videoLink.hashCode())
        return result
    }

}