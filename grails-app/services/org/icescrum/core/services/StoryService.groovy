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
    def commentService
    def grailsApplication

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
        def sameBacklogStories = story.sameBacklogStories
        def rank = sameBacklogStories ? sameBacklogStories.max { it.rank }.rank + 1 : 1
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
        stories.sort { -it.rank }.each { story ->
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, story)
            dirtyProperties.project = dirtyProperties.backlog
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
            story.description = reason ?: null
            story.delete()
            if (!newObject) {
                activityService.addActivity(project, springSecurityService.currentUser, Activity.CODE_DELETE, story.name)
            }
            project.removeFromStories(story)
            project.save(flush: true)
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
    void plan(Story story, Sprint sprint, Long newRank = null) {
        plan([story], sprint, newRank)
    }

    @PreAuthorize('(productOwner(#sprint.parentProject) or scrumMaster(#sprint.parentProject)) and !archivedProject(#sprint.parentProject)')
    void plan(List<Story> stories, Sprint sprint, Long newRank = null) {
        if (!stories) {
            return
        }
        if (sprint.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.error.associate.done')
        }
        def nbStories = Story.countByParentSprintAndStateLessThan(sprint, Story.STATE_DONE)
        def maxRank = nbStories ? nbStories + 1 : 1
        if (!newRank || newRank > maxRank) {
            newRank = maxRank
        }
        stories.sort { it.rank }.eachWithIndex { Story story, index ->
            if (story.dependsOn && (story.dependsOn.state < Story.STATE_PLANNED || story.dependsOn.parentSprint.startDate > sprint.startDate)) {
                throw new BusinessException(code: 'is.story.error.dependsOn', args: [story.name, story.dependsOn.name])
            }
            if (story.dependences) {
                Story dependence = story.dependences.findAll { it.parentSprint }?.min { it.parentSprint.startDate }
                if (dependence && sprint.startDate > dependence.parentSprint.startDate) {
                    throw new BusinessException(code: 'is.story.error.dependences', args: [story.name, dependence.name])
                }
            }
            if (![Story.TYPE_USER_STORY, Story.TYPE_DEFECT, Story.TYPE_TECHNICAL_STORY].contains(story.type)) {
                throw new BusinessException(code: 'is.story.error.plan.type')
            }
            if (story.state < Story.STATE_ESTIMATED) {
                throw new BusinessException(code: 'is.sprint.error.associate.story.noEstimated', args: [sprint.parentProject.getStoryStateNames()[Story.STATE_ESTIMATED]])
            }
            if (story.state == Story.STATE_DONE) {
                throw new BusinessException(code: 'is.sprint.error.associate.story.done', args: [sprint.parentProject.getStoryStateNames()[Story.STATE_DONE]])
            }
            if (story.parentSprint != null) {
                unPlan(story, false)
            } else {
                resetRank(story)
            }
            User user = (User) springSecurityService.currentUser
            sprint.addToStories(story)
            story.parentSprint = sprint
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
            } else {
                story.state = Story.STATE_PLANNED
                story.plannedDate = new Date()
            }
            setRank(story, newRank + index)
            update(story)
            pushService.disablePushForThisThread()
            story.tasks.findAll { it.state == Task.STATE_WAIT }.each { Task task ->
                task.backlog = sprint
                taskService.update(task, user)
            }
            pushService.enablePushForThisThread()
        }
        if (sprint.state == Sprint.STATE_WAIT) {
            sprint.capacity = sprint.totalEffort
            SprintService sprintService = (SprintService) grailsApplication.mainContext.getBean("sprintService")
            sprintService.update(sprint, null, null, false, false)
        } else if (sprint.state == Sprint.STATE_INPROGRESS) {
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        }
    }

    @PreAuthorize('(productOwner(#story.backlog) or scrumMaster(#story.backlog)) and !archivedProject(#story.backlog)')
    void unPlan(Story story, Boolean fullUnPlan = true) {
        unPlan([story], fullUnPlan)
    }

    @PreAuthorize('(productOwner(#stories[0].backlog) or scrumMaster(#stories[0].backlog)) and !archivedProject(#stories[0].backlog)')
    void unPlan(List<Story> stories, Boolean fullUnPlan = true) {
        if (!stories) {
            return
        }
        Sprint parentSprint = stories[0].parentSprint
        if (!parentSprint) {
            throw new BusinessException(code: 'is.story.error.not.planned')
        }
        stories.sort { -it.rank }.each { Story story ->
            if (parentSprint.id != story.parentSprint.id) {
                throw new BusinessException(text: 'Error, only stories belonging to the same sprint can be unplanned together')
            }
            if (story.state == Story.STATE_DONE) {
                throw new BusinessException(code: 'is.story.error.unplan.done')
            }
            if (fullUnPlan && story.dependences?.find { it.state > Story.STATE_ESTIMATED }) {
                throw new BusinessException(code: 'is.story.error.dependences', args: [story.name, story.dependences.find { it.state > Story.STATE_ESTIMATED }.name])
            }
            resetRank(story)
            parentSprint.removeFromStories(story)
            story.parentSprint = null
            story.inProgressDate = null
            story.plannedDate = null
            if (fullUnPlan) {
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
                        taskService.update(task, (User) springSecurityService.currentUser, false, props)
                    }
                }
            }
            pushService.enablePushForThisThread()
        }
        if (parentSprint.state == Sprint.STATE_WAIT) {
            parentSprint.capacity = parentSprint.totalEffort
            SprintService sprintService = (SprintService) grailsApplication.mainContext.getBean("sprintService")
            sprintService.update(parentSprint, null, null, false, false)
        }
    }

    def unPlanAll(Collection<Sprint> sprints, Integer sprintState = null) {
        def unPlannedStories = []
        sprints.sort { -it.orderNumber }.each { sprint ->
            if (!sprintState || sprint.state == sprintState) {
                def stories = sprint.stories.findAll { story ->
                    story.state != Story.STATE_DONE
                }.asList()
                if (stories) {
                    unPlan(stories)
                    unPlannedStories.addAll(stories)
                }
            }
        }
        return unPlannedStories
    }

    def autoPlan(List<Sprint> sprints, Double plannedVelocity) {
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
            if ((nbPoints + story.effort) > plannedVelocity || currentSprint == null) {
                nbPoints = 0
                if (nbSprint < maxSprint) {
                    currentSprint = sprints[nbSprint++]
                    nbPoints += currentSprint.capacity
                    while (nbPoints + story.effort > plannedVelocity && currentSprint.capacity > 0) {
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
                    plan(story, currentSprint)
                    plannedStories << story
                    nbPoints += story.effort

                } else {
                    break
                }
            } else {
                plan(story, currentSprint)
                plannedStories << story
                nbPoints += story.effort
            }
        }
        return plannedStories
    }

    private void updateStoryRank(Story story, Integer newRank) {
        def dirtyProperties = [rank: story.rank]
        story.rank = newRank
        story.save()
        publishSynchronousEvent(IceScrumEventType.PARTIAL_UPDATE, story, dirtyProperties)
    }

    void setRank(Story story, Long rank) {
        rank = adjustRankAccordingToDependences(story, rank)
        def stories = story.sameBacklogStories
        stories.each { _story ->
            if (_story.rank >= rank) {
                updateStoryRank(_story, _story.rank + 1)
            }
        }
        story.rank = rank
        cleanRanks(stories)
    }

    private void cleanRanks(stories) {
        stories.sort { it.rank }
        int i = 0
        def error = false
        while (i < stories.size() && !error) {
            error = stories[i].rank != (i + 1)
            i++
        }
        if (error) {
            stories.eachWithIndex { _story, index ->
                def expectedRank = index + 1
                if (_story.rank != expectedRank) {
                    if (log.debugEnabled) {
                        log.debug("story ${_story.uid} as rank ${_story.rank} but should have ${expectedRank} fixing!!")
                    }
                    updateStoryRank(_story, expectedRank)
                }
            }
        }
    }

    void resetRank(Story story) {
        def sameBacklogStories = story.sameBacklogStories
        sameBacklogStories.each { _story ->
            if (_story.rank > story.rank) {
                updateStoryRank(_story, _story.rank - 1)
            }
        }
    }

    private void updateRank(Story story, Long rank, Integer newState = null) {
        rank = adjustRankAccordingToDependences(story, rank)
        if ((story.dependsOn || story.dependences) && story.rank == rank) {
            return
        }
        if (story.state == Story.STATE_DONE && newState != Story.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.story.error.done', args: [((Project) story.backlog).getStoryStateNames()[Story.STATE_DONE]])
        }
        def stories = story.sameBacklogStories
        if (story.state in [Story.STATE_INPROGRESS, Story.STATE_INREVIEW] && newState != Story.STATE_DONE) {
            def maxRankInProgress = stories.findAll { it.state != Story.STATE_DONE }.size()
            if (rank > maxRankInProgress) {
                rank = maxRankInProgress
            }
        }
        Range affectedRange = story.rank..rank
        int delta = affectedRange.isReverse() ? 1 : -1
        stories.each { _story ->
            if (_story.id != story.id && _story.rank in affectedRange) {
                updateStoryRank(_story, _story.rank + delta)
            }
        }
        story.rank = rank
        cleanRanks(stories)
    }

    void shiftRankInList(Story story, List<Story> stories, Integer newIndex) {
        stories = stories.sort { it.rank }
        List<Integer> ranks = stories*.rank
        def oldIndex = stories.indexOf(story)
        stories.remove(oldIndex)
        stories.add(newIndex, story)
        (oldIndex..newIndex).each { index ->
            def newRank = ranks[index]
            def _story = stories[index]
            if (newRank != adjustRankAccordingToDependences(_story, newRank)) {
                throw new BusinessException(code: 'is.story.error.shift.rank.has.dependences', args: [_story.name])
            }
            updateStoryRank(_story, newRank) // NO REAL UPDATE EVENT FOR THE CURRENT STORY...
        }
    }

    @PreAuthorize('productOwner(#story.backlog) and !archivedProject(#story.backlog)')
    def acceptToBacklog(Story story, Long newRank = null) {
        acceptToBacklog([story], newRank)
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProject(#stories[0].backlog)')
    def acceptToBacklog(List<Story> stories, Long newRank = null) {
        Project project = (Project) stories[0].backlog
        if (!newRank) {
            newRank = Story.countByBacklogAndStateInList(project, [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]) + 1
        }
        stories.sort { it.rank }.eachWithIndex { Story story, index ->
            if (story.state > Story.STATE_SUGGESTED) {
                throw new BusinessException(code: 'is.story.error.not.state.suggested', args: [project.getStoryStateNames()[Story.STATE_SUGGESTED]])
            }
            if (story.dependsOn?.state == Story.STATE_SUGGESTED) {
                throw new BusinessException(code: 'is.story.error.dependsOn', args: [story.name, story.dependsOn.name])
            }
            resetRank(story)
            story.state = Story.STATE_ACCEPTED
            story.acceptedDate = new Date()
            if (project.preferences.noEstimation) {
                story.estimatedDate = new Date()
                story.effort = 1
                story.state = Story.STATE_ESTIMATED
            }
            setRank(story, newRank + index)
            update(story)
        }
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProject(#stories[0].backlog)')
    void returnToSandbox(List<Story> stories, Long newRank = null) {
        Project project = (Project) stories[0].backlog
        if (!newRank) {
            newRank = 1
        }
        stories.sort { it.rank }.eachWithIndex { Story story, index ->
            if (!(story.state in [Story.STATE_ESTIMATED, Story.STATE_ACCEPTED])) {
                def storyStatesByName = project.getStoryStateNames()
                throw new BusinessException(code: 'is.story.error.not.in.backlog', args: [storyStatesByName[Story.STATE_ACCEPTED], storyStatesByName[Story.STATE_ESTIMATED]])
            }
            if (story.dependences?.find { it.state > Story.STATE_SUGGESTED }) {
                throw new BusinessException(code: 'is.story.error.dependences', args: [story.name, story.dependences.find { it.state > Story.STATE_SUGGESTED }.name])
            }
            resetRank(story)
            story.state = Story.STATE_SUGGESTED
            story.acceptedDate = null
            story.estimatedDate = null
            story.effort = null
            setRank(story, newRank + index)
            update(story)
        }
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProject(#stories[0].backlog)')
    def turnIntoFeature(List<Story> stories) {
        def features = []
        Project project = (Project) stories[0].backlog
        stories.sort { it.rank }.each { story ->
            if (story.state > Story.STATE_SUGGESTED) {
                throw new BusinessException(code: 'is.story.error.not.state.suggested', args: [project.getStoryStateNames()[Story.STATE_SUGGESTED]])
            }
            User user = (User) springSecurityService.currentUser
            def storyProperties = [:] << story.properties
            storyProperties.remove('type')
            storyProperties.remove('activities')
            storyProperties.remove('metaDatas')
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
            featureService.save(feature, project)
            story.attachments.each { attachment ->
                feature.addAttachment(story.creator, attachmentableService.getFile(attachment), attachment.filename)
            }
            story.comments.each { Comment comment ->
                def commentLink = CommentLink.findByComment(comment)
                if (commentLink) {
                    commentLink.commentRef = feature.id
                    commentLink.type = GrailsNameUtils.getPropertyName(feature.class)
                    commentLink.save()
                }
            }
            feature.tags = story.tags
            delete([story], feature)
            features << feature
            activityService.addActivity(feature, user, 'acceptAs', feature.name)
        }
        return features
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProject(#stories[0].backlog)')
    def turnIntoUrgentTask(List<Story> stories) {
        def tasks = []
        Project project = (Project) stories[0].backlog
        stories.sort { it.rank }.each { story ->
            if (story.state > Story.STATE_SUGGESTED) {
                throw new BusinessException(code: 'is.story.error.not.state.suggested', args: [project.getStoryStateNames()[Story.STATE_SUGGESTED]])
            }
            def storyProperties = [:] << story.properties
            storyProperties.remove('activities')
            storyProperties.remove('metaDatas')
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
        if (!stories) {
            return
        }
        Project project = (Project) stories[0].backlog
        def storyStateNames = project.getStoryStateNames()
        def parentSprint = stories[0].parentSprint
        if (parentSprint?.state != Sprint.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.story.error.markAsDone.not.inProgress', args: [storyStateNames[Story.STATE_DONE]])
        }
        User user = (User) springSecurityService.currentUser
        def newRank = Story.countByParentSprint(parentSprint)
        stories.sort { it.rank }.eachWithIndex { story, index ->
            if (story.state < Story.STATE_INPROGRESS || story.state >= Story.STATE_DONE) {
                throw new BusinessException(code: 'is.story.error.workflow', args: [storyStateNames[Story.STATE_DONE], storyStateNames[story.state]])
            }
            updateRank(story, newRank + index, Story.STATE_DONE)
            story.state = Story.STATE_DONE
            story.doneDate = new Date()
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, story)
            story.save()
            publishSynchronousEvent(IceScrumEventType.UPDATE, story, dirtyProperties)
            pushService.disablePushForThisThread()
            story.tasks?.findAll { it.state != Task.STATE_DONE }?.each { Task task ->
                taskService.update(task, user, false, [state: Task.STATE_DONE])
            }
            pushService.enablePushForThisThread()
            Feature feature = story.feature
            if (feature && feature.state != Feature.STATE_DONE && project.preferences.autoDoneFeature && Story.countByFeatureAndStateNotEqual(feature, Story.STATE_DONE) == 0) {
                featureService.update(feature, [state: Feature.STATE_DONE])
            }
            story.acceptanceTests.each { AcceptanceTest acceptanceTest ->
                if (acceptanceTest.stateEnum != AcceptanceTestState.SUCCESS) {
                    acceptanceTest.stateEnum = AcceptanceTestState.SUCCESS
                    acceptanceTestService.update(acceptanceTest)
                }
            }
        }
        parentSprint.velocity += stories.sum { it.effort }
        SprintService sprintService = (SprintService) grailsApplication.mainContext.getBean("sprintService")
        sprintService.update(parentSprint, null, null, false, false)
        clicheService.createOrUpdateDailyTasksCliche(parentSprint)
    }

    @PreAuthorize('(productOwner(#stories[0].backlog) or scrumMaster(#stories[0].backlog)) and !archivedProject(#stories[0].backlog)')
    void unDone(List<Story> stories) {
        if (!stories) {
            return
        }
        def storyStateNames = ((Project) stories[0].backlog).getStoryStateNames()
        def parentSprint = stories[0].parentSprint
        def newRank = Story.countByParentSprintAndState(parentSprint, Story.STATE_INPROGRESS) + 1
        if (parentSprint.state != Sprint.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.sprint.error.declareAsUnDone.state.not.inProgress')
        }
        stories.sort { it.rank }.eachWithIndex { story, index ->
            if (story.state != Story.STATE_DONE) {
                throw new BusinessException(code: 'is.story.error.workflow', args: [storyStateNames[Story.STATE_INPROGRESS], storyStateNames[story.state]])
            }
            updateRank(story, newRank + index, Story.STATE_INPROGRESS) // Move story to last rank of in progress stories in sprint
            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            story.doneDate = null
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, story)
            story.save()
            publishSynchronousEvent(IceScrumEventType.UPDATE, story, dirtyProperties)
        }
        parentSprint.velocity -= stories.sum { it.effort }
        SprintService sprintService = (SprintService) grailsApplication.mainContext.getBean("sprintService")
        sprintService.update(parentSprint, null, null, false, false)
        clicheService.createOrUpdateDailyTasksCliche(parentSprint)
    }

    @PreAuthorize('inProject(#stories[0].backlog) and !archivedProject(#stories[0].backlog) and inProject(#project) and !archivedProject(#project)')
    def copy(List<Story> stories, Project project) {
        def copiedStories = []
        def sameProject = stories.first().backlog.id == project.id
        stories.sort { it.rank }.each { story ->
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
                    frozenDate: DateUtils.parseDateFromExport(storyXml.frozenDate.text()),
                    suggestedDate: DateUtils.parseDateFromExport(storyXml.suggestedDate.text()),
                    acceptedDate: DateUtils.parseDateFromExport(storyXml.acceptedDate.text()),
                    estimatedDate: DateUtils.parseDateFromExport(storyXml.estimatedDate.text()),
                    plannedDate: DateUtils.parseDateFromExport(storyXml.plannedDate.text()),
                    inProgressDate: DateUtils.parseDateFromExport(storyXml.inProgressDate.text()),
                    inReviewDate: DateUtils.parseDateFromExport(storyXml.inReviewDate.text()),
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
                        commentService.importComment(story, user, _commentXml.body.text(), DateUtils.parseDateFromExport(_commentXml.dateCreated.text()))
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

    private static Long adjustRankAccordingToDependences(story, Long rank) {
        def sameBacklogStories = story.sameBacklogStories
        if (story.dependsOn && (story.dependsOn in sameBacklogStories) && rank <= story.dependsOn.rank) {
            rank = story.dependsOn.rank + 1
        }
        if (story.dependences) {
            def highestPriorityRank = story.dependences.findAll { it.backlog.id == story.backlog.id }.intersect(sameBacklogStories)*.rank.min()
            if (highestPriorityRank && rank >= highestPriorityRank) {
                rank = highestPriorityRank - 1
            }
        }
        return rank > 0 ? rank : 1
    }
}
