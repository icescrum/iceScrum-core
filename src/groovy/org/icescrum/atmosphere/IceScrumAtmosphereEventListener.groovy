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
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.AtmosphereResourceEvent
import org.atmosphere.cpr.AtmosphereResourceEventListener
import org.atmosphere.cpr.Broadcaster
import org.icescrum.core.services.PushService
import org.icescrum.core.support.ApplicationSupport
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

class IceScrumAtmosphereEventListener implements AtmosphereResourceEventListener {

    private static final log = LogFactory.getLog(this)
    public static final USER_CONTEXT = 'user_context'
    public static final GLOBAL_CONTEXT = '/stream/app/*'
    public static final GLOBAL_CONTEXT_NO_STAR = '/stream/app/'

    def atmosphereMeteor = Holders.applicationContext.getBean("atmosphereMeteor")

    @Override
    void onPreSuspend(AtmosphereResourceEvent event) {}

    @Override
    void onSuspend(AtmosphereResourceEvent event) {
        def request = event.resource.request
        AtmosphereUser user = createAtmosphereUser(event.resource)
        request.setAttribute(USER_CONTEXT, user)
        String[] decodedPath = request.pathInfo ? request.pathInfo.split("/") : []
        if (atmosphereMeteor.broadcasterFactory && decodedPath.length > 0) {
            def channel = "/stream/app/" + decodedPath[decodedPath.length - 1]
            IceScrumBroadcaster broadcaster = atmosphereMeteor.broadcasterFactory.lookup(channel, true)
            broadcaster.addAtmosphereResource(event.resource)
        }
        if(ApplicationSupport.betaFeatureEnabled("usersOnline")){
            event.resource.broadcasters().each {
                if (it instanceof IceScrumBroadcaster && it.addUser(user) && it.getID() != GLOBAL_CONTEXT) {
                    updateUsersInWorkspace(it, event.resource)
                }
            }
        }
    }

    @Override
    void onDisconnect(AtmosphereResourceEvent event) {
        if (event.resource) {
            AtmosphereUser user = (AtmosphereUser) event.resource.request.getAttribute(USER_CONTEXT) ?: null
            if (log.isDebugEnabled()) {
                log.debug("user ${user?.username} with UUID ${event.resource.uuid()} disconnected")
            }
            if(ApplicationSupport.betaFeatureEnabled("usersOnline")) {
                event.resource.broadcasters().each {
                    if (it instanceof IceScrumBroadcaster && it.removeUser(user) && it.getID() != GLOBAL_CONTEXT) {
                        updateUsersInWorkspace(it, event.resource)
                    }
                }
            }
        }
    }

    @Override
    void onResume(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            AtmosphereUser user = (AtmosphereUser) event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Resume connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onBroadcast(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            AtmosphereUser user = (AtmosphereUser) event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("broadcast to user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onThrowable(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            AtmosphereUser user = (AtmosphereUser) event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Throwable connection for user ${user?.username} with UUID ${event.resource.uuid()} ${event.resource.transport().toString()}")
        }
    }

    @Override
    void onHeartbeat(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            AtmosphereUser user = (AtmosphereUser) event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Heartbeat connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onClose(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            AtmosphereUser user = (AtmosphereUser) event.resource.request.getAttribute(USER_CONTEXT) ?: null
            log.debug("Close connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    private static def createAtmosphereUser(def resource) {
        def request = resource.request
        AtmosphereUser user = new AtmosphereUser()
        // Cannot use springSecurityService directly here because there is no hibernate session to look into
        def context = (SecurityContext) request.session?.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY)
        user.id = context?.authentication?.isAuthenticated() ? context.authentication.principal.id : null
        user.username = context?.authentication?.isAuthenticated() ? context.authentication.principal.username : 'anonymous'
        user.connections.add(new AtmosphereUserConnection(
                resource: resource,
                window: request.getParameterValues("window") ? request.getParameterValues("window")[0] : null)
        )
        return user
    }

    private synchronized void updateUsersInWorkspace(Broadcaster _broadcaster, AtmosphereResource resource) {
        IceScrumBroadcaster broadcaster = (IceScrumBroadcaster) _broadcaster
        def workspace = broadcaster.getID() - GLOBAL_CONTEXT_NO_STAR
        workspace = workspace.split('-')
        workspace = [id: workspace[1].toLong(), type: workspace[0]]
        def message = PushService.buildMessage(workspace.type, "onlineMembers", ["messageId": "online-users-${workspace.type}-${workspace.id}", "${workspace.type}": ["id": workspace.id, "onlineMembers": broadcaster.users]])
        Set<AtmosphereResource> resources = broadcaster.atmosphereResources - resource
        broadcaster.broadcast(message as JSON, resources)
    }
}
