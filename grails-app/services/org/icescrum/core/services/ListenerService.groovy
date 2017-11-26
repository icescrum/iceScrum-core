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
    def releaseService
    def sprintService
    def featureService
    def actorService
    def storyService
    def acceptanceTestService
    def taskService

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.CREATE)
    void storyCreate(Story story, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(story, user ?: story.creator, Activity.CODE_SAVE, story.name)
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, story, story.backlog.id)
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.UPDATE)
    void storyUpdate(Story story, Map dirtyProperties) {
        Project project = story.backlog
        if (dirtyProperties) {
            if (!pushService.isDisabledPushThread() && (dirtyProperties.containsKey('rank') || dirtyProperties.containsKey('state'))) { //isDisabledPushThread() called to avoid useless findAll
                project.stories.findAll { it.isDirty('rank') && it.id != story.id }.each { // If others stories have been updated, push them
                    def storyData = [class: 'Story', id: it.id, rank: it.rank, messageId: 'story-' + it.id + '-rank'] // Avoid pushing everything, which is very costly
                    if (it.parentSprint) {
                        storyData.parentSprint = [id: it.parentSprint.id];
                    }
                    pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, storyData, project.id)
                }
            }
            def newUpdatedProperties = [:]
            ['feature', 'dependsOn', 'parentSprint'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    def oldProperty = dirtyProperties[property]
                    def newProperty = story."$property"
                    if (oldProperty != null) {
                        oldProperty.lastUpdated = new Date()
                        oldProperty.save(flush: true)
                        oldProperty.refresh()
                        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, oldProperty, project.id)
                    }
                    if (newProperty != null) {
                        newProperty.lastUpdated = new Date()
                        newProperty.save(flush: true)
                        newProperty.refresh()
                        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, newProperty, project.id)
                        newUpdatedProperties[property] = true
                    }
                }
            }
            if (dirtyProperties.containsKey('state') && Story.STATE_DONE in [dirtyProperties.state, story.state] && story.feature && !newUpdatedProperties['feature']) {
                story.feature.lastUpdated = new Date()
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, story.feature, project.id)
            }
            if (dirtyProperties.containsKey('state') && Story.STATE_DONE in [dirtyProperties.state, story.state] && story.parentSprint && !newUpdatedProperties['parentSprint']) {
                story.parentSprint.lastUpdated = new Date()
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, story.parentSprint, project.id)
            }
            def user = (User) springSecurityService.currentUser
            ['name', 'type', 'value', 'effort'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, property, dirtyProperties[property]?.toString(), story."$property"?.toString())
                }
            }
            ['feature', 'dependsOn'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    def newValue = story."$property"
                    activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, property, dirtyProperties[property]?.uid?.toString(), newValue?.uid?.toString(), newValue?.name)
                }
            }
            ['notes', 'description'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, property)
                }
            }
            ['parentSprint'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    def tasksData = [class: 'Task', ids: story.tasks.collect({ it.id }), properties: [class: 'Task', state: Task.STATE_WAIT, parentSprint: story.parentSprint ?: null, inProgressDate: null], messageId: 'story-' + story.id + '-tasks']
                    pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, tasksData, story.backlog.id)
                }
            }
        }
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, story, project.id)
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.DELETE)
    void storyDelete(Story story, Map dirtyProperties) {
        if (!pushService.isDisabledPushThread()) { //isDisabledPushThread() called to avoid useless findAll
            def project = Project.get(dirtyProperties.backlog.id)
            project.stories.findAll { it.isDirty('rank') && it.id != dirtyProperties.id }.each {
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, [class: 'Story', id: it.id, rank: it.rank, messageId: 'story-' + it.id + '-rank'], project.id)
            }
            pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Story', id: dirtyProperties.id, state: dirtyProperties.state, messageId: 'story-' + dirtyProperties.id + '-delete'], project.id)

        }
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.CREATE)
    void actorCreate(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, actor, actor.parentProject.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.UPDATE)
    void actorUpdate(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, actor, actor.parentProject.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.DELETE)
    void actorDelete(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Actor', id: dirtyProperties.id, messageId: 'actor-' + dirtyProperties.id + '-delete'], dirtyProperties.parentProject.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.CREATE)
    void featureCreate(Feature feature, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, feature, feature.backlog.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.UPDATE)
    void featureUpdate(Feature feature, Map dirtyProperties) {
        Project project = feature.backlog
        if (!pushService.isDisabledPushThread() && dirtyProperties.containsKey('rank')) { //isDisabledPushThread() called to avoid useless findAll
            project.features.findAll { it.isDirty('rank') && it.id != feature.id }.each { // If others features have been updated, push them
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, [class: 'Feature', id: it.id, rank: it.rank, messageId: 'feature-' + it.id + '-rank'], project.id)
            }
        }
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, feature, project.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.DELETE)
    void featureDelete(Feature feature, Map dirtyProperties) {
        if (!pushService.isDisabledPushThread()) {
            def project = Project.get(dirtyProperties.backlog.id)
            project.features.findAll { it.isDirty('rank') && it.id != dirtyProperties.id }.each { //isDisabledPushThread() called to avoid useless findAll
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, [class: 'Feature', id: it.id, rank: it.rank, messageId: 'feature-' + it.id + '-rank'], project.id)
            }
            pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Feature', id: dirtyProperties.id, messageId: 'feature-' + dirtyProperties.id + '-delete'], project.id)
        }
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.CREATE)
    void taskCreate(Task task, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(task, user ?: task.creator, 'taskSave', task.name)
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, task, task.parentProject.id)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.UPDATE)
    void taskUpdate(Task task, Map dirtyProperties) {
        def project = task.parentProject
        def newStoryUpdated = false
        if (!pushService.isDisabledPushThread() && ['rank', 'parentStory', 'type', 'state'].any { dirtyProperties.containsKey(it) }) { //isDisabledPushThread() called to avoid useless findAll
            task.sprint?.tasks?.findAll { it.isDirty('rank') && it.id != task.id }?.each { // If others tasks have been updated, push them
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, [class: 'Task', id: it.id, rank: it.rank, messageId: 'task-' + it.id + '-rank'], project.id)
            }
        }
        if (dirtyProperties.containsKey('parentStory')) {
            def oldStory = dirtyProperties.parentStory
            if (oldStory != null) {
                oldStory.lastUpdated = new Date()
                oldStory.save(flush: true)
                oldStory.refresh()
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, oldStory, project.id)
            }
            def newStory = task.parentStory
            if (newStory != null) {
                newStory.lastUpdated = new Date()
                newStory.save(flush: true)
                newStory.refresh()
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, newStory, project.id)
                newStoryUpdated = true;
            }
        }
        if (dirtyProperties.containsKey('state') && Task.STATE_DONE in [dirtyProperties.state, task.state] && task.parentStory && !newStoryUpdated) {
            task.parentStory.lastUpdated = new Date()
            pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, task.parentStory, project.id)
        }
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, task, project.id)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.DELETE)
    void taskDelete(Task task, Map dirtyProperties) {
        if (!pushService.isDisabledPushThread()) {
            def container = dirtyProperties.parentStory ?: dirtyProperties.backlog
            container.tasks.findAll { it.isDirty('rank') && it.id != dirtyProperties.id }.each {
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, [class: 'Task', id: it.id, rank: it.rank, messageId: 'task-' + it.id + '-rank'], it.parentProject.id)
            }
            pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Task', id: dirtyProperties.id, messageId: 'task-' + dirtyProperties.id + '-delete'], dirtyProperties.parentProject.id)
        }
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.CREATE)
    void sprintCreate(Sprint sprint, Map dirtyProperties) {
        def project = sprint.parentProject
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, sprint.parentRelease, project.id) // Push parentRelease.closeable
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, sprint, project.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.UPDATE)
    void sprintUpdate(Sprint sprint, Map dirtyProperties) {
        def project = sprint.parentProject
        if (!pushService.isDisabledPushThread() && dirtyProperties.containsKey('state') && sprint.state == Sprint.STATE_DONE) {
            def nextSprintSameRelease = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.orderNumber + 1)
            if (nextSprintSameRelease) {
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, nextSprintSameRelease, project.id) // Push nextSprint.activable
            } else {
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, sprint.parentRelease, project.id) // Push parentRelease.closeable
            }
        }
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, sprint, project.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.DELETE)
    void sprintDelete(Sprint sprint, Map dirtyProperties) {
        def project = dirtyProperties.parentRelease.parentProject
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, dirtyProperties.parentRelease, project.id) // Push parentRelease.closeable
        pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Sprint', id: dirtyProperties.id, messageId: 'sprint-' + dirtyProperties.id + '-delete'], project.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.CREATE)
    void releaseCreate(Release release, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, release, release.parentProject.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.UPDATE)
    void releaseUpdate(Release release, Map dirtyProperties) {
        def project = release.parentProject
        if (dirtyProperties.containsKey('state')) {
            if (release.state == Release.STATE_DONE && release.nextRelease) {
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, release.nextRelease, project.id) // Push nextRelease.activable
            } else if (release.state == Release.STATE_INPROGRESS && release.sprints) {
                pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, release.sprints.first(), project.id) // Push firstSprint.activable
            }
        }
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, release, project.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.DELETE)
    void releaseDelete(Release release, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Release', id: dirtyProperties.id, messageId: 'release-' + dirtyProperties.id + '-delete'], dirtyProperties.parentProject.id)
    }

    @IceScrumListener(domain = 'activity', eventType = IceScrumEventType.CREATE)
    void activityCreate(Activity activity, Map dirtyProperties) {
        if (activity.parentType == 'story' && activity.important) {
            Project project = Story.get(activity.parentRef).backlog
            def users = project.allUsersAndOwnerAndStakeholders - activity.poster
            pushService.broadcastToUsers(IceScrumEventType.CREATE, activity, users)
        }
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.CREATE)
    void acceptanceTestCreate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        def project = acceptanceTest.parentProject
        activityService.addActivity(acceptanceTest, user ?: acceptanceTest.parentStory.creator, 'acceptanceTestSave', acceptanceTest.name)
        pushService.broadcastToProjectChannel(IceScrumEventType.CREATE, acceptanceTest, project.id)
        // TODO remove when using a proper AT cache on the client side. Required to update acceptanceTests_count because we can't use client side "sync".
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, acceptanceTest.parentStory, project.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.UPDATE)
    void acceptanceTestUpdate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        def project = acceptanceTest.parentProject
        def activityType = 'acceptanceTest' + (dirtyProperties.containsKey('state') ? acceptanceTest.stateEnum.name().toLowerCase().capitalize() : 'Update')
        activityService.addActivity(acceptanceTest, user, activityType, acceptanceTest.name)
        if (dirtyProperties.containsKey('state')) {
            acceptanceTest.parentStory.lastUpdated = new Date()
            pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, acceptanceTest.parentStory, project.id) // push story.testState
        }
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, acceptanceTest, project.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.DELETE)
    void acceptanceTestDelete(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def project = dirtyProperties.parentStory.backlog
        activityService.addActivity(dirtyProperties.parentStory, springSecurityService.currentUser, 'acceptanceTestDelete', acceptanceTest.name)
        pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'AcceptanceTest', id: dirtyProperties.id, messageId: 'acceptance-' + dirtyProperties.id + '-delete'], project.id)
        // TODO remove when using a proper AT cache on the client side. Required to update acceptanceTests_count because we can't use client side "sync".
        pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, dirtyProperties.parentStory, project.id)
    }

    @IceScrumListener(domain = 'project', eventType = IceScrumEventType.UPDATE)
    void projectUpdate(Project project, Map dirtyProperties) {
        if (project.hasProperty('membersByRole') && project.membersByRole) {
            def newMembers = project.membersByRole
            def oldMembers = dirtyProperties.membersByRole
            def shortProject = [id: project.id, pkey: project.pkey, name: project.name]
            newMembers.each { User newMember, int role ->
                if (oldMembers.containsKey(newMember)) {
                    def oldRole = oldMembers[newMember]
                    if (role != oldRole) {
                        pushService.broadcastToUsers(IceScrumEventType.UPDATE, [class: 'User', id: newMember.id, updatedRole: [role: role, oldRole: oldRole, project: shortProject], messageId: 'user-' + newMember.id + '-updaterole-' + shortProject.pkey], [newMember])
                    }
                } else {
                    pushService.broadcastToUsers(IceScrumEventType.UPDATE, [class: 'User', id: newMember.id, updatedRole: [role: role, project: shortProject, messageId: 'user-' + newMember.id + '-role-' + shortProject.pkey]], [newMember])
                }
            }
            oldMembers.each { User oldMember, int role ->
                if (!newMembers.containsKey(oldMember)) {
                    oldMember.preferences.removeEmailsSettings(project.pkey)
                    pushService.broadcastToUsers(IceScrumEventType.UPDATE, [class: 'User', id: oldMember.id, updatedRole: [project: shortProject], messageId: 'user-' + oldMember.id + '-oldrole-' + shortProject.pkey], [oldMember])
                }
            }
        } else {
            pushService.broadcastToProjectChannel(IceScrumEventType.UPDATE, project, project.id)
        }
    }

    @IceScrumListener(domain = 'project', eventType = IceScrumEventType.BEFORE_DELETE)
    void projectBeforeDelete(Project project, Map dirtyProperties) {
        project.tasks.each { Task task ->
            taskService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, task, [:])
        }
        project.stories.each { Story story ->
            storyService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, story, [:])
            story.acceptanceTests.each { AcceptanceTest acceptanceTest ->
                acceptanceTestService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, acceptanceTest, [:])
            }
        }
        project.releases.each { Release release ->
            release.sprints.each { Sprint sprint ->
                sprintService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, sprint, [:])
            }
            releaseService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, release, [:])
        }
        project.features.each { Feature feature ->
            featureService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, feature, [:])
        }
        project.actors.each { Actor actor ->
            actorService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, actor, [:])
        }
    }

    @IceScrumListener(domain = 'project', eventType = IceScrumEventType.DELETE)
    void projectDelete(Project project, Map dirtyProperties) {
        pushService.broadcastToProjectChannel(IceScrumEventType.DELETE, [class: 'Project', id: dirtyProperties.id, messageId: 'project-' + dirtyProperties.id + '-delete'], dirtyProperties.id)
    }

    // SHARED LISTENERS
    @IceScrumListener(domains = ['story', 'feature', 'task', 'sprint', 'release', 'project'], eventType = IceScrumEventType.BEFORE_DELETE)
    void backlogElementBeforeDelete(object, Map dirtyProperties) {
        object.removeAllAttachments()
    }

    @IceScrumListener(domains = ['story', 'feature', 'task', 'sprint', 'release', 'acceptanceTest', 'project'], eventType = IceScrumEventType.BEFORE_UPDATE)
    void invalidCacheBeforeUpdate(object, Map dirtyProperties) {
        object.lastUpdated = new Date()
    }
}
