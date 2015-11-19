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
import grails.plugin.springsecurity.web.SecurityRequestHolder as SRH

import grails.plugin.springsecurity.userdetails.GrailsUser
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.UserPreferences

class MenuBarSupport {

    def webInvocationPrivilegeEvaluator
    def springSecurityService

    private commonVerification(url) {
        url = url.toString() - SRH.request.contextPath
        permissionDynamicBar(url)
    }

    private menuFromUserPreferences(uiDefininitionId) {
        UserPreferences userPreferences = null
        if (GrailsUser.isAssignableFrom(SCH.context.authentication?.principal?.getClass())) {
            userPreferences = User.get(SCH.context.authentication.principal?.id)?.preferences
        }
        def visiblePosition = userPreferences?.menu?.getAt(uiDefininitionId)
        def hiddenPosition = userPreferences?.menuHidden?.getAt(uiDefininitionId)
        def menuEntry = [:]
        if (visiblePosition) {
            menuEntry.pos = visiblePosition
            menuEntry.visible = true
        } else if (hiddenPosition) {
            menuEntry.pos = hiddenPosition
            menuEntry.visible = false
        } else {
            menuEntry = null
        }
        return menuEntry
    }

    def spaceDynamicBar = { uiDefininitionId, defaultVisibility, defaultPosition, space, initAction ->
        return {
            if (!params?."$space" || !defaultPosition) {
                return false
            }
            def attrs = [:]
            attrs."$space" = params."$space"
            if (!commonVerification(createLink(controller: uiDefininitionId, action: initAction, params:attrs))) {
                return false
            }
            return menuFromUserPreferences(uiDefininitionId) ?: [visible: defaultVisibility, pos: defaultPosition]
        }
    }

    def permissionDynamicBar = {url ->
        webInvocationPrivilegeEvaluator.isAllowed(SRH.request.contextPath, url, 'GET', SCH.context?.authentication)
    }
}
