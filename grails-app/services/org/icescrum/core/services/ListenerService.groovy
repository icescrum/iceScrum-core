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

import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.grails.comments.Comment
import org.icescrum.core.domain.*
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener
import org.icescrum.plugins.attachmentable.domain.Attachment

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
    def commentService
    def meetingService
    def grailsApplication
    def attachmentService

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.CREATE)
    void storyCreate(Story story, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(story, user ?: story.creator, Activity.CODE_SAVE, story.name)
        ['feature'].each { property ->
            def relationalProperty = story."$property"
            if (relationalProperty != null) {
                relationalProperty.lastUpdated = new Date()
                relationalProperty.save(flush: true)
                relationalProperty.refresh()
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, relationalProperty, story.backlog.id)
            }
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, story, story.backlog.id)
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.UPDATE)
    void storyUpdate(Story story, Map dirtyProperties) {
        Project project = story.backlog
        if (dirtyProperties) {
            def newUpdatedProperties = [:]
            ['feature', 'dependsOn', 'parentSprint'].each { property ->
                if (dirtyProperties.containsKey(property)) {
                    def oldProperty = dirtyProperties[property]
                    def newProperty = story."$property"
                    if (oldProperty != null) {
                        oldProperty.lastUpdated = new Date()
                        oldProperty.save(flush: true)
                        oldProperty.refresh()
                        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, oldProperty, project.id)
                    }
                    if (newProperty != null) {
                        newProperty.lastUpdated = new Date()
                        newProperty.save(flush: true)
                        newProperty.refresh()
                        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, newProperty, project.id)
                        newUpdatedProperties[property] = true
                    }
                }
            }
            if (dirtyProperties.containsKey('state') && story.state >= Story.STATE_ESTIMATED && dirtyProperties.state >= Story.STATE_ESTIMATED && story.feature && !newUpdatedProperties['feature']) {
                story.feature.lastUpdated = new Date()
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, story.feature, project.id)
            }
            if (dirtyProperties.containsKey('state') && Story.STATE_DONE in [dirtyProperties.state, story.state] && story.parentSprint && !newUpdatedProperties['parentSprint']) {
                story.parentSprint.lastUpdated = new Date()
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, story.parentSprint, project.id)
            }
            if (dirtyProperties.containsKey('effort') && story.parentSprint && story.parentSprint.state == Sprint.STATE_WAIT) {
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, story.parentSprint, project.id)
            }
            def user = (User) springSecurityService.currentUser
            if (dirtyProperties.containsKey('state')) {
                activityService.addActivity(story, user, 'updateState', story.name, 'state', dirtyProperties.state?.toString(), story.state?.toString())
            }
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
            if (dirtyProperties.containsKey('parentSprint')) {
                activityService.addActivity(story, user, Activity.CODE_UPDATE, story.name, 'parentSprint', dirtyProperties.parentSprint?.id?.toString(), story.parentSprint?.id?.toString(), story.parentSprint?.fullName)
                def tasksData = [class     : 'Task',
                                 ids       : story.tasks.findAll { it.state != Task.STATE_DONE }*.id,
                                 properties: [class: 'Task', state: Task.STATE_WAIT, backlog: story.parentSprint ? getSprintAsShort(story.parentSprint) : null, inProgressDate: null],
                                 messageId : 'story-' + story.id + '-tasks']
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, tasksData, story.backlog.id)
            }
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, story, project.id)
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.PARTIAL_UPDATE)
    void storyPartialUpdate(Story story, Map dirtyProperties) {
        if (dirtyProperties.containsKey('rank')) {
            Project project = story.backlog
            def storyData = [class: 'Story', id: story.id, rank: story.rank, messageId: 'story-' + story.id + '-rank']
            if (story.parentSprint) {
                storyData.parentSprint = getSprintAsShort(story.parentSprint)
            }
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, storyData, project.id)
        }
    }

    @IceScrumListener(domain = 'story', eventType = IceScrumEventType.DELETE)
    void storyDelete(Story story, Map dirtyProperties) {
        if (!pushService.isDisabledPushThread()) { // isDisabledPushThread() called to avoid useless findAll
            def project = Project.get(dirtyProperties.backlog.id)
            if (dirtyProperties.feature) {
                def feature = dirtyProperties.feature
                feature.lastUpdated = new Date()
                feature.save(flush: true)
                feature.refresh()
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, feature, project.id)
            }
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'Story', id: dirtyProperties.id, state: dirtyProperties.state, messageId: 'story-' + dirtyProperties.id + '-delete'], project.id)
        }
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.CREATE)
    void actorCreate(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, actor, actor.parentProject.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.UPDATE)
    void actorUpdate(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, actor, actor.parentProject.id)
    }

    @IceScrumListener(domain = 'actor', eventType = IceScrumEventType.DELETE)
    void actorDelete(Actor actor, Map dirtyProperties) {
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'Actor', id: dirtyProperties.id, messageId: 'actor-' + dirtyProperties.id + '-delete'], dirtyProperties.parentProject.id)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.CREATE)
    void featureCreate(Feature feature, Map dirtyProperties) {
        if (feature.backlog) {
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, feature, feature.backlog.id)
        } else if (feature.portfolio) {
            pushService.broadcastToPortfolioChannel(IceScrumEventType.CREATE, feature, feature.portfolio.id)
        }
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.UPDATE)
    void featureUpdate(Feature feature, Map dirtyProperties) {
        def workspace
        def workspaceType
        if (feature.backlog) {
            workspace = feature.backlog
            workspaceType = WorkspaceType.PROJECT
        } else if (feature.portfolio) {
            workspace = feature.portfolio
            workspaceType = WorkspaceType.PORTFOLIO
        }
        if (!pushService.isDisabledPushThread() && dirtyProperties.containsKey('rank')) { // isDisabledPushThread() called to avoid useless findAll
            workspace.features.findAll { it.isDirty('rank') && it.id != feature.id }.each { // If others features have been updated, push them
                pushService.broadcastToWorkspaceChannel(IceScrumEventType.UPDATE, [class: 'Feature', id: it.id, rank: it.rank, messageId: 'feature-' + it.id + '-rank'], workspace.id, workspaceType)
            }
        }
        pushService.broadcastToWorkspaceChannel(IceScrumEventType.UPDATE, feature, workspace.id, workspaceType)
    }

    @IceScrumListener(domain = 'feature', eventType = IceScrumEventType.DELETE)
    void featureDelete(Feature feature, Map dirtyProperties) {
        if (!pushService.isDisabledPushThread()) { // isDisabledPushThread() called to avoid useless findAll
            def workspace
            def workspaceType
            if (dirtyProperties.backlog) {
                workspace = Project.get(dirtyProperties.backlog.id)
                workspaceType = WorkspaceType.PROJECT
            } else if (dirtyProperties.portfolio) {
                workspace = Portfolio.get(dirtyProperties.portfolio.id)
                workspaceType = WorkspaceType.PORTFOLIO
            }
            workspace.features.findAll { it.isDirty('rank') && it.id != dirtyProperties.id }.each {
                pushService.broadcastToWorkspaceChannel(IceScrumEventType.UPDATE, [class: 'Feature', id: it.id, rank: it.rank, messageId: 'feature-' + it.id + '-rank'], workspace.id, workspaceType)
            }
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.DELETE, [class: 'Feature', id: dirtyProperties.id, messageId: 'feature-' + dirtyProperties.id + '-delete'], workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.CREATE)
    void taskCreate(Task task, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        activityService.addActivity(task, user ?: task.creator, 'taskSave', task.name)
        if (task.parentStory) {
            pushStory(task.parentStory)
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, task, task.parentProject.id)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.UPDATE)
    void taskUpdate(Task task, Map dirtyProperties) {
        def project = task.parentProject
        def newStoryUpdated = false
        if (!pushService.isDisabledPushThread() && ['rank', 'parentStory', 'type', 'state'].any { dirtyProperties.containsKey(it) }) { // isDisabledPushThread() called to avoid useless findAll
            def pushOtherRank = { tasks -> // If others tasks have been updated, push them
                tasks?.findAll { it.isDirty('rank') && it.id != task.id }?.each {
                    pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, [class: 'Task', id: it.id, rank: it.rank, messageId: 'task-' + it.id + '-rank'], project.id)
                }
            }
            pushOtherRank(((Sprint) task.backlog)?.tasks)
            pushOtherRank(task.parentStory?.tasks)
        }
        if (dirtyProperties.containsKey('parentStory')) {
            if (dirtyProperties.parentStory != null) {
                pushStory(dirtyProperties.parentStory)
            }
            if (task.parentStory != null) {
                pushStory(task.parentStory)
                newStoryUpdated = true;
            }
        }
        if ((dirtyProperties.containsKey('estimation') && task.parentStory) || (dirtyProperties.containsKey('state') && Task.STATE_DONE in [dirtyProperties.state, task.state] && task.parentStory && !newStoryUpdated)) {
            task.parentStory.lastUpdated = new Date()
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, task.parentStory, project.id)
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, task, project.id)
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.DELETE)
    void taskDelete(Task task, Map dirtyProperties) {
        if (!pushService.isDisabledPushThread()) {
            def container = dirtyProperties.parentStory ?: dirtyProperties.backlog
            container.tasks.findAll { it.isDirty('rank') && it.id != dirtyProperties.id }.each {
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, [class: 'Task', id: it.id, rank: it.rank, messageId: 'task-' + it.id + '-rank'], it.parentProject.id)
            }
            if (dirtyProperties.parentStory) {
                pushStory(dirtyProperties.parentStory)
            }
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'Task', id: dirtyProperties.id, messageId: 'task-' + dirtyProperties.id + '-delete'], dirtyProperties.parentProject.id)
        }
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.CREATE)
    void sprintCreate(Sprint sprint, Map dirtyProperties) {
        def project = sprint.parentProject
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, sprint.parentRelease, project.id) // Push parentRelease.closeable
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, sprint, project.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.UPDATE)
    void sprintUpdate(Sprint sprint, Map dirtyProperties) {
        def project = sprint.parentProject
        if (!pushService.isDisabledPushThread() && dirtyProperties.containsKey('state') && sprint.state == Sprint.STATE_DONE) {
            def nextSprintSameRelease = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.orderNumber + 1)
            if (nextSprintSameRelease) {
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, nextSprintSameRelease, project.id) // Push nextSprint.activable
            } else {
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, sprint.parentRelease, project.id) // Push parentRelease.closeable
                def nextRelease = sprint.parentRelease.nextRelease
                if (nextRelease) {
                    pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, nextRelease, project.id) // Push nextRelease.activable
                }
                def nextSprint = sprint.nextSprint
                if (nextSprint) {
                    pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, nextSprint, project.id) // Push nextSprint.activable
                }
            }
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, sprint, project.id)
    }

    @IceScrumListener(domain = 'sprint', eventType = IceScrumEventType.DELETE)
    void sprintDelete(Sprint sprint, Map dirtyProperties) {
        def project = dirtyProperties.parentRelease.parentProject
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, dirtyProperties.parentRelease, project.id) // Push parentRelease.closeable
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'Sprint', id: dirtyProperties.id, messageId: 'sprint-' + dirtyProperties.id + '-delete'], project.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.CREATE)
    void releaseCreate(Release release, Map dirtyProperties) {
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, release, release.parentProject.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.UPDATE)
    void releaseUpdate(Release release, Map dirtyProperties) {
        def project = release.parentProject
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, release, project.id)
    }

    @IceScrumListener(domain = 'release', eventType = IceScrumEventType.DELETE)
    void releaseDelete(Release release, Map dirtyProperties) {
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'Release', id: dirtyProperties.id, messageId: 'release-' + dirtyProperties.id + '-delete'], dirtyProperties.parentProject.id)
    }

    @IceScrumListener(domain = 'activity', eventType = IceScrumEventType.CREATE)
    void activityCreate(Activity activity, Map dirtyProperties) {
        if (activity.parentType == 'story' && activity.important) {
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, activity, Story.get(activity.parentRef).backlog.id)
        }
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.CREATE)
    void acceptanceTestCreate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        def project = acceptanceTest.parentStory.backlog
        activityService.addActivity(acceptanceTest, user ?: acceptanceTest.parentStory.creator, 'acceptanceTestSave', acceptanceTest.name)
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.CREATE, acceptanceTest, project.id)
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, acceptanceTest.parentStory, project.id) // Push count
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.UPDATE)
    void acceptanceTestUpdate(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def user = (User) springSecurityService.currentUser
        def project = acceptanceTest.parentStory.backlog
        def activityType = 'acceptanceTest' + (dirtyProperties.containsKey('state') ? acceptanceTest.stateEnum.name().toLowerCase().capitalize() : 'Update')
        activityService.addActivity(acceptanceTest, user, activityType, acceptanceTest.name)
        if (dirtyProperties.containsKey('state')) {
            acceptanceTest.parentStory.lastUpdated = new Date()
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, acceptanceTest.parentStory, project.id) // push story.testState
        }
        if (!pushService.isDisabledPushThread() && dirtyProperties.containsKey('rank')) { // isDisabledPushThread() called to avoid useless findAll
            acceptanceTest.parentStory.acceptanceTests.findAll { it.isDirty('rank') && it.id != acceptanceTest.id }.each {
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, [class: 'AcceptanceTest', id: it.id, rank: it.rank, messageId: 'acceptanceTest-' + it.id + '-rank'], project.id)
            }
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, acceptanceTest, project.id)
    }

    @IceScrumListener(domain = 'acceptanceTest', eventType = IceScrumEventType.DELETE)
    void acceptanceTestDelete(AcceptanceTest acceptanceTest, Map dirtyProperties) {
        def project = dirtyProperties.parentStory.backlog
        activityService.addActivity(dirtyProperties.parentStory, springSecurityService.currentUser, 'acceptanceTestDelete', acceptanceTest.name)
        if (!pushService.isDisabledPushThread()) { // isDisabledPushThread() called to avoid useless findAll
            Story.get(dirtyProperties.parentStory.id).acceptanceTests.findAll { it.isDirty('rank') && it.id != dirtyProperties.id }.each {
                pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, [class: 'AcceptanceTest', id: it.id, rank: it.rank, messageId: 'acceptanceTest-' + it.id + '-rank'], project.id)
            }
        }
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'AcceptanceTest', id: dirtyProperties.id, messageId: 'acceptance-' + dirtyProperties.id + '-delete'], project.id)
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, dirtyProperties.parentStory, project.id) // Push count
    }

    @IceScrumListener(domain = 'comment', eventType = IceScrumEventType.CREATE)
    void commentCreate(Comment comment, Map dirtyProperties) {
        def workspace = commentService.getWorkspace(comment)
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            def commentData = commentService.getRenderableComment(comment)
            commentData.messageId = 'comment-CREATE-' + comment.id
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.CREATE, commentData, workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'comment', eventType = IceScrumEventType.UPDATE)
    void commentUpdate(Comment comment, Map dirtyProperties) {
        def workspace = commentService.getWorkspace(comment)
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            def commentData = commentService.getRenderableComment(comment)
            commentData.messageId = 'comment-UPDATE-' + comment.id
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.UPDATE, commentData, workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'comment', eventType = IceScrumEventType.DELETE)
    void commentDelete(Comment comment, Map dirtyProperties) {
        def workspace = dirtyProperties.workspace
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.DELETE, [class: 'Comment', id: dirtyProperties.id, messageId: 'comment-' + dirtyProperties.id + '-delete'], workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'attachment', eventType = IceScrumEventType.CREATE)
    void attachmentCreate(Attachment attachment, Map dirtyProperties) {
        def workspace = attachmentService.getWorkspace(attachment)
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            def attachmentData = attachmentService.getRenderableAttachment(attachment)
            attachmentData.messageId = 'attachment-CREATE-' + attachment.id
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.CREATE, attachmentData, workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'attachment', eventType = IceScrumEventType.UPDATE)
    void attachmentUpdate(Attachment attachment, Map dirtyProperties) {
        def workspace = attachmentService.getWorkspace(attachment)
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            def attachmentData = attachmentService.getRenderableAttachment(attachment)
            attachmentData.messageId = 'attachment-UPDATE-' + attachment.id
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.UPDATE, attachmentData, workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'attachment', eventType = IceScrumEventType.DELETE)
    void attachmentDelete(Attachment attachment, Map dirtyProperties) {
        def workspace = dirtyProperties.workspace
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.DELETE, [class: 'Attachment', id: dirtyProperties.id, attachmentable: dirtyProperties.attachmentable, messageId: 'attachment-' + dirtyProperties.id + '-delete'], workspace.id, workspaceType)
        }
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
            pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, project, project.id)
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
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.DELETE, [class: 'Project', id: dirtyProperties.id, messageId: 'project-' + dirtyProperties.id + '-delete'], dirtyProperties.id)
    }

    @IceScrumListener(domain = 'portfolio', eventType = IceScrumEventType.BEFORE_DELETE)
    void portfolioBeforeDelete(Portfolio portfolio, Map dirtyProperties) {
        portfolio.features.each { Feature feature ->
            featureService.publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, feature, [:])
        }
    }

    @IceScrumListener(domain = 'meeting', eventType = IceScrumEventType.CREATE)
    void meetingCreate(Meeting meeting, Map dirtyProperties) {
        def workspace = meetingService.getWorkspace(meeting)
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.CREATE, meeting, workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'meeting', eventType = IceScrumEventType.UPDATE)
    void meetingUpdate(Meeting meeting, Map dirtyProperties) {
        def workspace = meetingService.getWorkspace(meeting)
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.UPDATE, meeting, workspace.id, workspaceType)
        }
    }

    @IceScrumListener(domain = 'meeting', eventType = IceScrumEventType.DELETE)
    void meetingDelete(Meeting meeting, Map dirtyProperties) {
        def workspace = dirtyProperties.workspace
        if (workspace) {
            def workspaceType = workspace instanceof Portfolio ? WorkspaceType.PORTFOLIO : WorkspaceType.PROJECT
            pushService.broadcastToWorkspaceChannel(IceScrumEventType.DELETE, [class: 'Meeting', id: dirtyProperties.id, messageId: 'meeting-' + dirtyProperties.id + '-delete'], workspace.id, workspaceType)
        }
    }

    // SHARED LISTENERS
    @IceScrumListener(domains = ['story', 'feature', 'task', 'sprint', 'release', 'project'], eventType = IceScrumEventType.BEFORE_DELETE)
    void attachmentableBeforeDelete(attachmentable, Map dirtyProperties) {
        attachmentable.removeAllAttachments()
    }

    @IceScrumListener(domains = ['story', 'feature', 'task'], eventType = IceScrumEventType.BEFORE_DELETE)
    void backlogElementBeforeDelete(backlogElement, Map dirtyProperties) {
        backlogElement = GrailsHibernateUtil.unwrapIfProxy(backlogElement) // Prevent issues with comment deletion
        backlogElement.tags = []
        backlogElement.deleteComments()
    }

    @IceScrumListener(domains = ['story', 'feature', 'task', 'sprint', 'release', 'acceptanceTest', 'project'], eventType = IceScrumEventType.BEFORE_UPDATE)
    void invalidCacheBeforeUpdate(object, Map dirtyProperties) {
        object.lastUpdated = new Date()
    }

    private Map getSprintAsShort(Sprint sprint) {
        def sprintData = [id: sprint.id, class: 'Sprint']
        grailsApplication.config.icescrum.marshaller.sprint.asShort.each { String sprintProperty ->
            sprintData[sprintProperty] = sprint[sprintProperty]
        }
        return sprintData
    }

    private boolean pushStory(Story story) {
        story.lastUpdated = new Date()
        story.save(flush: true)
        story.refresh()
        pushService.broadcastToProjectRelatedChannels(IceScrumEventType.UPDATE, story, story.backlog.id)
    }
}
