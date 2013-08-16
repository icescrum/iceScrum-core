/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 *
 */
package org.icescrum.core.support

import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.codehaus.groovy.grails.plugins.springsecurity.SecurityRequestHolder as SRH

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.codehaus.groovy.grails.plugins.springsecurity.GrailsUser
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.UserPreferences

class MenuBarSupport {

    def webInvocationPrivilegeEvaluator
    def springSecurityService

    private commonVerification(url) {
        url = url.toString() - SRH.request.contextPath
        permissionDynamicBar(url)
    }

    private commonUserPreferences(id) {
        UserPreferences up = null
        if (GrailsUser.isAssignableFrom(SCH.context.authentication?.principal?.getClass()))
            up = User.get(SCH.context.authentication.principal?.id)?.preferences
        def pos = up?.menu?.getAt(id)
        if (pos)
            return [visible: true, pos: pos]
        else
            pos = up?.menuHidden?.getAt(id)
        if (pos)
            return [visible: false, pos: pos]
        else
            return null
    }

    def spaceDynamicBar = { id, defaultVisibility, defaultPosition, space ->
        return {
            if (!params?."$space") return false
            if (!defaultPosition) return false
            def attrs = [:]
            attrs."$space" = params."$space"
            if (!commonVerification(createLink(controller: id, params:attrs))) return false
            commonUserPreferences(id) ?: [visible: defaultVisibility, pos: defaultPosition]
        }
    }

    def permissionDynamicBar = {url ->
        webInvocationPrivilegeEvaluator.isAllowed(SRH.request.contextPath, url, 'GET', SCH.context?.authentication)
    }
}
