/*
 * Copyright (c) 2014 Kagilum.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.services

import grails.plugin.fluxiable.Activity
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.event.IceScrumSynchronousEvent.EventType

class ListenerService {

    def springSecurityService

    @IceScrumListener(domain='story', eventType=EventType.UPDATE)
    def storyUpdate(Story story, Map dirtyProperties) {
        if (dirtyProperties) {
            def product = story.backlog
            if (dirtyProperties.containsKey('feature')) {
                def oldFeature = dirtyProperties.feature
                def newFeature = story.feature
                if (oldFeature != null) {
                    oldFeature.lastUpdated = new Date()
                    // should rather be a call to feature service update
                    broadcast(function: 'update', message: oldFeature, channel:'product-'+product.id)
                }
                if (newFeature != null) {
                    newFeature.lastUpdated = new Date()
                    broadcast(function: 'update', message: newFeature, channel:'product-'+product.id)
                }
            }
            if (dirtyProperties.containsKey('dependsOn')) {
                def oldDependsOn = dirtyProperties.dependsOn
                def newDependsOn = story.dependsOn
                if (oldDependsOn != null) {
                    oldDependsOn.lastUpdated = new Date()
                    broadcast(function: 'update', message: oldDependsOn, channel:'product-'+product.id)
                }
                if (newDependsOn != null) {
                    newDependsOn.lastUpdated = new Date()
                    broadcast(function: 'update', message: newDependsOn, channel:'product-'+product.id)
                }
            }
            def u = (User) springSecurityService.currentUser
            story.addActivity(u, Activity.CODE_UPDATE, story.name)
            broadcast(function: 'update', message: story, channel:'product-'+product.id)
        }
    }
}
