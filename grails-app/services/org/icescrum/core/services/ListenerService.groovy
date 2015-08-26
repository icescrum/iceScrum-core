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

import grails.converters.JSON
import org.atmosphere.cpr.Broadcaster
import org.icescrum.atmosphere.IceScrumBroadcaster
import org.icescrum.core.domain.Activity
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.event.IceScrumEventType

class ListenerService {

    def springSecurityService
    def activityService
    def atmosphereMeteor
    // SPECIFIC LISTENERS

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.CREATE)
    void storyCreate(Story story, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(story, user, Activity.CODE_SAVE, story.name)
        Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory.lookup(IceScrumBroadcaster.class, '/stream/app/product-'+story.backlog.id)
        if (broadcaster) { // TODO FIX IT SHOULD BE THERE BUT IT IS NOT
            broadcaster.broadcast((story as JSON).toString()) // toString() required to eagerly generate the String (lazy raise an error because no session in atmosphere thread)
        }
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.UPDATE)
    void storyUpdate(Story story, Map dirtyProperties) {
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
            def user = (User) springSecurityService.currentUser
            ['name', 'type'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, property, dirtyProperties[property]?.toString(), story."$property"?.toString())
                }
            }
            ['actor', 'feature', 'dependsOn'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, property, dirtyProperties[property]?.uid?.toString(), story."$property"?.uid?.toString())
                }
            }
            ['notes', 'description'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, property)
                }
            }
            broadcast(function: 'update', message: story, channel: 'product-' + product.id)
        }
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.DELETE)
    void storyDelete(Story story, Map dirtyProperties) {
        broadcast(function: 'delete', message: [class: story.class, id: dirtyProperties.id, state: dirtyProperties.state], channel: 'product-' + dirtyProperties.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.CREATE)
    void actorCreate(Actor actor, Map dirtyProperties) {
        broadcast(function: 'add', message: actor, channel: 'product-' + actor.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.UPDATE)
    void actorUpdate(Actor actor, Map dirtyProperties) {
        broadcast(function: 'update', message: actor, channel: 'product-' + actor.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.DELETE)
    void actorDelete(Actor actor, Map dirtyProperties) {
        broadcast(function: 'delete', message: [class: actor.class, id: dirtyProperties.id], channel: 'product-' + dirtyProperties.backlog.id)
    }


    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.CREATE)
    void featureCreate(Feature feature, Map dirtyProperties) {
        broadcast(function: 'add', message: feature, channel: 'product-' + feature.backlog.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.UPDATE)
    void featureUpdate(Feature feature, Map dirtyProperties) {
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
        def productId = dirtyProperties.backlog.id
        dirtyProperties.stories.each { story ->
            broadcast(function: 'update', message: story, channel: 'product-' + productId)
        }
        broadcast(function: 'delete', message: [class: feature.class, id: dirtyProperties.id], channel: 'product-' + productId)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.CREATE)
    void taskCreate(Task task, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(task, user, 'taskSave', task.name)
        def productId = task.backlog ? task.backlog.id : task.parentStory.backlog.id
        broadcast(function: 'add', message: task, channel: 'product-' + productId)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.UPDATE)
    void taskUpdate(Task task, Map dirtyProperties) {
        def productId = task.backlog ? task.backlog.id : task.parentStory.backlog.id
        broadcast(function: 'update', message: task, channel: 'product-' + productId)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.DELETE)
    void taskDelete(Task task, Map dirtyProperties) {
        def productId = dirtyProperties.backlog ? dirtyProperties.backlog.id : dirtyProperties.parentStory.backlog.id
        broadcast(function: 'delete', message: [class: task.class, id: dirtyProperties.id], channel: 'product-' + productId)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.CREATE)
    void sprintCreate(Sprint sprint, Map dirtyProperties) {
        broadcast(function: 'add', message: sprint, channel: 'product-' + sprint.parentProduct.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.UPDATE)
    void sprintUpdate(Sprint sprint, Map dirtyProperties) {
        broadcast(function: 'update', message: sprint, channel: 'product-' + sprint.parentProduct.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.DELETE)
    void sprintDelete(Sprint sprint, Map dirtyProperties) {
        broadcast(function: 'delete', message: [class: sprint.class, id: dirtyProperties.id], channel: 'product-' + dirtyProperties.parentRelease.parentProduct.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.CREATE)
    void releaseCreate(Release release, Map dirtyProperties) {
        broadcast(function: 'add', message: release, channel: 'product-' + release.parentProduct.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.UPDATE)
    void releaseUpdate(Release release, Map dirtyProperties) {
        broadcast(function: 'update', message: release, channel: 'product-' + release.parentProduct.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.DELETE)
    void releaseDelete(Release release, Map dirtyProperties) {
        broadcast(function: 'delete', message: [class: release.class, id: dirtyProperties.id], channel: 'product-' + dirtyProperties.parentProduct.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.CREATE)
    void acceptanceTestCreate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(acceptanceTest, user, 'acceptanceTestSave', acceptanceTest.name)
        broadcast(function: 'add', message: acceptanceTest, channel: 'product-' + acceptanceTest.parentProduct.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.UPDATE)
    void acceptanceTestUpdate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        def activityType = 'acceptanceTest' + (dirtyProperties.containsKey('state') ? acceptanceTest.stateEnum.name().toLowerCase().capitalize() : 'Update')
        activityService.addActivity(acceptanceTest, user, activityType, acceptanceTest.name)
        broadcast(function: 'update', message: acceptanceTest, channel: 'product-' + acceptanceTest.parentProduct.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.DELETE)
    void acceptanceTestDelete(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def product = dirtyProperties.parentStory.backlog
        activityService.addActivity(dirtyProperties.parentStory, springSecurityService.currentUser, 'acceptanceTestDelete', acceptanceTest.name)
        broadcast(function: 'delete', message: [class: acceptanceTest.class, id: dirtyProperties.id], channel: 'product-' + product.id)
    }

    // SHARED LISTENERS
    // TODO add product
    @IceScrumListener(domains = ['actor', 'story', 'feature', 'task', 'sprint', 'release'], eventType = IceScrumEventType.BEFORE_DELETE)
    void backlogElementBeforeDelete(object, Map dirtyProperties) {
        object.removeAllAttachments()
        activityService.removeAllActivities(object)
    }

    @IceScrumListener(domains = ['actor', 'story', 'feature', 'task', 'sprint', 'release', 'acceptanceTest'], eventType = IceScrumEventType.BEFORE_UPDATE)
    void invalidCacheBeforeUpdate(object, Map dirtyProperties) {
        object.lastUpdated = new Date()
    }

    void broadcast(def a){

    }
}
