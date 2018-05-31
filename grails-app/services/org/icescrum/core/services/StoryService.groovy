/*
 * Copyright (c) 2015 Kagilum SAS
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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Colin Bontemps (cbontemps@kagilum.com)
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import grails.util.GrailsNameUtils
import grails.validation.ValidationException
import org.apache.commons.io.FileUtils
import org.grails.comments.Comment
import org.grails.comments.CommentLink
import org.icescrum.core.domain.*
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.utils.DateUtils
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.springframework.security.access.AccessDeniedException
import org.springframework.security.access.prepost.PreAuthorize

import java.util.regex.Matcher

@Transactional
class StoryService extends IceScrumEventPublisher {
    def taskService
    def springSecurityService
    def clicheService
    def featureService
    def attachmentableService
    def securityService
    def acceptanceTestService
    def activityService
    def pushService
    def i18nService

    @PreAuthorize('isAuthenticated() and !archivedProject(#project)')
    void save(Story story, Project project, User user) {
        if (!story.effort) {
            story.effort = null
        }
        story.backlog = project
        story.creator = user
        manageActors(story, project)
        story.uid = Story.findNextUId(project.id)
        if (!story.suggestedDate) {
            story.suggestedDate = new Date()
        }
        story.affectVersion = (story.type == Story.TYPE_DEFECT ? story.affectVersion : null)
        story.addToFollowers(user)
        project.allUsers.findAll {
            user.id != it.id && project.pkey in it.preferences.emailsSettings.autoFollow
        }.each {
            story.addToFollowers(user)
        }
        def rank = story.sameBacklogStories ? story.sameBacklogStories.max { it.rank }.rank + 1 : 1
        setRank(story, rank)
        publishSynchronousEvent(IceScrumEventType.BEFORE_CREATE, story)
        story.save(flush: true)
        story.refresh() // required to initialize collections to empty list
        project.addToStories(story)
        publishSynchronousEvent(IceScrumEventType.CREATE, story)
    }

    // TODO replace stories by a single one and call the service in a loop in story controller
    @PreAuthorize('isAuthenticated() and !archivedProject(#stories[0].backlog)')
    void delete(Collection<Story> stories, newObject = null, reason = null) {
        def project = stories[0].backlog
        stories.each { story ->
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, story)
            // Custom properties
            if (newObject) {
                dirtyProperties.newObject = newObject
            }
            if (story.state >= Story.STATE_PLANNED) {
                throw new BusinessException(code: 'is.story.error.not.deleted.state')
            }
            if (!(story.creator.id == springSecurityService.currentUser?.id) && !securityService.productOwner(project.id, springSecurityService.authentication)) {
                throw new BusinessException(code: 'is.story.error.not.deleted.permission')
            }
            if (story.feature) {
                story.feature.removeFromStories(story)
            }
            if (story.dependsOn) {
                story.dependsOn.removeFromDependences(story)
            }
            story.dependences?.collect { it }?.each { // Collect to avoid ConcurrentModificationException
                story.removeFromDependences(it)
                it.dependsOn = null
                it.save()
            }
            resetRank(story)
            story.deleteComments()
            story.description = reason ?: null

            story.delete()
            if (!newObject) {
                activityService.addActivity(project, springSecurityService.currentUser, Activity.CODE_DELETE, story.name)
            }
            project.removeFromStories(story)
            project.save(flush: true)
            // Be careful, events may be pushed event if the delete fails because the flush didn't occur yet
            publishSynchronousEvent(IceScrumEventType.DELETE, story, dirtyProperties)
        }
    }

    @PreAuthorize('isAuthenticated() and !archivedProject(#story.backlog)')
    void update(Story story, Map props = [:]) {
        Project project = story.backlog
        if (props.effort != null) {
            if (props.effort != story.effort) {
                if (story.state < Story.STATE_ACCEPTED || story.state == Story.STATE_DONE) {
                    throw new BusinessException(code: 'is.story.error.not.estimated.state')
                }
                if (!securityService.inTeam(project.team, springSecurityService.authentication)) {
                    throw new AccessDeniedException('')
                }
                if (story.state == Story.STATE_ACCEPTED) {
                    story.state = Story.STATE_ESTIMATED
                    story.estimatedDate = new Date()
                    activityService.addActivity(story, springSecurityService.currentUser, 'estimate', story.name)
                }
                story.effort = props.effort
            }
            if (story.parentSprint && story.parentSprint.state == Sprint.STATE_WAIT) {
                story.parentSprint.capacity = story.parentSprint.totalEffort
            }
        } else if (props.containsKey('effort')) {
            if (story.state == Story.STATE_ESTIMATED) {
                if (!securityService.inTeam(project.team, springSecurityService.authentication)) {
                    throw new AccessDeniedException('')
                }
                story.state = Story.STATE_ACCEPTED
                story.effort = null
                story.estimatedDate = null
            } else if (story.state > Story.STATE_ESTIMATED) {
                throw new BusinessException(code: 'is.story.error.not.unestimated.state')
            }
        }
        if (story.type != Story.TYPE_DEFECT) {
            story.affectVersion = null
        }
        if (story.state < Story.STATE_SUGGESTED && story.rank != 0) {
            story.rank = 0
        } else if (props.rank != null && props.rank != story.rank) {
            updateRank(story, props.rank)
        }
        if (story.isDirty('description')) {
            manageActors(story, project)
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, story)
        story.save()
        publishSynchronousEvent(IceScrumEventType.UPDATE, story, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#sprint.parentProject) or scrumMaster(#sprint.parentProject)) and !archivedProject(#sprint.parentProject)')
    public void planMultiple(Sprint sprint, def stories) {
        stories.each {
            plan(sprint, it)
        }
    }

    @PreAuthorize('(productOwner(#sprint.parentProject) or scrumMaster(#sprint.parentProject)) and !archivedProject(#sprint.parentProject)')
    public void plan(Sprint sprint, Story story, Long newRank = null) {
        if (story.dependsOn) {
            if (story.dependsOn.state < Story.STATE_PLANNED) {
                throw new BusinessException(code: 'is.story.error.dependsOn.notPlanned', args: [story.name, story.dependsOn.name])
            } else if (story.dependsOn.parentSprint.startDate > sprint.startDate) {
                throw new BusinessException(code: 'is.story.error.dependsOn.beforePlanned', args: [story.name, story.dependsOn.name])
            }
        }
        if (story.dependences) {
            def startDate = story.dependences.findAll { it.parentSprint }?.collect { it.parentSprint.startDate }?.min()
            if (startDate && sprint.startDate > startDate) {
                throw new BusinessException(code: 'is.story.error.dependences.beforePlanned', args: [story.name])
            }
        }
        if (![Story.TYPE_USER_STORY, Story.TYPE_DEFECT, Story.TYPE_TECHNICAL_STORY].contains(story.type)) {
            throw new BusinessException(code: 'is.story.error.plan.type')
        }
        if (sprint.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.error.associate.done')
        }
        if (story.state < Story.STATE_ESTIMATED) {
            throw new BusinessException(code: 'is.sprint.error.associate.story.noEstimated')
        }
        if (story.state == Story.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.error.associate.story.done')
        }
        if (story.parentSprint != null) {
            unPlan(story, false)
        } else {
            resetRank(story)
        }
        User user = (User) springSecurityService.currentUser
        sprint.addToStories(story)
        if (sprint.state == Sprint.STATE_WAIT) {
            sprint.capacity = sprint.totalEffort
        }
        story.parentSprint = sprint
        activityService.addActivity(story, user, 'plan', story.name, 'parentSprint', null, sprint.id.toString())
        if (sprint.state == Sprint.STATE_INPROGRESS) {
            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            if (!story.plannedDate) {
                story.plannedDate = story.inProgressDate
            }
            if (sprint.parentRelease.parentProject.preferences.autoCreateTaskOnEmptyStory && !story.tasks) {
                def emptyTask = new Task(name: story.name, state: Task.STATE_WAIT, description: story.description, parentStory: story)
                taskService.save(emptyTask, user)
            }
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        } else {
            story.state = Story.STATE_PLANNED
            story.plannedDate = new Date()
        }
        def maxRank = (sprint.stories?.findAll { it.state != Story.STATE_DONE }?.size() ?: 1)
        def rank = (newRank && newRank <= maxRank) ? newRank : maxRank
        setRank(story, rank)
        update(story)
        pushService.disablePushForThisThread()
        story.tasks.findAll { it.state == Task.STATE_WAIT }.each { Task task ->
            task.backlog = sprint
            taskService.update(task, user)
        }
        pushService.enablePushForThisThread()
    }

    @PreAuthorize('(productOwner(#story.backlog) or scrumMaster(#story.backlog)) and !archivedProject(#story.backlog)')
    public void unPlan(Story story, Boolean fullUnPlan = true) {
        def sprint = story.parentSprint
        if (!sprint) {
            throw new BusinessException(code: 'is.story.error.not.planned')
        }
        if (story.state == Story.STATE_DONE) {
            throw new BusinessException(code: 'is.story.error.unplan.done')
        }
        if (fullUnPlan && story.dependences?.find { it.state > Story.STATE_ESTIMATED }) {
            throw new BusinessException(code: 'is.story.error.dependences.dissociate', args: [story.name, story.dependences.find { it.state > Story.STATE_ESTIMATED }.name])
        }
        resetRank(story)
        sprint.removeFromStories(story)
        if (sprint.state == Sprint.STATE_WAIT) {
            sprint.capacity = sprint.totalEffort
        }
        story.parentSprint = null
        story.inProgressDate = null
        story.plannedDate = null
        User user = (User) springSecurityService.currentUser
        if (fullUnPlan) {
            activityService.addActivity(story, user, 'unPlan', story.name, 'parentSprint', sprint.id.toString())
            story.state = Story.STATE_ESTIMATED
            setRank(story, 1)
            update(story)
        }
        pushService.disablePushForThisThread()
        story.tasks.each { Task task ->
            if (task.state != Task.STATE_DONE) {
                def props = task.state == Task.STATE_WAIT ? [:] : [state: Task.STATE_WAIT]
                if (fullUnPlan) {
                    task.backlog = null
                }
                if (props || fullUnPlan) {
                    taskService.update(task, user, false, props)
                }
            }
        }
        pushService.enablePushForThisThread()
    }

    // TODO check rights
    def unPlanAll(Collection<Sprint> sprintList, Integer sprintState = null) {
        sprintList.sort { sprint1, sprint2 -> sprint2.orderNumber <=> sprint1.orderNumber }
        def storiesUnPlanned = []
        sprintList.each { sprint ->
            if ((!sprintState) || (sprintState && sprint.state == sprintState)) {
                def stories = sprint.stories.findAll { story ->
                    story.state != Story.STATE_DONE
                }.sort { st1, st2 -> st2.rank <=> st1.rank }
                stories.each {
                    unPlan(it)
                }
                // Recalculate the sprint estimated velocity (capacite)
                if (sprint.state == Sprint.STATE_WAIT) {
                    sprint.capacity = (Double) sprint.stories?.sum { it.effort } ?: 0
                }
                storiesUnPlanned.addAll(stories)
            }
        }
        return storiesUnPlanned
    }

    // TODO check rights
    def autoPlan(List<Sprint> sprints, Double capacity) {
        def nbPoints = 0
        int nbSprint = 0
        def project = sprints.first().parentProject
        sprints = sprints.findAll { it.state == Sprint.STATE_WAIT }.sort { it.orderNumber }
        int maxSprint = sprints.size()
        // Get the list of stories that have been estimated
        Collection<Story> itemsList = project.stories.findAll { story ->
            story.state == Story.STATE_ESTIMATED && [Story.TYPE_USER_STORY, Story.TYPE_DEFECT, Story.TYPE_TECHNICAL_STORY].contains(story.type)
        }.sort { it.rank }
        Sprint currentSprint = null
        def plannedStories = []
        // Associate story in each sprint
        for (Story story : itemsList) {
            if ((nbPoints + story.effort) > capacity || currentSprint == null) {
                nbPoints = 0
                if (nbSprint < maxSprint) {
                    currentSprint = sprints[nbSprint++]
                    nbPoints += currentSprint.capacity
                    while (nbPoints + story.effort > capacity && currentSprint.capacity > 0) {
                        nbPoints = 0
                        if (nbSprint < maxSprint) {
                            currentSprint = sprints[nbSprint++]
                            nbPoints += currentSprint.capacity
                        } else {
                            nbSprint++
                            break
                        }
                    }
                    if (nbSprint > maxSprint) {
                        break
                    }
                    this.plan(currentSprint, story)
                    plannedStories << story
                    nbPoints += story.effort

                } else {
                    break
                }
            } else {
                this.plan(currentSprint, story)
                plannedStories << story
                nbPoints += story.effort
            }
        }
        return plannedStories
    }

    // TODO check rights
    void setRank(Story story, Long rank) {
        rank = adjustRankAccordingToDependences(story, rank)
        def stories = story.sameBacklogStories
        stories.findAll { it.rank >= rank }.each {
            it.rank++
            it.save()
        }
        story.rank = rank
        cleanRanks(stories)
    }

    // TODO check rights
    private void cleanRanks(stories) {
        stories.sort { it.rank }
        int i = 0
        def error = false
        while (i < stories.size() && !error) {
            error = stories[i].rank != (i + 1)
            i++
        }
        if (error) {
            stories.eachWithIndex { story, ind ->
                if (story.rank != ind + 1) {
                    if (log.debugEnabled) {
                        log.debug("story ${story.uid} as rank ${story.rank} but should have ${ind + 1} fixing!!")
                    }
                    story.rank = ind + 1
                    story.save()
                }
            }
        }
    }

    void resetRank(Story story) {
        story.sameBacklogStories.findAll { it.rank > story.rank }.each {
            it.rank--
            it.save()
        }
    }

    // TODO check rights
    private void updateRank(Story story, Long rank, Boolean force = false) {
        rank = adjustRankAccordingToDependences(story, rank)
        if ((story.dependsOn || story.dependences) && story.rank == rank) {
            return
        }
        if (story.state == Story.STATE_DONE && !force) {
            throw new BusinessException(code: 'is.story.rank.error')
        }
        def stories = story.sameBacklogStories
        if (story.state == Story.STATE_INPROGRESS) {
            def maxRankInProgress = stories.findAll { it.state != Story.STATE_DONE }.size()
            if (rank > maxRankInProgress) {
                rank = maxRankInProgress
            }
        }
        Range affectedRange = story.rank..rank
        int delta = affectedRange.isReverse() ? 1 : -1
        stories.findAll { it != story && it.rank in affectedRange }.each {
            it.rank += delta
            it.save()
        }
        story.rank = rank
        cleanRanks(stories)
    }

    @PreAuthorize('productOwner(#story.backlog) and !archivedProject(#story.backlog)')
    def acceptToBacklog(Story story, Long newRank = null) {
        if (story.state > Story.STATE_SUGGESTED) {
            throw new BusinessException(code: 'is.story.error.not.state.suggested')
        }
        if (story.dependsOn?.state == Story.STATE_SUGGESTED) {
            throw new BusinessException(code: 'is.story.error.dependsOn.suggested', args: [story.name, story.dependsOn.name])
        }
        def rank = newRank ?: ((Story.countByBacklogAndStateInList(story.backlog, [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]) ?: 0) + 1) // Do it before resetRank to prevent flushing dirty ranks that need to be pushed
        resetRank(story)
        story.state = Story.STATE_ACCEPTED
        story.acceptedDate = new Date()
        activityService.addActivity(story, springSecurityService.currentUser, 'acceptAs', story.name)
        if (((Project) story.backlog).preferences.noEstimation) {
            story.estimatedDate = new Date()
            story.effort = 1
            story.state = Story.STATE_ESTIMATED
            activityService.addActivity(story, springSecurityService.currentUser, 'estimate', story.name)
        }
        setRank(story, rank)
        update(story)
    }

    @PreAuthorize('productOwner(#story.backlog) and !archivedProject(#story.backlog)')
    void returnToSandbox(Story story, Long newRank) {
        if (!(story.state in [Story.STATE_ESTIMATED, Story.STATE_ACCEPTED])) {
            throw new BusinessException(code: 'is.story.error.not.in.backlog')
        }
        if (story.dependences?.find { it.state > Story.STATE_SUGGESTED }) {
            throw new BusinessException(code: 'is.story.error.dependences.returntosandbox', args: [story.name, story.dependences.find { it.state > Story.STATE_SUGGESTED }.name])
        }
        resetRank(story)
        story.state = Story.STATE_SUGGESTED
        story.acceptedDate = null
        story.estimatedDate = null
        story.effort = null
        activityService.addActivity(story, springSecurityService.currentUser, 'returnToSandbox', story.name)
        def rank = newRank ?: 1
        setRank(story, rank)
        update(story)
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProject(#stories[0].backlog)')
    def acceptToFeature(List<Story> stories) {
        def features = []
        stories.each { story ->
            if (story.state > Story.STATE_SUGGESTED) {
                throw new BusinessException(code: 'is.story.error.not.state.suggested')
            }
            User user = (User) springSecurityService.currentUser
            def storyProperties = [:] << story.properties
            storyProperties.remove('type')
            storyProperties.remove('activities')
            def feature = new Feature(storyProperties)
            feature.description = (feature.description ?: '')
            feature.validate()
            def i = 1
            while (feature.hasErrors()) {
                if (feature.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    feature.name = feature.name + '_' + i
                    feature.validate()
                } else if (story.errors.getFieldError('name')?.code?.contains("maxSize.exceeded")) {
                    feature.name = feature.name[0..20]
                    feature.validate()
                } else {
                    throw new ValidationException('Validation Error(s) occurred during save()', feature.errors)
                }
            }
            featureService.save(feature, (Project) story.backlog)
            story.attachments.each { attachment ->
                feature.addAttachment(story.creator, attachmentableService.getFile(attachment), attachment.filename)
            }
            feature.tags = story.tags
            delete([story], feature)
            features << feature
            activityService.addActivity(feature, user, 'acceptAs', feature.name)
        }
        return features
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProject(#stories[0].backlog)')
    def acceptToUrgentTask(List<Story> stories) {
        def tasks = []
        def project = stories[0].backlog
        stories.each { story ->
            if (story.state > Story.STATE_SUGGESTED) {
                throw new BusinessException(code: 'is.story.error.not.state.suggested')
            }
            def storyProperties = [:] << story.properties
            storyProperties.remove('activities')
            def task = new Task(storyProperties)
            task.type = Task.TYPE_URGENT
            task.state = Task.STATE_WAIT
            task.description = (story.affectVersion ? i18nService.message(code: 'is.story.affectVersion') + ': ' + story.affectVersion : '') + (task.description ?: '')
            def sprint = (Sprint) Sprint.findCurrentSprint(project.id).list()
            if (!sprint) {
                throw new BusinessException(code: 'is.story.error.not.acceptedAsUrgentTask')
            }
            task.backlog = sprint
            task.validate()
            while (task.hasErrors() && task.errors.getFieldError('name')) {
                if (story.errors.getFieldError('name')?.code?.contains("maxSize.exceeded")) {
                    task.name = task.name[0..20]
                    task.validate()
                } else {
                    throw new ValidationException('Validation Error(s) occurred during save()', task.errors)
                }
            }
            if (story.feature) {
                task.color = story.feature.color
            }
            taskService.save(task, story.creator)
            story.attachments.each { attachment ->
                task.addAttachment(story.creator, attachmentableService.getFile(attachment), attachment.filename)
            }
            story.comments.each { Comment comment ->
                def commentLink = CommentLink.findByComment(comment)
                if (commentLink) {
                    commentLink.commentRef = task.id
                    commentLink.type = GrailsNameUtils.getPropertyName(task.class)
                    commentLink.save()
                }
            }
            task.tags = story.tags
            tasks << task
            delete([story], task)
        }
        return tasks
    }

    // This method is used by taskService when moving the last task as done so the permissions are more relaxed so it works for any team member
    @PreAuthorize('inProject(#story.backlog) and !archivedProject(#story.backlog)')
    void done(Story story) {
        done([story])
    }

    @PreAuthorize('(productOwner(#stories[0].backlog) or scrumMaster(#stories[0].backlog)) and !archivedProject(#stories[0].backlog)')
    void done(List<Story> stories) {
        stories.each { story ->
            if (story.parentSprint.state != Sprint.STATE_INPROGRESS) {
                throw new BusinessException(code: 'is.sprint.error.declareAsDone.state.not.inProgress')
            }
            if (story.state != Story.STATE_INPROGRESS) {
                throw new BusinessException(code: 'is.story.error.declareAsDone.state.not.inProgress')
            }
            //Move story to last rank in sprint
            updateRank(story, Story.countByParentSprint(story.parentSprint))
            story.state = Story.STATE_DONE
            story.doneDate = new Date()
            story.parentSprint.velocity += story.effort
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, story)
            story.save()
            publishSynchronousEvent(IceScrumEventType.UPDATE, story, dirtyProperties)
            User user = (User) springSecurityService.currentUser
            activityService.addActivity(story, user, 'done', story.name)
            pushService.disablePushForThisThread()
            story.tasks?.findAll { it.state != Task.STATE_DONE }?.each { t ->
                taskService.update(t, user, false, [state: Task.STATE_DONE])
            }
            pushService.enablePushForThisThread()
            story.acceptanceTests.each { AcceptanceTest acceptanceTest ->
                if (acceptanceTest.stateEnum != AcceptanceTestState.SUCCESS) {
                    acceptanceTest.stateEnum = AcceptanceTestState.SUCCESS
                    acceptanceTestService.update(acceptanceTest)
                }
            }
        }
        if (stories) {
            clicheService.createOrUpdateDailyTasksCliche(stories[0]?.parentSprint)
        }
    }

    @PreAuthorize('productOwner(#story.backlog) and !archivedProject(#story.backlog)')
    void unDone(Story story) {
        unDone([story])
    }

    @PreAuthorize('(productOwner(#stories[0].backlog) or scrumMaster(#stories[0].backlog)) and !archivedProject(#stories[0].backlog)')
    void unDone(List<Story> stories) {
        stories.each { story ->
            if (story.state != Story.STATE_DONE) {
                throw new BusinessException(code: 'is.story.error.declareAsUnDone.state.not.done')
            }
            if (story.parentSprint.state != Sprint.STATE_INPROGRESS) {
                throw new BusinessException(code: 'is.sprint.error.declareAsUnDone.state.not.inProgress')
            }
            updateRank(story, Story.countByParentSprintAndState(story.parentSprint, Story.STATE_INPROGRESS) + 1, true) // Move story to last rank of in progress stories in sprint
            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            story.doneDate = null
            story.parentSprint.velocity -= story.effort
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, story)
            story.save()
            publishSynchronousEvent(IceScrumEventType.UPDATE, story, dirtyProperties)
            User user = (User) springSecurityService.currentUser
            activityService.addActivity(story, user, 'unDone', story.name)
        }
        if (stories) {
            clicheService.createOrUpdateDailyTasksCliche(stories[0]?.parentSprint)
        }
    }

    @PreAuthorize('inProject(#stories[0].backlog) and !archivedProject(#stories[0].backlog) and inProject(#project) and !archivedProject(#project)')
    def copy(List<Story> stories, Project project) {
        def copiedStories = []
        def sameProject = stories.first().backlog.id == project.id
        stories.each { story ->
            def copiedStory = new Story(
                    name: story.name,
                    description: story.description,
                    notes: story.notes,
                    dateCreated: new Date(),
                    type: story.type,
                    backlog: project,
                    affectVersion: story.affectVersion,
                    value: story.value
            )
            if (sameProject) {
                copiedStory.state = Story.STATE_SUGGESTED
                copiedStory.origin = story.name
                copiedStory.name += '_1'
                copiedStory.feature = story.feature
            } else {
                copiedStory.state = story.state <= Story.STATE_ESTIMATED ? story.state : Story.STATE_ESTIMATED
                copiedStory.origin = story.backlog.name
                copiedStory.suggestedDate = story.state >= Story.STATE_SUGGESTED ? story.suggestedDate : null
                copiedStory.acceptedDate = story.state >= Story.STATE_ACCEPTED ? story.acceptedDate : null
                copiedStory.estimatedDate = story.effort ? story.estimatedDate : null
                copiedStory.effort = story.effort
                if (story.feature) {
                    copiedStory.feature = Feature.findByBacklogAndNameIlike(project, story.feature.name)
                }
                if (story.actors && copiedStory.description) {
                    // Fetch old project's actors in text rather than domain because it can be different and it is least astonishing
                    Matcher actorNameMatcher = copiedStory.description =~ /A\[\d+-([^]]*)]/
                    while (actorNameMatcher.find()) {
                        String actorName = actorNameMatcher.group(1)
                        Actor newActor = Actor.findByNameIlikeAndParentProject(actorName, project)
                        if (newActor) {
                            // Update id for existing actors in description
                            copiedStory.description = copiedStory.description.replace(actorNameMatcher.group(0), "A[${newActor.uid}-${newActor.name}]")
                        } else {
                            // Remove id and tag for non existing actors
                            copiedStory.description = copiedStory.description.replace(actorNameMatcher.group(0), actorName)
                        }
                    }
                }
            }
            copiedStory.validate()
            def i = 1
            while (copiedStory.hasErrors()) {
                if (copiedStory.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    copiedStory.name = story.name + '_' + i
                    copiedStory.validate()
                } else if (copiedStory.errors.getFieldError('name')?.code?.contains("maxSize.exceeded")) {
                    copiedStory.name = copiedStory.name[0..20]
                    copiedStory.validate()
                } else {
                    throw new ValidationException('Validation Error(s) occurred during save()', copiedStory.errors)
                }
            }
            save(copiedStory, project, (User) springSecurityService.currentUser)
            story.attachments?.each { Attachment a ->
                if (!a.url) {
                    def currentFile = attachmentableService.getFile(a)
                    def newFile = File.createTempFile(a.name, a.ext)
                    FileUtils.copyFile(currentFile, newFile)
                    copiedStory.addAttachment(a.poster, newFile, a.name + (a.ext ? '.' + a.ext : ''))
                } else {
                    copiedStory.addAttachment(a.poster, [url: a.url, provider: a.provider, length: a.length], a.name + (a.ext ? '.' + a.ext : ''))
                }
            }
            story.comments?.each { Comment c ->
                copiedStory.addComment(c.poster, c.body)
            }
            copiedStory.tags = story.tags
            story.acceptanceTests?.each { acceptanceTest ->
                acceptanceTestService.save(new AcceptanceTest(name: acceptanceTest.name, description: acceptanceTest.description), copiedStory, (User) springSecurityService.currentUser)
            }
            story.tasks?.each { task ->
                taskService.save(new Task(
                        name: task.name,
                        description: task.description,
                        notes: task.notes,
                        estimation: task.estimation ?: null,
                        color: task.color,
                        parentStory: copiedStory
                ), (User) springSecurityService.currentUser)
            }
            copiedStories << copiedStory.refresh()
        }
        return copiedStories
    }

    def unMarshall(def storyXml, def options) {
        Project project = options.project
        Sprint sprint = options.sprint
        Story.withTransaction(readOnly: !options.save) { transaction ->
            User creator = project ? project.getUserByUidOrOwner(storyXml.creator.@uid.text()) : null
            def story = new Story(
                    type: storyXml.type.text().toInteger(),
                    suggestedDate: DateUtils.parseDateFromExport(storyXml.suggestedDate.text()),
                    acceptedDate: DateUtils.parseDateFromExport(storyXml.acceptedDate.text()),
                    estimatedDate: DateUtils.parseDateFromExport(storyXml.estimatedDate.text()),
                    plannedDate: DateUtils.parseDateFromExport(storyXml.plannedDate.text()),
                    inProgressDate: DateUtils.parseDateFromExport(storyXml.inProgressDate.text()),
                    doneDate: DateUtils.parseDateFromExport(storyXml.doneDate.text()),
                    origin: storyXml.origin.text() ?: null,
                    effort: storyXml.effort.text().isEmpty() ? null : storyXml.effort.text().toBigDecimal(),
                    rank: storyXml.rank.text().toInteger(),
                    state: storyXml.state.text().toInteger(),
                    value: storyXml.value.text().isEmpty() ? 0 : storyXml.value.text().toInteger(),
                    affectVersion: storyXml.affectVersion.text(),
                    //backlogElement
                    name: storyXml."${'name'}".text(),
                    description: storyXml.description.text() ?: null,
                    notes: storyXml.notes.text() ?: null,
                    todoDate: DateUtils.parseDateFromExport(storyXml.todoDate.text()),
                    uid: storyXml.@uid.text().toInteger(),
            )
            // References on other objects
            if (project) {
                if (!storyXml.feature.@uid.isEmpty()) {
                    Feature feature = project.features.find {
                        it.uid == storyXml.feature.@uid.text().toInteger()
                    }
                    if (feature) {
                        feature.addToStories(story)
                    }
                }
                Closure importActor = { actorXml ->
                    if (!actorXml.@uid.isEmpty()) {
                        Actor actor = project.actors.find { it.uid == actorXml.@uid.text().toInteger() }
                        if (actor) {
                            actor.addToStories(story)
                        }
                    }
                }
                storyXml?.actors?.actor?.each(importActor)
                importActor(storyXml.actor) // Handle legacy exports with one actor per story
                story.creator = creator
                project.addToStories(story)
            }
            if (sprint) {
                sprint.addToStories(story)
            }
            // Save before some hibernate stuff
            if (options.save) {
                //handle hsqldb unicity with name
                story.validate()
                def i = 1
                while (story.hasErrors()) {
                    if (story.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                        i += 1
                        story.name = story.name + '_' + i
                        story.validate()
                    } else if (story.errors.getFieldError('name')?.code?.contains("maxSize.exceeded")) {
                        story.name = story.name[0..20]
                        story.validate()
                    } else {
                        throw new ValidationException('Validation Error(s) occurred during save()', story.errors)
                    }
                }
                story.save()
                //Handle dependsOn
                if (project && !storyXml.dependsOn.@uid.isEmpty()) {
                    Story dependsOn = Story.findByBacklogAndUid(project, storyXml.dependsOn.@uid.text().toInteger())
                    if (dependsOn) {
                        story.dependsOn = dependsOn
                        story.save()
                        dependsOn.save()
                    }
                }
                //Handle tags only available when story has an id options.save)
                if (storyXml.tags.text()) {
                    story.tags = storyXml.tags.text().replaceAll(' ', '').replace('[', '').replace(']', '').split(',')
                }
                if (project) {
                    storyXml.comments.comment.each { _commentXml ->
                        def uid = options.userUIDByImportedID?."${_commentXml.posterId.text().toInteger()}" ?: null
                        User user = project.getUserByUidOrOwner(uid)
                        ApplicationSupport.importComment(story, user, _commentXml.body.text(), DateUtils.parseDateFromExport(_commentXml.dateCreated.text()))
                    }
                    story.comments_count = storyXml.comments.comment.size() ?: 0
                    storyXml.attachments.attachment.each { _attachmentXml ->
                        def uid = options.userUIDByImportedID?."${_attachmentXml.posterId.text().toInteger()}" ?: null
                        User user = project.getUserByUidOrOwner(uid)
                        ApplicationSupport.importAttachment(story, user, options.path, _attachmentXml)
                    }
                    story.attachments_count = storyXml.attachments.attachment.size() ?: 0
                    storyXml.followers.user.each { _userXml ->
                        User user = User.findByUid(_userXml.@uid.text())
                        if (user) {
                            story.addToFollowers(user)
                        }
                    }
                }
            }
            options.story = story
            // Child objects
            storyXml.tasks.task.each {
                taskService.unMarshall(it, options)
            }
            storyXml.acceptanceTests.acceptanceTest.each {
                acceptanceTestService.unMarshall(it, options)
            }
            options.parent = story
            storyXml.activities.activity.each { def activityXml ->
                activityService.unMarshall(activityXml, options)
            }
            options.parent = null
            if (options.save) {
                story.save()
            }
            options.story = null
            return (Story) importDomainsPlugins(storyXml, story, options)
        }
    }

    void manageActors(story, project) {
        Set<Actor> actorSet = new HashSet<>()
        if (story.description) {
            Matcher actorIdMatcher = story.description =~ /A\[(\d+)-.+?]/
            while (actorIdMatcher.find()) {
                Integer actorUid = actorIdMatcher.group(1).toInteger()
                Actor actor = Actor.findByUidAndParentProject(actorUid, project)
                if (actor) {
                    actorSet.add(actor)
                } else {
                    throw new BusinessException(code: "Error, no actor with UID $actorUid can be found on this project. Please change your story description.") // TODO i18n
                }
            }
        }
        story.actors = actorSet
    }

    private Long adjustRankAccordingToDependences(story, Long rank) {
        def sameBacklogStories = story.sameBacklogStories
        if (story.dependsOn && (story.dependsOn in sameBacklogStories) && rank <= story.dependsOn.rank) {
            rank = story.dependsOn.rank + 1
        }
        if (story.dependences) {
            def highestPriorityRank = story.dependences.intersect(sameBacklogStories)*.rank.min()
            if (highestPriorityRank && rank >= highestPriorityRank) {
                rank = highestPriorityRank - 1
            }
        }
        return rank > 0 ? rank : 1
    }
}
