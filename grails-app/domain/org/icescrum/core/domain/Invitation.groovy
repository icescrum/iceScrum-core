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

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.domain.security.Authority

class Invitation implements Serializable {

    InvitationType type
    String email
    Date dateCreated
    Integer role
    String token

    static belongsTo = [team: Team, product: Product]

    static transients = ['userMock']

    static constraints = {
        email(blank: false, email: true)
        team(nullable: true)
        product(nullable: true)
        type(validator: { newType, Invitation -> newType == InvitationType.TEAM         && Invitation.team != null && Invitation.product == null ||
                newType == InvitationType.PRODUCT      && Invitation.team == null && Invitation.product != null
        })
        role(validator: { newRole, Invitation -> newRole in [Authority.MEMBER, Authority.SCRUMMASTER]       && Invitation.team != null && Invitation.product == null ||
                newRole in [Authority.STAKEHOLDER, Authority.PRODUCTOWNER] && Invitation.team == null && Invitation.product != null
        })
    }

    static mapping = {
        cache true
        table 'icescrum2_invitation'
    }

    static namedQueries = {
        // Needs to be implemented manually because grails 1.3.9 prevents using dynamic finders with more than 2 elements
        findAllByTypeAndProductAndRole { InvitationType type, Product product, Integer role ->
            eq 'type', type
            eq 'product', product
            eq 'role', role
        }
        // Needs to be implemented manually because grails 1.3.9 prevents using dynamic finders with more than 2 elements
        findAllByTypeAndTeamAndRole { InvitationType type, Team team, Integer role ->
            eq 'type', type
            eq 'team', team
            eq 'role', role
        }
    }

    enum InvitationType {
        TEAM, PRODUCT
    }

    def beforeValidate() {
        token = email.encodeAsMD5()
    }

    Map getUserMock() {
        return getUserMock(email)
    }

    static Map getUserMock(String email) {
        def is = ApplicationHolder.application.mainContext.getBean('org.icescrum.core.taglib.ScrumTagLib')
        return [id: email, name: email, activity: '', avatar: is.avatar([user:[email: email, id: email],link:true]), isInvited: true]
    }
}