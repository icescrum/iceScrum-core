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

class Invitation implements Serializable {

    InvitationType type
    String email
    Date dateCreated
    Integer role
    String token

    static belongsTo = [team: Team, product: Product]

    static constraints = {
        email(blank: false, email: true)
        team(nullable: true)
        product(nullable: true)
        type(validator: { newType, Invitation -> newType == InvitationType.TEAM         && Invitation.team != null && Invitation.product == null ||
                                                 newType == InvitationType.PRODUCT      && Invitation.team == null && Invitation.product != null ||
                                                 newType == InvitationType.TEAM_PRODUCT && Invitation.team != null && Invitation.product != null
        }) // TODO custom message
    }

    static mapping = {
        cache true
        table 'icescrum2_invitation'
    }

    enum InvitationType {
        TEAM, PRODUCT, TEAM_PRODUCT
    }

    def beforeValidate() {
        token = email.encodeAsMD5()
    }
}