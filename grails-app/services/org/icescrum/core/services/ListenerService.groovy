/*
 * Copyright (c) 2014 Kagilum.
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
 *
 */
package org.icescrum.core.services

import grails.plugin.fluxiable.Activity
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.event.IceScrumEventType

class ListenerService {

    def springSecurityService

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.CREATE)
    void storyCreate(Story story, Map dirtyProperties) {
        log.debug("the story $story.name ($story.id) has been created")
        def u = (User) springSecurityService.currentUser
        story.addActivity(u, Activity.CODE_SAVE, story.name)
        broadcast(function: 'add', message: story, channel: 'product-' + story.backlog.id)
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.UPDATE)
    void storyUpdate(Story story, Map dirtyProperties) {
        log.debug("the story $story.name ($story.id) has been updated")
        if (dirtyProperties) {
            def product = story.backlog
            ['feature', 'dependsOn', 'actor'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    def oldProperty = dirtyProperties[property]
                    def newProperty = story."$property"
                    if (oldProperty != null) {
                        oldProperty.lastUpdated = new Date()
                        broadcast(function: 'update', message: oldProperty, channel: 'product-' + product.id)
                    }
                    if (newProperty != null) {
                        newProperty.lastUpdated = new Date()
                        broadcast(function: 'update', message: newProperty, channel: 'product-' + product.id)
                    }
                }
            }
            def u = (User) springSecurityService.currentUser
            story.addActivity(u, Activity.CODE_UPDATE, story.name)
            broadcast(function: 'update', message: story, channel: 'product-' + product.id)
        }
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.DELETE)
    void storyDelete(Story story, Map dirtyProperties) {
        log.debug("the story $dirtyProperties.name ($dirtyProperties.id) has been deleted")
        broadcast(function: 'delete', message: [class: story.class, id: dirtyProperties.id, state: dirtyProperties.state], channel: 'product-' + dirtyProperties.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.CREATE)
    void actorCreate(Actor actor, Map dirtyProperties) {
        log.debug("the actor $actor.name ($actor.id) has been created")
        broadcast(function: 'add', message: actor, channel: 'product-' + actor.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.UPDATE)
    void actorUpdate(Actor actor, Map dirtyProperties) {
        log.debug("the actor $actor.name ($actor.id) has been updated")
        broadcast(function: 'update', message: actor, channel: 'product-' + actor.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.DELETE)
    void actorDelete(Actor actor, Map dirtyProperties) {
        log.debug("the actor $dirtyProperties.name ($dirtyProperties.id) has been deleted")
        broadcast(function: 'delete', message: [class: actor.class, id: dirtyProperties.id], channel: 'product-' + dirtyProperties.backlog.id)
    }


    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.CREATE)
    void featureCreate(Feature feature, Map dirtyProperties) {
        log.debug("the feature $feature.name ($feature.id) has been created")
        broadcast(function: 'add', message: feature, channel: 'product-' + feature.backlog.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.UPDATE)
    void featureUpdate(Feature feature, Map dirtyProperties) {
        log.debug("the feature $feature.name ($feature.id) has been updated")
        def productId = feature.backlog.id
        if(dirtyProperties.containsKey('color')) {
            feature.stories.each { story ->
                broadcast(function: 'update', message: story, channel: 'product-' + productId)
            }
        }
        broadcast(function: 'update', message: feature, channel: 'product-' + productId)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.DELETE)
    void featureDelete(Feature feature, Map dirtyProperties) {
        log.debug("the feature $dirtyProperties.name ($dirtyProperties.id) has been deleted")
        def productId = dirtyProperties.backlog.id
        dirtyProperties.stories.each { story ->
            broadcast(function: 'update', message: story, channel: 'product-' + productId)
        }
        broadcast(function: 'delete', message: [class: feature.class, id: dirtyProperties.id], channel: 'product-' + productId)
    }

    @IceScrumListener(domains = ['actor', 'story', 'feature'], eventType = IceScrumEventType.BEFORE_DELETE)
    void backlogElementBeforeDelete(object, Map dirtyProperties) {
        log.debug("the item of ${object.class} and name $object.name will be deleted")
        object.removeAllAttachments()
    }

    @IceScrumListener(domains = ['actor', 'story', 'feature'], eventType = IceScrumEventType.BEFORE_UPDATE)
    void invalidCacheBeforeUpdate(object, Map dirtyProperties) {
        object.lastUpdated = new Date()
    }
}
