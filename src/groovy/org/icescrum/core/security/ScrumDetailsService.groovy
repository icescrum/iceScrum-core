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
 */
package org.icescrum.core.security

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.userdetails.GrailsUserDetailsService;
import org.icescrum.core.domain.security.Authority
import org.slf4j.LoggerFactory
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import grails.transaction.Transactional

class ScrumDetailsService implements GrailsUserDetailsService {

    def grailsApplication
    static final List NO_ROLES = [new SimpleGrantedAuthority(Authority.ROLE_USER)]

    UserDetails loadUserByUsername(String username, boolean loadRoles)
            throws UsernameNotFoundException {
        return loadUserByUsername(username)
    }

    @Transactional(readOnly=true, noRollbackFor=[IllegalArgumentException, UsernameNotFoundException])
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        def user = findUser(username, false)

        if (!user && grailsApplication.mainContext['ldapUserDetailsMapper']?.isEnabled()){
            user = findUser(username, true)
        }

        if (!user){
            LoggerFactory.getLogger(getClass()).warn "User not found: $username"
            throw new UsernameNotFoundException('User not found', username)
        }

        def authorities = user.authorities.collect {
            new SimpleGrantedAuthority(it.authority)
        }

        return new ScrumUserDetails(user.username, user.password,
                user.enabled, !user.accountExpired, !user.passwordExpired,
                !user.accountLocked, authorities ?: NO_ROLES, user.id,
                user.firstName + " " + user.lastName)
    }

    def findUser(username, external){
        def conf = SpringSecurityUtils.securityConfig
        Class<?> User = grailsApplication.getDomainClass(conf.userLookup.userDomainClassName).clazz
        return User.createCriteria().get {
            or {
                eq conf.userLookup.usernamePropertyName, username
                eq 'email', username.toLowerCase()
            }
            eq('accountExternal', external)
            cache true
        }
    }
}
