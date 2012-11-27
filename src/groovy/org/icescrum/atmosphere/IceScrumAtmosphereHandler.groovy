/*
 * Copyright (c) 2011 Kagilum.
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

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.apache.commons.logging.LogFactory
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Team
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.atmosphere.cpr.*
import org.atmosphere.cpr.BroadcasterLifeCyclePolicy.ATMOSPHERE_RESOURCE_POLICY
import org.atmosphere.cpr.BroadcasterLifeCyclePolicy.Builder
import org.codehaus.groovy.grails.commons.ApplicationHolder
import grails.converters.JSON

class IceScrumAtmosphereHandler implements AtmosphereHandler<HttpServletRequest, HttpServletResponse> {

    private static final log = LogFactory.getLog(this)

    void onRequest(AtmosphereResource<HttpServletRequest, HttpServletResponse> event) throws IOException {
        def conf = ApplicationHolder.application.config.icescrum.push
        if (!conf.enable || event.request.getMethod() == "POST") {
            event.resume()
            return
        }

        event.response.setContentType("text/plain;charset=ISO-8859-1");
        event.response.addHeader("Cache-Control", "private");
        event.response.addHeader("Pragma", "no-cache");
        event.response.addHeader("Access-Control-Allow-Origin", "*");
        event.suspend(60000, true);

        def productID = event.request.getParameterValues("product") ? event.request.getParameterValues("product")[0] : null
        def user = getUserFromAtmosphereResource(event.request, true) ?: [fullName: 'anonymous', id: null, username: 'anonymous']
        event.request.setAttribute('user_context', user)

        def channel = null

        if (productID && productID.isLong()) {
            channel = Product.load(productID.toLong()) ? "product-${productID}" : null
        }

        channel = channel?.toString()
        if (channel) {
            Class<? extends org.atmosphere.cpr.Broadcaster> bc = (Class<? extends org.atmosphere.cpr.Broadcaster>) ApplicationHolder.application.getClassLoader().loadClass(conf?.broadcaster?:'org.icescrum.atmosphere.ExcludeSessionBroadcaster')
            def broadcaster = BroadcasterFactory.default.lookup(bc, channel)
            if(broadcaster == null){
                broadcaster = bc.newInstance(channel, event.atmosphereConfig)
                broadcaster.setBroadcasterLifeCyclePolicy(new Builder().policy(ATMOSPHERE_RESOURCE_POLICY.EMPTY_DESTROY).build())
                broadcaster.broadcasterConfig.addFilter(new StreamFilter())
            }
            broadcaster.addAtmosphereResource(event)
            if (log.isDebugEnabled()) {
                log.debug("add user ${user.username} to broadcaster: ${channel}")
            }
            def users = broadcaster.atmosphereResources.collect{ it.request.getAttribute('user_context') }
            broadcaster.broadcast(([broadcaster:[users:users]] as JSON).toString())
        }
        if (user.id != null)
            addBroadcasterToFactory(event, (String)user.username)
    }

    void onStateChange(AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) throws IOException {

        def user = event.resource.request.getAttribute('user_context')?:null

        //Event cancelled
        if (event.cancelled) {
            if (log.isDebugEnabled()) {
                log.debug("user ${user.username} disconnected")
            }
            //Help atmosphere to clear old events
            BroadcasterFactory.default.lookupAll().each {
                it.removeAtmosphereResource(event.resource)
                if (it instanceof ExcludeSessionBroadcaster){
                    def users = it.atmosphereResources?.collect{ it.request.getAttribute('user_context') }
                    if (users)
                        it.broadcast(([broadcaster:[users:users]] as JSON).toString())
                }
            }

            if (!event.message) {
                return
            }
        }

        //No message why I'm here ?
        if (!event.message) {
            return
        }

        if (log.isDebugEnabled()) {
            log.debug("broadcast to user ${user?.username ?: 'anonymous'}")
        }

        //Finally broadcast message to client
        event.resource.response.writer.with {
            write "${event.message}"
            flush()
        }
    }

    void destroy() {

    }

    private def getUserFromAtmosphereResource(def request, def createSession = false) {
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

    private void addBroadcasterToFactory(AtmosphereResource resource, String broadcasterID){
        def conf = ApplicationHolder.application.config.icescrum.push
        Class<? extends org.atmosphere.cpr.Broadcaster> bc = (Class<? extends org.atmosphere.cpr.Broadcaster>) ApplicationHolder.application.getClassLoader().loadClass(conf?.userBroadcaster?:'org.atmosphere.cpr.DefaultBroadcaster')
        Broadcaster singleBroadcaster= BroadcasterFactory.default.lookup(bc, broadcasterID);
        if(singleBroadcaster != null){
            singleBroadcaster.addAtmosphereResource(resource)
            return
        }
        Broadcaster selfBroadcaster = bc.newInstance(broadcasterID, resource.atmosphereConfig);
        selfBroadcaster.broadcasterConfig.addFilter(new StreamFilter())
        selfBroadcaster.setBroadcasterLifeCyclePolicy(new Builder().policy(ATMOSPHERE_RESOURCE_POLICY.EMPTY_DESTROY).build())
        selfBroadcaster.addAtmosphereResource(resource)

        BroadcasterFactory.getDefault().add(selfBroadcaster, broadcasterID);
        if (log.isDebugEnabled()) {
            log.debug('new broadcaster for user')
        }
    }
}
