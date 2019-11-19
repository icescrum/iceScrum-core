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

import grails.converters.JSON
import grails.util.Holders
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.BroadcasterListenerAdapter
import org.icescrum.core.services.PushService
import org.icescrum.core.support.ApplicationSupport

class IceScrumBroadcasterListener extends BroadcasterListenerAdapter {

    public static final GLOBAL_CONTEXT = '/stream/app/*'

    @Override
    void onAddAtmosphereResource(Broadcaster _broadcaster, AtmosphereResource atmosphereResource) {
        IceScrumBroadcaster broadcaster = (IceScrumBroadcaster) _broadcaster
        def user = getUserFromAtmosphereResource(atmosphereResource, broadcaster.getID() == GLOBAL_CONTEXT)
        if (broadcaster.addUser(user) && broadcaster.getID() != GLOBAL_CONTEXT) {
            updateUsersInWorkspace(broadcaster)
        }
    }

    @Override
    void onRemoveAtmosphereResource(Broadcaster _broadcaster, AtmosphereResource atmosphereResource) {
        IceScrumBroadcaster broadcaster = (IceScrumBroadcaster) _broadcaster
        def user = getUserFromAtmosphereResource(atmosphereResource, broadcaster.getID() == GLOBAL_CONTEXT)
        if (broadcaster.removeUser(user) && broadcaster.getID() != GLOBAL_CONTEXT) {
            updateUsersInWorkspace(broadcaster)
        }
    }

    private synchronized void updateUsersInWorkspace(Broadcaster _broadcaster) {
        IceScrumBroadcaster broadcaster = (IceScrumBroadcaster) _broadcaster
        def workspace = broadcaster.getID() - "/stream/app/"
        workspace = workspace.split('-')
        workspace = [id: workspace[1].toLong(), type: workspace[0]]
        def message = PushService.buildMessage(workspace.type, "onlineMembers", ["messageId": "online-users-${workspace.type}-${workspace.id}", "${workspace.type}": ["id": workspace.id, "onlineMembers": broadcaster.users]])
        broadcaster.broadcast(message as JSON)
    }

    private static Map getUserFromAtmosphereResource(def resource, def includeIp = false) {
        def user
        try { // catch exception from atmosphere
            def userData = resource.getRequest(false).getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)
            user = [username : userData ? userData.username : 'anonymous',
                    id       : userData ? userData.id : null,
                    transport: resource.transport().toString()]
            if (includeIp) {
                user.ip = getAddressIp(resource.request)
            }
        } catch (IllegalStateException e) {

        }
        return user
    }

    private static String getAddressIp(def request) {
        String ip
        if (request.getHeader("X-Forwarded-For") != null) {
            String xForwardedFor = request.getHeader("X-Forwarded-For")
            if (xForwardedFor.indexOf(",") != -1) {
                ip = xForwardedFor.substring(xForwardedFor.lastIndexOf(",") + 2)
            } else {
                ip = xForwardedFor
            }
        } else {
            ip = request.getRemoteAddr()
        }
        return ip
    }
}