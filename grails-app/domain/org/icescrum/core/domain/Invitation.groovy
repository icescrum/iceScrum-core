/*
 * Copyright (c) 2015 Kagilum SAS.
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

import org.icescrum.core.domain.security.Authority

class Invitation implements Serializable {

    InvitationType type
    String email
    Date dateCreated
    Integer futureRole // don't use "role" because it is a reserved keyword in some SQL dialects
    String token

    static belongsTo = [team: Team, project: Project, portfolio: Portfolio]

    static constraints = {
        email(blank: false, email: true)
        team(nullable: true)
        project(nullable: true)
        portfolio(nullable: true)
        type(validator: { newType, Invitation ->
            newType == InvitationType.TEAM && Invitation.team != null && Invitation.project == null && Invitation.portfolio == null ||
            newType == InvitationType.PROJECT && Invitation.team == null && Invitation.portfolio == null && Invitation.project != null ||
            newType == InvitationType.PORTFOLIO && Invitation.team == null && Invitation.project == null && Invitation.portfolio != null ?: 'invalid'
        })
        futureRole(validator: { newRole, Invitation ->
            newRole in [Authority.MEMBER, Authority.SCRUMMASTER] && Invitation.team != null && Invitation.project == null && Invitation.portfolio == null ||
            newRole in [Authority.STAKEHOLDER, Authority.PRODUCTOWNER] && Invitation.team == null && Invitation.portfolio == null && Invitation.project != null ||
            newRole in [Authority.STAKEHOLDER, Authority.PRODUCTOWNER] && Invitation.portfolio != null && Invitation.team == null && Invitation.project == null ?: 'invalid' //TODO
        })
    }

    static mapping = {
        cache true
        table 'is_invitation'
    }

    enum InvitationType {
        TEAM, PROJECT, PORTFOLIO
    }

    def beforeValidate() {
        token = email.encodeAsMD5()
    }
}
