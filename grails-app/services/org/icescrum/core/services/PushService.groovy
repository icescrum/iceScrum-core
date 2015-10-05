/*
 * Copyright (c) 2015 Kagilum SAS.
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import grails.converters.JSON
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.HeaderConfig
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener
import org.icescrum.atmosphere.IceScrumBroadcaster
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumEventType
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.context.request.RequestContextHolder

@Transactional
class PushService {

    def atmosphereMeteor

    void broadcastToProductUsers(IceScrumEventType eventType, object, long productId) {
        def channel = '/stream/app/product-' + productId
        Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
        if (broadcaster) {
            def uuid = null
            try {
                uuid = RequestContextHolder.currentRequestAttributes()?.request?.getHeader(HeaderConfig.X_ATMOSPHERE_TRACKING_ID)
            } catch (IllegalStateException e) {
                println e.message
                //something we are not in a webrequest (like in batch threads)
            }
            Set<AtmosphereResource> resources = uuid ? broadcaster.atmosphereResources?.findAll { AtmosphereResource r -> r.uuid() != uuid } : null
            def message = ([eventType: eventType.name(), object: object] as JSON).toString()
            // toString() required to eagerly generate the String (lazy raise an error because no session in atmosphere thread)
            if (resources) {
                log.debug("broadcast to everybody except $uuid on channel " + channel)
                broadcaster.broadcast(message, resources)
            } else if (!uuid) {
                log.debug("broadcast to everybody on channel " + channel)
                broadcaster.broadcast(message)
            }
        }
    }

    void broadcastToSingleUser(IceScrumEventType eventType, object, User user) {
        def channel = '/stream/app/*'
        Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
        if (broadcaster) {
            Set<AtmosphereResource> resources = broadcaster.atmosphereResources?.findAll {
                it.request?.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)?.username == user.username
            }
            if (resources) {
                log.debug('broadcast to ' + resources*.uuid().join(', ') + ' on channel ' + channel)
                broadcaster.broadcast(([eventType: eventType.name(), object: object] as JSON).toString(), resources)
            }
        }
    }
}
