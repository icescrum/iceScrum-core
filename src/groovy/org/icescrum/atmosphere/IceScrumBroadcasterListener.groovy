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
import org.atmosphere.cpr.BroadcasterListenerAdapter
import org.icescrum.core.services.PushService
import org.icescrum.core.support.ApplicationSupport

class IceScrumBroadcasterListener extends BroadcasterListenerAdapter {

    public static final GLOBAL_CONTEXT = '/stream/app/*'
    def grailsApplication = Holders.applicationContext.getBean("grailsApplication")

    @Override
    void onAddAtmosphereResource(Broadcaster broadcaster, AtmosphereResource atmosphereResource) {
        def resources = broadcaster.atmosphereResources.findAll { !it.isCancelled() }
        if (broadcaster.getID() == GLOBAL_CONTEXT) {
            updateUsersAndConnections(resources)
        } else {
            if (broadcaster.getID() != GLOBAL_CONTEXT) {
                updateUsersInWorkspace(broadcaster, resources)
            }
        }
    }

    @Override
    void onRemoveAtmosphereResource(Broadcaster broadcaster, AtmosphereResource atmosphereResource) {
        def resources = broadcaster.atmosphereResources.findAll { !it.isCancelled() }
        if (broadcaster.getID() == GLOBAL_CONTEXT) {
            updateUsersAndConnections(resources)
        } else {
            if (broadcaster.getID() != GLOBAL_CONTEXT) {
                updateUsersInWorkspace(broadcaster, resources)
            }
        }
    }

    private synchronized void updateUsersAndConnections(List<AtmosphereResource> resources) {
        def config = grailsApplication.config.icescrum.atmosphere
        def users = ApplicationSupport.getUsersFromAtmosphereResources(resources, true)
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

    private synchronized void updateUsersInWorkspace(Broadcaster broadcaster, List<AtmosphereResource> resources) {
        def users = ApplicationSupport.getUsersFromAtmosphereResources(resources)
        def workspace = broadcaster.getID() - "/stream/app/"
        workspace = workspace.split('-')
        workspace = [id: workspace[1].toLong(), type: workspace[0]]
        def message = PushService.buildMessage(workspace.type, "onlineMembers", [messageId: "online-users-${workspace.type}-${workspace.id}", "${workspace.type}": [id: workspace.id, onlineMembers: users]]).content
        broadcaster.broadcast(message)
    }
}
