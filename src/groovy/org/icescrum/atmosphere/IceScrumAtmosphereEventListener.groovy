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
import org.apache.commons.logging.LogFactory
import org.atmosphere.cpr.AtmosphereResourceEvent
import org.atmosphere.cpr.AtmosphereResourceEventListener
import org.atmosphere.cpr.AtmosphereResourceFactory
import org.atmosphere.cpr.BroadcasterFactory
import org.icescrum.core.domain.Product
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository


class IceScrumAtmosphereEventListener implements AtmosphereResourceEventListener {

    public static final USER_CONTEXT = 'user_context'
    private static final log = LogFactory.getLog(this)

    @Override
    void onSuspend(AtmosphereResourceEvent event) {
        def request = event.resource.request
        def productID = request.getParameterValues("product") ? request.getParameterValues("product")[0] : null

        def user = getUserFromAtmosphereResource(request, true) ?: [fullName: 'anonymous', id: null, username: 'anonymous']
        request.setAttribute(USER_CONTEXT, user)

        def channel = null

        if (productID && productID.isLong()) {
            channel = Product.load(productID.toLong()) ? "product-${productID}" : null
        }

        if (log.isDebugEnabled()) {
            log.debug("Check product ${productID} to create new channel: ${channel}")
        }

        channel = channel?.toString()
        if (channel) {
            def broadcaster = BroadcasterFactory.default.lookup(channel) ?: BroadcasterFactory.default.get(channel)
            broadcaster.addAtmosphereResource(event.resource)
            if (log.isDebugEnabled()) {
                log.debug("add user ${user.username} with UUID ${event.resource.uuid()} to broadcaster: ${channel}")
            }
            def users = broadcaster.atmosphereResources.collect{
                it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)
            }
            broadcaster.broadcast(([broadcaster:[users:users]] as JSON).toString())
        }
    }

    @Override
    void onResume(AtmosphereResourceEvent event) {
        def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
        if (log.isDebugEnabled()) {
            log.debug("Resume connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onDisconnect(AtmosphereResourceEvent event) {
        def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
        if (log.isDebugEnabled()) {
            log.debug("user ${user?.username} disconnected with UUID ${event.resource.uuid()}")
        }
        BroadcasterFactory.default.lookupAll().each {
            if (it.atmosphereResources.contains(event.resource)){
                it.removeAtmosphereResource(event.resource)
                if (it.getID().contains('product-')) {
                    def users = it.atmosphereResources?.collect{ it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT) }
                    it.broadcast(([broadcaster:[users:users]] as JSON).toString())
                }
            }
        }
        AtmosphereResourceFactory.getDefault().remove(event.resource.uuid());
    }

    @Override
    void onBroadcast(AtmosphereResourceEvent event) {
        def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
        if (log.isDebugEnabled()) {
            log.debug("broadcast to user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onThrowable(AtmosphereResourceEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private static def getUserFromAtmosphereResource(def request, def createSession = false) {
        def httpSession = request.getSession(createSession)
        def user = null
        if (httpSession != null) {
            def context = (SecurityContext) httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context?.authentication?.isAuthenticated()) {
                user = [fullName:context.authentication.principal.fullName, id:context.authentication.principal.id, username:context.authentication.principal.username]
            }
        }
        user
    }
}
