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
import org.icescrum.core.domain.User
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository
import org.atmosphere.cpr.*
import org.atmosphere.util.ExcludeSessionBroadcaster
import javax.servlet.http.HttpSession
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.apache.http.HttpRequest
import org.springframework.http.HttpMethod

class IceScrumAtmosphereHandler implements AtmosphereHandler<HttpServletRequest, HttpServletResponse> {

    private static final log = LogFactory.getLog(this)

    void onRequest(AtmosphereResource<HttpServletRequest, HttpServletResponse> event) throws IOException {

        if (event.request.getMethod() == "POST") {
            event.resume()
            return
        }
        event.response.setContentType("text/plain;charset=ISO-8859-1");
        event.response.addHeader("Cache-Control", "private");
        event.response.addHeader("Pragma", "no-cache");
        event.response.addHeader("Access-Control-Allow-Origin", "*");
        event.suspend()

        def productID = event.request.getParameterValues("product") ? event.request.getParameterValues("product")[0] : null
        def teamID = event.request.getParameterValues("team") ? event.request.getParameterValues("team")[0] : null
        def user = getUserFromAtmosphereResource(event.request)

        def channel = null
        if (productID && productID.isLong()) {
            channel = Product.load(productID.toLong()) ? "product-${productID}" : null

        } else if (teamID && teamID.isLong()) {
            channel = Team.load(teamID.toLong()) ? "team-${teamID}" : null
        }
        if (channel) {
            def broadcaster = BroadcasterFactory.default.lookup(ExcludeSessionBroadcaster.class, channel, true)
            broadcaster.broadcasterConfig.addFilter(new StreamFilter())
            broadcaster.addAtmosphereResource(event)
            if (log.isDebugEnabled()) {
                log.debug("add user ${user ?: 'anonymous'} to broadcaster: ${channel}")
            }
            displayConnectedUsers(broadcaster)
        }

        if (log.isDebugEnabled()) {
            log.debug("add user ${user ?: 'anonymous'} to app broadcaster")
        }
    }

    void onStateChange(AtmosphereResourceEvent<HttpServletRequest, HttpServletResponse> event) throws IOException {

        def user = getUserFromAtmosphereResource(event.resource.request)

        //No message why I'm here ?
        if (!event.message) {
            return
        }

        //Event cancelled
        if (event.cancelled) {
            if (log.isDebugEnabled()) {
                log.debug("user ${user ?: 'anonymous'} disconnected")
            }
            //Help atmosphere to clear old events
            BroadcasterFactory.default.lookupAll().each {
                it.removeAtmosphereResource(event.resource)
            }
            return
        }

        if (log.isDebugEnabled()) {
            log.debug("broadcast to user ${user ?: 'anonymous'}")
        }

        //Finally broadcast message to client
        event.resource.response.writer.with {
            write "${event.message}"
            flush()
        }
    }

    void destroy() {}

    private def getUserFromAtmosphereResource(def request) {
        def httpSession = request.session;
        def user = null
        if (httpSession != null) {
            def context = (SecurityContext) httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context?.authentication?.isAuthenticated()) {
                user = context.authentication.principal.fullName
            }
        }
        user
    }

    private void displayConnectedUsers(def broadcaster) {
        def authenticated = []
        def anonymous = 0
        broadcaster.atmosphereResources*.request.each {
            def user = getUserFromAtmosphereResource(it)
            if (user) {
                authenticated << user
            } else {
                anonymous++
            }
        }
    }
}
