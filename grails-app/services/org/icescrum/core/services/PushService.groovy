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
import grails.transaction.Transactional
import grails.util.GrailsNameUtils
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.Broadcaster
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener
import org.icescrum.atmosphere.IceScrumBroadcaster
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumEventType

import java.util.concurrent.CopyOnWriteArrayList

@Transactional
class PushService {

    def atmosphereMeteor
    def disabledThreads = new CopyOnWriteArrayList<>()

    void broadcastToChannel(String namespace, String eventType, object, String channel = '/stream/app/*') {
        Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
        if (broadcaster) {
            log.debug("Broadcast to everybody on channel " + channel)
            broadcaster.broadcast(buildMessage(namespace, eventType, object))
        }
    }

    void broadcastToProductChannel(String namespace, String eventType, object, long productId) {
        def channel = '/stream/app/product-' + productId
        broadcastToChannel(namespace, eventType, object, channel)
    }

    void broadcastToProductChannel(IceScrumEventType eventType, object, long productId) {
        if (!isDisabledThread()) {
            broadcastToProductChannel(getNamespaceFromDomain(object), eventType.name(), object, productId)
        }
    }

    void broadcastToUsers(String namespace, String eventType, object, Collection<String> usernames) {
        def channel = '/stream/app/*'
        Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
        if (broadcaster) {
            Set<AtmosphereResource> resources = broadcaster.atmosphereResources?.findAll { AtmosphereResource resource ->
                resource.request?.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)?.username in usernames
            }
            if (resources) {
                log.debug('Broadcast to ' + resources*.uuid().join(', ') + ' on channel ' + channel)
                broadcaster.broadcast(buildMessage(namespace, eventType, object), resources)
            }
        }
    }

    void broadcastToUsers(IceScrumEventType eventType, object, Collection<User> users) {
        if (!isDisabledThread()) {
            broadcastToUsers(getNamespaceFromDomain(object), eventType.name(), object, users*.username)
        }
    }

    void disablePushForThisThread() {
        if (!isDisabledThread()) {
            disabledThreads.add(Thread.currentThread().getId())
        }
    }

    void enablePushForThisThread() {
        if (isDisabledThread()) {
            disabledThreads.remove(Thread.currentThread().getId())
        }
    }

    private boolean isDisabledThread() {
        return disabledThreads.contains(Thread.currentThread().getId())
    }

    private getNamespaceFromDomain(domain) {
        return GrailsNameUtils.getShortName(domain.class).toLowerCase()
    }

    private String buildMessage(String namespace, String eventType, object) {
        return ([namespace: namespace, eventType: eventType, object: object] as JSON).toString() // toString() required to serialize eagerly (otherwise error because no session in atmosphere thread)
    }
}
