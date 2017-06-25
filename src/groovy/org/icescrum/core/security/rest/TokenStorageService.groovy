/*
 * Copyright (c) 2017 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 */
package org.icescrum.core.security.rest;

import org.icescrum.core.domain.security.UserToken
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService

class TokenStorageService {

    UserDetailsService userDetailsService

    UserDetails loadUserByToken(String tokenValue) throws TokenNotFoundException {
        String username = UserToken.createCriteria().get {
            eq("id", tokenValue)
            projections {
                user {
                    property('username', 'username')
                }
            }
        }
        if (username) {
            return userDetailsService.loadUserByUsername(username)
        }
        throw new TokenNotFoundException("Token ${tokenValue} not found")
    }
}