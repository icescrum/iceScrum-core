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

import org.icescrum.core.domain.*
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener

class ListenerService {

    def springSecurityService
    def activityService
    def pushService
    // SPECIFIC LISTENERS

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.CREATE)
    void storyCreate(Story story, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(story, user, Activity.CODE_SAVE, story.name)
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, story, story.backlog.id)
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
                        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, oldProperty, product.id)
                    }
                    if (newProperty != null) {
                        newProperty.lastUpdated = new Date()
                        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, newProperty, product.id)
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
            pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, story, product.id)
        }
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.DELETE)
    void storyDelete(Story story, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Story', id: dirtyProperties.id, state: dirtyProperties.state], dirtyProperties.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.CREATE)
    void actorCreate(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, actor, actor.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.UPDATE)
    void actorUpdate(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, actor, actor.backlog.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.DELETE)
    void actorDelete(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Actor', id: dirtyProperties.id], dirtyProperties.backlog.id)
    }


    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.CREATE)
    void featureCreate(Feature feature, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, feature, feature.backlog.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.UPDATE)
    void featureUpdate(Feature feature, Map dirtyProperties) {
        def productId = feature.backlog.id
        if(dirtyProperties.containsKey('color')) {
            feature.stories.each { story ->
                pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, story, productId)
            }
        }
        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, feature, productId)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.DELETE)
    void featureDelete(Feature feature, Map dirtyProperties) {
        def productId = dirtyProperties.backlog.id
        dirtyProperties.stories.each { story ->
            pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, story, productId)
        }
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Feature', id: dirtyProperties.id], productId)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.CREATE)
    void taskCreate(Task task, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(task, user, 'taskSave', task.name)
        def productId = task.backlog ? task.backlog.id : task.parentStory.backlog.id
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, task, productId)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.UPDATE)
    void taskUpdate(Task task, Map dirtyProperties) {
        def productId = task.backlog ? task.backlog.id : task.parentStory.backlog.id
        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, task, productId)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.DELETE)
    void taskDelete(Task task, Map dirtyProperties) {
        def productId = dirtyProperties.backlog ? dirtyProperties.backlog.id : dirtyProperties.parentStory.backlog.id
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Task', id: dirtyProperties.id], productId)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.CREATE)
    void sprintCreate(Sprint sprint, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, sprint, sprint.parentProduct.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.UPDATE)
    void sprintUpdate(Sprint sprint, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, sprint, sprint.parentProduct.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.DELETE)
    void sprintDelete(Sprint sprint, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Sprint', id: dirtyProperties.id], dirtyProperties.parentRelease.parentProduct.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.CREATE)
    void releaseCreate(Release release, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, release, release.parentProduct.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.UPDATE)
    void releaseUpdate(Release release, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, release, release.parentProduct.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.DELETE)
    void releaseDelete(Release release, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Release', id: dirtyProperties.id], dirtyProperties.parentProduct.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.CREATE)
    void acceptanceTestCreate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(acceptanceTest, user, 'acceptanceTestSave', acceptanceTest.name)
        pushService.broadcastToProductUsers(IceScrumEventType.CREATE, acceptanceTest, acceptanceTest.parentProduct.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.UPDATE)
    void acceptanceTestUpdate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        def activityType = 'acceptanceTest' + (dirtyProperties.containsKey('state') ? acceptanceTest.stateEnum.name().toLowerCase().capitalize() : 'Update')
        activityService.addActivity(acceptanceTest, user, activityType, acceptanceTest.name)
        pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, acceptanceTest, acceptanceTest.parentProduct.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.DELETE)
    void acceptanceTestDelete(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def product = dirtyProperties.parentStory.backlog
        activityService.addActivity(dirtyProperties.parentStory, springSecurityService.currentUser, 'acceptanceTestDelete', acceptanceTest.name)
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'AcceptanceTest', id: dirtyProperties.id], product.id)
    }

    @IceScrumListener(domain = 'product', eventType = IceScrumEventType.UPDATE)
    void productUpdate(Product product, Map dirtyProperties) {
        if (product.hasProperty('membersByRole') && product.membersByRole) {
            def newMembers = product.membersByRole
            def oldMembers = dirtyProperties.membersByRole
            def productId = product.id
            newMembers.each { User newMember, int role ->
                if (oldMembers.containsKey(newMember)) {
                    def oldRole = oldMembers[newMember]
                    if (role != oldRole) {
                        pushService.broadcastToSingleUser(IceScrumEventType.UPDATE, [class: 'User', id: newMember.id, updatedRole: [role: role, oldRole: oldRole, product: productId]], newMember)
                    }
                } else {
                    pushService.broadcastToSingleUser(IceScrumEventType.UPDATE, [class: 'User', id: newMember.id, updatedRole: [role: role, product: productId]], newMember)
                }
            }
            oldMembers.each { User oldMember, int role ->
                if (!newMembers.containsKey(oldMember)) {
                    oldMember.preferences.removeEmailsSettings(product.pkey)
                    pushService.broadcastToSingleUser(IceScrumEventType.UPDATE, [class: 'User', id: oldMember.id, updatedRole: [product: productId]], oldMember)
                }
            }
        } else {
            pushService.broadcastToProductUsers(IceScrumEventType.UPDATE, product, product.id)
        }
    }

    @IceScrumListener(domain = 'product', eventType = IceScrumEventType.DELETE)
    void productDelete(Product product, Map dirtyProperties) {
        pushService.broadcastToProductUsers(IceScrumEventType.DELETE, [class: 'Product', id: dirtyProperties.id], dirtyProperties.id)
    }

    // SHARED LISTENERS
    // TODO test product
    @IceScrumListener(domains = ['actor', 'story', 'feature', 'task', 'sprint', 'release', 'product'], eventType = IceScrumEventType.BEFORE_DELETE)
    void backlogElementBeforeDelete(object, Map dirtyProperties) {
        object.removeAllAttachments()
        activityService.removeAllActivities(object)
    }

    @IceScrumListener(domains = ['actor', 'story', 'feature', 'task', 'sprint', 'release', 'acceptanceTest', 'product'], eventType = IceScrumEventType.BEFORE_UPDATE)
    void invalidCacheBeforeUpdate(object, Map dirtyProperties) {
        object.lastUpdated = new Date()
    }
}
