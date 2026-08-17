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
package org.icescrum.core.security.rest

import org.springframework.security.authentication.AuthenticationProvider
import org.springframework.security.authentication.AccountExpiredException
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.userdetails.UserDetails

class TokenAuthenticationProvider implements AuthenticationProvider {

    TokenStorageService tokenStorageService

    @Override
    Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!supports(authentication.class)) {
            throw new IllegalArgumentException("Only RestAuthenticationToken is supported")
        }
        RestAuthenticationToken authToken = (RestAuthenticationToken) authentication
        if (authToken.token) {
            UserDetails userDetails = tokenStorageService.loadUserByToken(authToken.getToken())
            checkAccountStatus(userDetails)
            // A bearer token is the credential. Do not retain the password hash in the security context.
            authToken = new RestAuthenticationToken(userDetails, null, userDetails.getAuthorities(), authToken.getToken())
        }
        return authToken
    }

    private static void checkAccountStatus(UserDetails userDetails) {
        if (!userDetails.accountNonLocked) {
            throw new LockedException('User account is locked')
        }
        if (!userDetails.enabled) {
            throw new DisabledException('User account is disabled')
        }
        if (!userDetails.accountNonExpired) {
            throw new AccountExpiredException('User account has expired')
        }
        if (!userDetails.credentialsNonExpired) {
            throw new CredentialsExpiredException('User credentials have expired')
        }
    }

    @Override
    boolean supports(Class<?> authentication) {
        return RestAuthenticationToken.class.isAssignableFrom(authentication)
    }
}
