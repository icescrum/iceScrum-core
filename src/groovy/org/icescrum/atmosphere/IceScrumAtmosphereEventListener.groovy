/*
 * Copyright (c) 2013 Kagilum.
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
package org.icescrum.atmosphere

import grails.converters.JSON
import grails.util.Holders
import org.apache.commons.logging.LogFactory
import org.atmosphere.cpr.AtmosphereResourceEvent
import org.atmosphere.cpr.AtmosphereResourceEventListener
import org.atmosphere.cpr.Broadcaster
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

class IceScrumAtmosphereEventListener implements AtmosphereResourceEventListener {

    private static final log = LogFactory.getLog(this)
    public static final USER_CONTEXT = 'user_context'

    def atmosphereMeteor = Holders.applicationContext.getBean("atmosphereMeteor")

    @Override
    void onPreSuspend(AtmosphereResourceEvent event) {}

    @Override
    void onSuspend(AtmosphereResourceEvent event) {
        def request = event.resource.request
        def user = getUserFromAtmosphereResource(event.resource)
        request.setAttribute(USER_CONTEXT, user)
        String[] decodedPath = request.pathInfo ? request.pathInfo.split("/") : []
        Broadcaster broadcaster
        def broadcasters = ["default channel"]
        if (atmosphereMeteor.broadcasterFactory){
            if (decodedPath.length > 0) {
                def channel = "/stream/app/" + decodedPath[decodedPath.length - 1]
                broadcaster = atmosphereMeteor.broadcasterFactory.lookup(channel, true)
                broadcaster.addAtmosphereResource(event.resource)
                broadcasters << channel
            }
        }
        if (log.isDebugEnabled()) {
            log.debug("add user ${user.username} with UUID ${event.resource.uuid()} to broadcasters: " + broadcasters.join(', '))
        }
    }

    @Override
    void onResume(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Resume connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onDisconnect(AtmosphereResourceEvent event) {
        if (event.resource) {
            if (log.isDebugEnabled()) {
                def user = event.resource.request.getAttribute(USER_CONTEXT) ?: null
                log.debug("user ${user?.username} disconnected with UUID ${event.resource.uuid()}")
            }
        }
    }

    @Override
    void onBroadcast(AtmosphereResourceEvent event) {
        def user = event.resource.request.getAttribute(USER_CONTEXT) ?: null
        if (log.isDebugEnabled()) {
            log.debug("broadcast to user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onThrowable(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Throwable connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onHeartbeat(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Heartbeat connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onClose(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Close connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    private static def getUserFromAtmosphereResource(def resource) {
        def request = resource.request
        def user = [uuid: resource.uuid(), window: request.getParameterValues("window") ? request.getParameterValues("window")[0] : null]
        // Cannot use springSecurityService directly here because there is no hibernate session to look into
        def context = (SecurityContext) request.session?.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
        if (context?.authentication?.isAuthenticated()) {
            def principal = context.authentication.principal
            user.putAll([fullName: principal.fullName, id: principal.id, username: principal.username])
        } else {
            user.putAll([fullName: 'anonymous', id: null, username: 'anonymous'])
        }
        return user
    }
}
