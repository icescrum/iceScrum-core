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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */


package org.icescrum.core.security;


import org.codehaus.groovy.grails.plugins.springsecurity.GormUserDetailsService

import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.security.core.GrantedAuthority

import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.springframework.security.core.authority.GrantedAuthorityImpl
import org.icescrum.core.domain.security.Authority

class ScrumDetailsService extends GormUserDetailsService {


    UserDetails loadUserByUsername(String username, boolean loadRoles) throws UsernameNotFoundException {
        def conf = SpringSecurityUtils.securityConfig
        Class<?> User = grailsApplication.getDomainClass(conf.userLookup.userDomainClassName).clazz

        User.withTransaction { status ->
            def user = User.findWhere((conf.userLookup.usernamePropertyName): username, accountExternal:false)

            //Will be valid only on reauthenticate context
            if (!user && grailsApplication.mainContext['ldapUserDetailsMapper']?.isEnabled()){
                user = User.findWhere((conf.userLookup.usernamePropertyName): username, accountExternal:true)
            }
            if (!user){
                log.warn "User not found: $username"
                throw new UsernameNotFoundException('User not found', username)
            }
            Collection<GrantedAuthority> authorities = loadAuthorities(user, username, loadRoles)
            authorities.add(new GrantedAuthorityImpl(Authority.ROLE_USER))

            //last login
            if(user.enabled && !user.accountExpired && !user.passwordExpired && !user.accountLocked){
                user.lastLogin = new Date()
                user.save(flush:true)
            }

            return new ScrumUserDetails(user.username, user.password, user.enabled,
                    !user.accountExpired, !user.passwordExpired,
                    !user.accountLocked, authorities ?: NO_ROLES, user.id,
                    user.firstName + " " + user.lastName)
        }
    }

    /**
     * {@inheritDoc}
     */
    UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        loadUserByUsername username, true
    }
}
