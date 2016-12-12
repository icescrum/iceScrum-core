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

    static belongsTo = [team: Team, product: Product]

    static transients = ['userMock']

    static constraints = {
        email(blank: false, email: true)
        team(nullable: true)
        product(nullable: true)
        type(validator: { newType, Invitation -> newType == InvitationType.TEAM         && Invitation.team != null && Invitation.product == null ||
                                                 newType == InvitationType.PRODUCT      && Invitation.team == null && Invitation.product != null ?: 'invalid'})
        futureRole(validator: { newRole, Invitation -> newRole in [Authority.MEMBER,      Authority.SCRUMMASTER]  && Invitation.team != null && Invitation.product == null ||
                                                       newRole in [Authority.STAKEHOLDER, Authority.PRODUCTOWNER] && Invitation.team == null && Invitation.product != null ?: 'invalid'})
    }

    static mapping = {
        cache true
        table 'is_invitation'
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
        def emailPrefix = email.split('@')[0]
        def firstName = emailPrefix
        def lastName = ""
        def dotPosition = emailPrefix.indexOf('.')
        if (dotPosition != -1) {
            firstName = emailPrefix.substring(0, dotPosition)?.capitalize()
            lastName = emailPrefix.substring(dotPosition + 1)?.capitalize()
        }
        return [id: null, firstName: firstName, lastName: lastName, email: email]
    }

    def xml(builder) {
        builder.invitation() {
            builder.type(this.type)
            builder.email(this.email)
            builder.dateCreated(this.dateCreated)
            builder.futurRole(this.futureRole)
            builder.token(this.token)
            exportDomainsPlugins(builder)
        }
    }
}