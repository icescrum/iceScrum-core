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

import grails.converters.JSON
import org.atmosphere.handler.AbstractReflectorAtmosphereHandler
import org.atmosphere.cpr.*
import org.codehaus.groovy.grails.commons.ApplicationHolder

class IceScrumAtmosphereHandler extends AbstractReflectorAtmosphereHandler {

    @Override
    void onRequest(AtmosphereResource event) throws IOException {
        def conf = ApplicationHolder.application.config.icescrum.push
        if (!conf.enable) {
            event.resume()
            return
        }
        if (event.request.getMethod().equalsIgnoreCase("POST")) {
            AtmosphereResource resourceFrom = AtmosphereResourceFactory.default.find(event.uuid())
            if (resourceFrom){
                def user = resourceFrom.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)
                if (event.request.getParameterValues("window")) {
                    user.window = event.request.getParameterValues("window") ? event.request.getParameterValues("window")[0] : null
                    resourceFrom.request.setAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT, user)
                }
                else if (event.request.getParameterValues("command") && event.request.getParameterValues("data") && event.request.getParameterValues("to")){
                    Broadcaster broadcaster = BroadcasterFactory.default.lookup('/stream/app/*')
                    AtmosphereResource resourceTo = AtmosphereResourceFactory.default.find(event.request.getParameterValues("to")[0])
                    if (resourceTo){
                        broadcaster.broadcast(([command:event.request.getParameterValues("command")[0],data:event.request.getParameterValues("data")[0], from:event.uuid()] as JSON).toString(),resourceTo)
                    }
                }
            }
            event.resume()
            return
        }
        event.response.setContentType("text/plain;charset=UTF-8")
        event.addEventListener( new IceScrumAtmosphereEventListener() )
        event.suspend()
    }

    @Override
    void destroy() {
    }
}
