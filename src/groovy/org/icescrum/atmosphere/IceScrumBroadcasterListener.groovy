/*
 * Copyright (c) 2018 Kagilum.
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

import grails.util.Holders
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.BroadcasterListener
import org.atmosphere.cpr.Deliver
import org.icescrum.core.services.PushService

class IceScrumBroadcasterListener implements BroadcasterListener {

    public static final GLOBAL_CONTEXT = '/stream/app/*'
    def grailsApplication = Holders.applicationContext.getBean("grailsApplication")

    @Override
    void onAddAtmosphereResource(Broadcaster broadcaster, AtmosphereResource atmosphereResource) {
        def resources = broadcaster.atmosphereResources.findAll { it.transport() != AtmosphereResource.TRANSPORT.CLOSE }
        if (broadcaster.getID() == GLOBAL_CONTEXT) {
            updateUsersAndConnections(resources)
        } else {
            updateUsersOnProject(broadcaster, resources)
        }
    }

    @Override
    void onRemoveAtmosphereResource(Broadcaster broadcaster, AtmosphereResource atmosphereResource) {
        def resources = broadcaster.atmosphereResources.findAll { it.transport() != AtmosphereResource.TRANSPORT.CLOSE }
        if (broadcaster.getID() == GLOBAL_CONTEXT) {
            updateUsersAndConnections(resources)
        } else {
            updateUsersOnProject(broadcaster, resources)
        }
    }

    private synchronized void updateUsersAndConnections(List<AtmosphereResource> resources) {
        def config = grailsApplication.config.icescrum.atmosphere
        def users = resources.collect {
            it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT) ? it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT).username : null
        }.unique {
            a, b -> a != 'anonymous' ? a <=> b : 1
        }
        config.liveUsers = users
        if (users.size() > config.maxUsers.size()) {
            config.maxUsers = users
            config.maxUsersDate = new Date()
        }
        config.liveConnections = resources.size() ?: 0
        if (config.liveConnections > config.maxConnections) {
            config.maxConnections = config.liveConnections
            config.maxConnectionsDate = new Date()
        }
    }

    private synchronized void updateUsersOnProject(Broadcaster broadcaster, List<AtmosphereResource> resources) {
        def users = resources.collect {
            it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT) ? it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT).username : null
        }.unique {
            a, b -> a != 'anonymous' ? a <=> b : 1
        }
        def message = PushService.buildMessage("projectUsers", "update", [messageId: "online-users-${broadcaster.getID()}", users: users]).content
        broadcaster.broadcast(message)
    }

    @Override
    void onPostCreate(Broadcaster broadcaster) {

    }
    @Override
    void onComplete(Broadcaster broadcaster) {

    }
    @Override
    void onPreDestroy(Broadcaster broadcaster) {

    }
    @Override
    void onMessage(Broadcaster broadcaster, Deliver deliver) {

    }
}
