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
import org.atmosphere.cpr.AtmosphereResourceFactory
import org.atmosphere.cpr.BroadcasterFactory
import org.icescrum.core.domain.Product
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository


class IceScrumAtmosphereEventListener implements AtmosphereResourceEventListener {

    private static final log = LogFactory.getLog(this)
    public static final USER_CONTEXT = 'user_context'

    def atmosphereMeteor = Holders.applicationContext.getBean("atmosphereMeteor")

    @Override
    void onPreSuspend(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
            log.debug("user ${user?.username} disconnected with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onSuspend(AtmosphereResourceEvent event) {
        def request = event.resource.request

        def productID = request.getParameterValues("product") ? request.getParameterValues("product")[0] : null
        def user = getUserFromAtmosphereResource(event.resource)

        if (!user){
            event.resource.resume();
            return
        }

        request.setAttribute(USER_CONTEXT, user)

        def channel = productID && productID.isLong() ?  (Product.load(productID.toLong()) ? "product-${productID}" : null) : null
        if (channel) {
            def broadcaster = atmosphereMeteor.broadcasterFactory.lookup(channel, true)
            broadcaster.addAtmosphereResource(event.resource)
            if (log.isDebugEnabled()) {
                log.debug("add user ${user.username} with UUID ${event.resource.uuid()} to broadcaster: ${channel}")
            }
            def users = broadcaster.atmosphereResources.collect{
                it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)
            }
            broadcaster.broadcast(([[command:'connected',object:users]] as JSON).toString())
        }
    }

    @Override
    void onResume(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
            log.debug("Resume connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onDisconnect(AtmosphereResourceEvent event) {
        if (event.resource){
            if (log.isDebugEnabled()) {
                def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
                log.debug("user ${user?.username} disconnected with UUID ${event.resource.uuid()}")
            }
            atmosphereMeteor.broadcasterFactory.lookupAll().each {
                if (it.atmosphereResources.contains(event.resource)){
                    if (it.getID().contains('product-') && it.atmosphereResources) {
                        def users = it.atmosphereResources?.findAll{ it.uuid() != event.resource.uuid() }?.collect{ it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT) }
                        if (users){
                            it.broadcast(([[command:'connected',object:users]] as JSON).toString())
                        }
                    }
                }
            }
        }

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
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
            log.debug("Throwable connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onHeartbeat(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
            log.debug("Heartbeat connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    @Override
    void onClose(AtmosphereResourceEvent event) {
        if (log.isDebugEnabled()) {
            def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
            log.debug("Close connection for user ${user?.username} with UUID ${event.resource.uuid()}")
        }
    }

    private static def getUserFromAtmosphereResource(def resource) {
        def user = [uuid:resource.uuid(), window:resource.request.getParameterValues("window") ? resource.request.getParameterValues("window")[0] : null]
        def springSecurityService = Holders.applicationContext.getBean("springSecurityService")
        user.putAll(springSecurityService.isLoggedIn() ? [fullName:springSecurityService.currentUser.fullName, id:springSecurityService.currentUser.id, username:springSecurityService.currentUser.username] : [fullName: 'anonymous', id: null, username: 'anonymous'])
    }
}
