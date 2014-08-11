/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 */

package org.icescrum.core.services

import grails.plugin.fluxiable.Activity
import grails.util.GrailsNameUtils
import org.apache.commons.io.FileUtils
import org.grails.comments.Comment
import org.grails.comments.CommentLink
import org.hibernate.Hibernate
import org.hibernate.exception.SQLGrammarException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.plugins.attachmentable.domain.Attachment

import java.text.SimpleDateFormat
import org.icescrum.core.event.IceScrumStoryEvent
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState

import org.icescrum.core.support.ApplicationSupport

@Transactional
class StoryService extends IceScrumEventPublisher {
    def taskService
    def springSecurityService
    def clicheService
    def featureService
    def attachmentableService
    def securityService
    def acceptanceTestService
    def sessionFactory
    def messageSource

    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('!archivedProduct(#product)')
    void save(Story story, Product product, User user) {

        if (!story.effort) {
            story.effort = null
        }

        story.backlog = product
        story.creator = user

        manageActors(story, product)

        story.uid = Story.findNextUId(product.id)
        if(!story.suggestedDate) {
            story.suggestedDate = new Date()
        }

        if (story.effort > 0) {
            story.state = Story.STATE_ESTIMATED
            if(!story.estimatedDate)
                story.estimatedDate = new Date()
        } else if (story.acceptedDate) {
            story.state = Story.STATE_ACCEPTED
        } else {
            story.state = Story.STATE_SUGGESTED
        }

        story.affectVersion = (story.type == Story.TYPE_DEFECT ? story.affectVersion : null)
        story.addToFollowers(user)
        product.allUsers.findAll {
            user.id != it.id && product.pkey in it.preferences.emailsSettings.autoFollow
        }.each {
            story.addToFollowers(user)
        }
        if (story.save()) {
            product.addToStories(story)
            publishSynchronousEvent(IceScrumEventType.CREATE, story)
        } else {
            throw new RuntimeException(story.errors?.toString())
        }
    }

    // TODO replace stories by a single one and call the service in a loop in story controller
    @PreAuthorize('!archivedProduct(#stories[0].backlog)')
    void delete(Collection<Story> stories, newObject = null, reason = null) {
        def product = stories[0].backlog
        stories.each { story ->
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, story)
            // Custom properties
            if (newObject) {
                dirtyProperties.newObject = newObject
            }

            if (story.state >= Story.STATE_PLANNED) {
                throw new IllegalStateException('is.story.error.not.deleted.state')
            }
            if (!springSecurityService.isLoggedIn()){
                throw new IllegalAccessException()
            }
            if (!(story.creator.id == springSecurityService.currentUser?.id) && !securityService.productOwner(product.id, springSecurityService.authentication)) {
                throw new IllegalAccessException()
            }
            if (story.actor){
                story.actor.removeFromStories(story)
            }
            if (story.feature){
                story.feature.removeFromStories(story)
            }
            def dependences = story.dependences
            if (dependences){
                dependences.each{
                    notDependsOn(it)
                }
            }
            if (story.dependsOn) {
                notDependsOn(story)
            }
            story.removeAllAttachments()
            if (story.state != Story.STATE_SUGGESTED) {
                resetRank(story)
            }
            story.deleteComments()
            story.description = reason ?: null

            story.delete()
            if (!newObject) {
                product.addActivity(springSecurityService.currentUser, Activity.CODE_DELETE, story.name)
            }
            product.removeFromStories(story)
            product.save()
            // Be careful, events may be pushed event if the delete fails because the flush didn't occur yet
            publishSynchronousEvent(IceScrumEventType.DELETE, story, dirtyProperties)
        }
    }

    @PreAuthorize('isAuthenticated() and !archivedProduct(#story.backlog)')
    void update(Story story, Map props = [:]) {

        if (props.state != null && props.state != story.state) {
            if (props.state == Story.STATE_ACCEPTED) {
                acceptToBacklog([story])
            } else if (props.state == Story.STATE_SUGGESTED) {
                returnToSandbox([story])
            } else if (props.state == Story.STATE_INPROGRESS) {
                story.state = Story.STATE_INPROGRESS
                story.inProgressDate = new Date()
            }
        }
        if (props.effort != null) {
            if (props.effort != story.effort) {
                // TODO check TM or SM
                if (story.state < Story.STATE_ACCEPTED || story.state == Story.STATE_DONE) {
                    throw new IllegalStateException() // TODO validation
                }
                if (story.state == Story.STATE_ACCEPTED) {
                    story.state = Story.STATE_ESTIMATED
                }
                story.effort = props.effort
                story.estimatedDate = new Date()
            }
            if (story.parentSprint && story.parentSprint.state == Sprint.STATE_WAIT) {
                story.parentSprint.capacity = story.parentSprint.totalEffort
            }
        } else if (props.containsKey('effort')) {
            if (story.state == Story.STATE_ESTIMATED) {
                story.state = Story.STATE_ACCEPTED
                story.effort = null
                story.estimatedDate = null
            } else {
                throw new IllegalStateException() // TODO validation
            }
        }
        if (props.parentSprint != null) {
            plan(props.parentSprint, story)
        } else if (props.containsKey('parentSprint') && story.parentSprint) {
            unPlan(story)
        }

        if (story.type != Story.TYPE_DEFECT) {
            story.affectVersion = null
        }

        if (story.state <= Story.STATE_SUGGESTED && story.rank != 0) {
            story.rank = 0
        } else if (props.rank != null && props.rank != story.rank) {
            rank(story, props.rank)
        }

        def product = story.backlog

        if (story.isDirty('description')) {
            manageActors(story, product)
        }

        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, story)
        if (!story.save()) {
            throw new RuntimeException(story.errors?.toString())
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, story, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#sprint.parentProduct) or scrumMaster(#sprint.parentProduct)) and !archivedProduct(#sprint.parentProduct)')
    public void plan(Sprint sprint, Collection<Story> stories) {
        stories.each {
            this.plan(sprint, it)
        }
    }

    @PreAuthorize('(productOwner(#sprint.parentProduct) or scrumMaster(#sprint.parentProduct)) and !archivedProduct(#sprint.parentProduct)')
    public void plan(Sprint sprint, Story story) {
        if (story.dependsOn){
            if (story.dependsOn.state < Story.STATE_PLANNED){
                throw new IllegalStateException(g.message(code:'is.story.error.dependsOn.notPlanned',args: [story.name, story.dependsOn.name]).toString())
            }else if(story.dependsOn.parentSprint.startDate > sprint.startDate){
                throw new IllegalStateException(g.message(code:'is.story.error.dependsOn.beforePlanned',args: [story.name, story.dependsOn.name]).toString())
            }
        }
        if (story.dependences){
            def startDate = story.dependences.findAll{ it.parentSprint }?.collect{ it.parentSprint.startDate }?.min()
            if (startDate && sprint.startDate > startDate){
                throw new IllegalStateException(g.message(code:'is.story.error.dependences.beforePlanned', args: [story.name]).toString())
            }
        }
        if (sprint.state == Sprint.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.associate.done')
        if (story.state < Story.STATE_ESTIMATED)
            throw new IllegalStateException('is.sprint.error.associate.story.noEstimated')
        if (story.state == Story.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.associate.story.done')

        if (story.parentSprint != null) {
            unPlan(story, false)
        }
        resetRank(story)

        User user = (User) springSecurityService.currentUser
        sprint.addToStories(story)
        if (sprint.state == Sprint.STATE_WAIT) {
            sprint.capacity = sprint.totalEffort
        }
        story.parentSprint = sprint

        if (sprint.state == Sprint.STATE_INPROGRESS) {
            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            if (!story.plannedDate) {
                story.plannedDate = story.inProgressDate
            }

            def autoCreateTaskOnEmptyStory = sprint.parentRelease.parentProduct.preferences.autoCreateTaskOnEmptyStory
            if (autoCreateTaskOnEmptyStory)
                if (autoCreateTaskOnEmptyStory && !story.tasks) {
                    def emptyTask = new Task(name: story.name, state: Task.STATE_WAIT, description: story.description, parentStory: story)
                    taskService.save(emptyTask, user)
                }
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        } else {
            story.state = Story.STATE_PLANNED
            story.plannedDate = new Date()
        }

        def rank = sprint.stories?.findAll{ it.state != Story.STATE_DONE }?.size() ?: 1

        setRank(story, rank)

        story.tasks.findAll {it.state == Task.STATE_WAIT}.each {
            it.backlog = sprint
            taskService.update(it, user)
        }
    }

    @PreAuthorize('(productOwner(#story.backlog) or scrumMaster(#story.backlog)) and !archivedProduct(#story.backlog)')
    public void unPlan(Story story, Boolean fullUnPlan = true) {

        def sprint = story.parentSprint
        if (!sprint) {
            throw new RuntimeException('is.story.error.not.associated')
        }
        if (story.state == Story.STATE_DONE) {
            throw new IllegalStateException('is.sprint.error.dissociate.story.done')
        }
        if (fullUnPlan && story.dependences?.find{it.state > Story.STATE_ESTIMATED}) {
            throw new RuntimeException(g.message(code:'is.story.error.dependences.dissociate',args: [story.name, story.dependences.find{it.state > Story.STATE_ESTIMATED}.name]).toString())
        }

        resetRank(story)

        sprint.removeFromStories(story)
        if (sprint.state == Sprint.STATE_WAIT) {
            sprint.capacity = sprint.totalEffort
        }
        story.parentSprint = null

        User u = (User) springSecurityService.currentUser

        def tasks = story.tasks.asList()
        tasks.each { Task task ->
            if (task.state == Task.STATE_DONE) {
                task.doneDate = null
                task.type = Task.TYPE_URGENT
                taskService.update(task, u)
            } else if (fullUnPlan) {
                taskService.delete(task, u)
            } else {
                task.state = Task.STATE_WAIT // TODO The sprint close problem occurs right after the task state setting. Before that, executing task.properties is OK, after it is not
                task.inProgressDate = null
            }
        }

        story.state = Story.STATE_ESTIMATED
        story.inProgressDate = null
        story.plannedDate = null

        setRank(story, 1)
    }

    // TODO check rights
    def unPlanAll(Collection<Sprint> sprintList, Integer sprintState = null) {
        sprintList.sort  { sprint1, sprint2 -> sprint2.orderNumber <=> sprint1.orderNumber }
        def product = sprintList.first().parentProduct
        bufferBroadcast(channel:'product-'+product.id)
        def storiesUnPlanned = []
        sprintList.each { sprint ->
            if ((!sprintState) || (sprintState && sprint.state == sprintState)) {
                def stories = sprint.stories.findAll { story ->
                    story.state != Story.STATE_DONE
                }.sort {st1, st2 -> st2.rank <=> st1.rank }
                stories.each {
                    unPlan(it)
                }
                // Recalculate the sprint estimated velocity (capacite)
                if (sprint.state == Sprint.STATE_WAIT)
                    sprint.capacity = (Double) sprint.stories?.sum { it.effort } ?: 0
                storiesUnPlanned.addAll(stories)
            }
        }
        resumeBufferedBroadcast(channel:'product-'+product.id)
        return storiesUnPlanned
    }

    // TODO check rights
    def autoPlan(Release release, Double capacity) {
        def nbPoints = 0
        int nbSprint = 0
        def product = release.parentProduct
        def sprints = release.sprints.findAll { it.state == Sprint.STATE_WAIT }.sort { it.orderNumber }.asList()
        int maxSprint = sprints.size()

        // Get the list of stories that have been estimated
        Collection<Story> itemsList = product.stories.findAll { it.state == Story.STATE_ESTIMATED }.sort { it.rank };

        Sprint currentSprint = null

        def plannedStories = []
        // Associate story in each sprint
        for (Story story: itemsList) {
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
                        }
                        else {
                            nbSprint++
                            break;
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
    private void setRank(Story story, int rank) {
        def backlogStates = [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]
        def stories = (story.state in backlogStates) ? story.backlog.stories.findAll { it.state in backlogStates } : story.parentSprint?.stories
        rank = checkRankDependencies(story, rank)
        def sortedStories = stories?.asList()?.sort { it.rank }
        sortedStories?.findAll { it.rank >= rank }?.each {
            it.rank++
            it.save()
        }
        story.rank = rank
        cleanRanks(sortedStories)
    }

    // TODO check rights
    private void cleanRanks(stories){
        stories.sort{ it.rank }
        int i = 0
        def error = false
        while(i < stories.size() && !error){
            error = stories[i].rank != (i + 1)
            i++
        }
        if (error){
            stories.eachWithIndex{ story, ind ->
                if (story.rank != ind + 1){
                    if (log.debugEnabled)
                        log.debug("story ${story.uid} as rank ${story.rank} but should have ${ind + 1} fixing!!")
                    story.rank = ind + 1
                    story.save()
                }
            }
        }
    }

    // TODO check rights
    private void resetRank(Story story) {
        def backlogStates = [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]
        def stories = (story.state in backlogStates) ? story.backlog.stories.findAll { it.state in backlogStates } : story.parentSprint?.stories
        if (stories) {
            stories.asList().findAll { it.rank > story.rank }.each {
                it.rank--
                it.save()
            }
        }
    }

    // TODO check rights
    @PreAuthorize('(productOwner(#story.backlog) or scrumMaster(#story.backlog))  and !archivedProduct(#story.backlog)')
    private void rank(Story story, int rank) {

        rank = checkRankDependencies(story, rank)
        if ((story.dependsOn || story.dependences ) && story.rank == rank) {
            return
        }
        if (story.state in [Story.STATE_SUGGESTED, Story.STATE_DONE]) {
            throw new IllegalStateException(g.message(code: 'is.story.rank.error').toString())
        }
        def stories = null
        def backlogStates = [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]
        if (story.state in backlogStates) {
            stories = story.backlog.stories.findAll { it.state in backlogStates }
        } else if (story.state == Story.STATE_PLANNED || story.state == Story.STATE_INPROGRESS) {
            stories = story.parentSprint.stories
            def maxRankInProgress = stories.findAll { it.state != Story.STATE_DONE }.size()
            if (story.state == Story.STATE_INPROGRESS && rank > maxRankInProgress) {
                rank = maxRankInProgress
            }
        }

        def sortedStories = stories.asList().sort { it.rank }

        Range affectedRange = story.rank..rank
        int delta = affectedRange.isReverse() ? 1 : -1
        sortedStories.findAll { it != story && it.rank in affectedRange }.each {
            it.rank += delta
            it.save() // consider push
        }

        story.rank = rank

        cleanRanks(sortedStories)
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    def acceptToBacklog(List<Story> stories) {
        def storiesA = []
        def product = stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->
            if (story.state > Story.STATE_SUGGESTED)
                throw new IllegalStateException('is.story.error.not.state.suggested')

            if (story.dependsOn?.state == Story.STATE_SUGGESTED)
                throw new IllegalStateException(g.message(code:'is.story.error.dependsOn.suggested', args:[story.name, story.dependsOn.name]).toString())

            story.rank = (Story.countAllAcceptedOrEstimated(story.backlog.id)?.list()[0] ?: 0) + 1
            story.state = Story.STATE_ACCEPTED
            story.acceptedDate = new Date()
            if (((Product) story.backlog).preferences.noEstimation) {
                story.estimatedDate = new Date()
                story.effort = 1
                story.state = Story.STATE_ESTIMATED
            }

            if (!story.save(flush: true))
                throw new RuntimeException(story.errors?.toString())

            User u = (User) springSecurityService.currentUser
            storiesA << story

            story.addActivity(u, 'acceptAs', story.name)
            broadcast(function: 'accept', message: story, channel:'product-'+story.backlog.id)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_ACCEPTED))
        }
        resumeBufferedBroadcast(channel:'product-'+product.id)
        return storiesA
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    void returnToSandbox(List<Story> stories) {
        Product product = (Product)stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->
            if (!(story.state in [Story.STATE_ESTIMATED, Story.STATE_ACCEPTED]))
                throw new IllegalStateException('is.story.error.not.in.backlog')

            resetRank(story)
            story.rank = 0
            story.state = Story.STATE_SUGGESTED
            story.acceptedDate = null
            story.estimatedDate = null
            story.effort = null

            if (!story.save(flush: true))
                throw new RuntimeException()

            User u = (User) springSecurityService.currentUser

            story.addActivity(u, 'returnToSandbox', story.name)
            broadcast(function: 'returnToSandbox', message: story, channel:'product-'+story.backlog.id)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_UPDATED))
        }
        resumeBufferedBroadcast(channel:'product-'+product.id)
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    def acceptToFeature(List<Story> stories) {
        def features = []
        def product =  stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->
            if (story.state > Story.STATE_SUGGESTED)
                throw new IllegalStateException('is.story.error.not.state.suggested')

            User user = (User) springSecurityService.currentUser
            def feature = new Feature(story.properties)
            feature.description = (feature.description ?: '')
            feature.validate()
            def i = 1
            while (feature.hasErrors()) {
                if (feature.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    feature.name = feature.name + '_' + i
                    feature.validate()
                }else if (story.errors.getFieldError('name')?.defaultMessage?.contains("maximum size")) {
                    feature.name = feature.name[0..20]
                    feature.validate()
                } else {
                    throw new RuntimeException()
                }
            }

            featureService.save(feature, (Product) story.backlog)

            story.attachments.each { attachment ->
                feature.addAttachment(story.creator, attachmentableService.getFile(attachment), attachment.filename)
            }

            feature.tags = story.tags
            delete([story], feature)
            features << feature

            feature.addActivity(user, 'acceptAs', feature.name)
            publishEvent(new IceScrumStoryEvent(feature, this.class, user, IceScrumStoryEvent.EVENT_ACCEPTED_AS_FEATURE))
        }
        resumeBufferedBroadcast(channel:'product-'+product.id)
        return features
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    def acceptToUrgentTask(List<Story> stories) {
        def tasks = []
        def product = stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->

            if (story.state > Story.STATE_SUGGESTED)
                throw new IllegalStateException('is.story.error.not.state.suggested')

            def task = new Task(story.properties)
            task.type = Task.TYPE_URGENT
            task.state = Task.STATE_WAIT
            task.description = (story.affectVersion ? g.message(code: 'is.story.affectVersion') + ': ' + story.affectVersion : '') + (task.description ?: '')

            def sprint = (Sprint) Sprint.findCurrentSprint(product.id).list()
            if (!sprint) {
                throw new IllegalStateException('is.story.error.not.acceptedAsUrgentTask')
            }
            task.backlog = sprint

            task.validate()
            def i = 1
            while (task.hasErrors() && task.errors.getFieldError('name')) {
                if (task.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    task.name = task.name + '_' + i
                    task.validate()
                }else if (story.errors.getFieldError('name')?.defaultMessage?.contains("maximum size")) {
                    task.name = task.name[0..20]
                    task.validate()
                } else {
                    throw new RuntimeException()
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

            publishEvent(new IceScrumStoryEvent(task, this.class, (User) springSecurityService.currentUser, IceScrumStoryEvent.EVENT_ACCEPTED_AS_TASK))
        }
        resumeBufferedBroadcast(channel:'product-'+product.id)
        return tasks
    }

    @PreAuthorize('inProduct(#story.backlog) and !archivedProduct(#story.backlog)')
    void done(Story story) {
        done([story])
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    void done(List<Story> stories) {
        def product = stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->

            if (story.parentSprint.state != Sprint.STATE_INPROGRESS) {
                throw new IllegalStateException('is.sprint.error.declareAsDone.state.not.inProgress')
            }
            if (story.state != Story.STATE_INPROGRESS) {
                throw new IllegalStateException('is.story.error.declareAsDone.state.not.inProgress')
            }

            //Move story to last rank in sprint
            rank(story, Story.countByParentSprint(story.parentSprint))

            story.state = Story.STATE_DONE
            story.doneDate = new Date()
            story.parentSprint.velocity += story.effort


            if (!story.save()) {
                throw new RuntimeException()
            }

            User user = (User) springSecurityService.currentUser

            broadcast(function: 'update', message: story, channel:'product-'+product.id)
            story.addActivity(user, 'done', story.name)
            publishEvent(new IceScrumStoryEvent(story, this.class, user, IceScrumStoryEvent.EVENT_DONE))

            // Set all tasks to done (and story estimation to 0)
            story.tasks?.findAll{ it.state != Task.STATE_DONE }?.each { t ->
                t.estimation = 0
                taskService.update(t, user)
            }

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
        resumeBufferedBroadcast(channel:'product-'+product.id)
    }

    @PreAuthorize('productOwner(#story.backlog) and !archivedProduct(#story.backlog)')
    void unDone(Story story) {
        unDone([story])
    }

    @PreAuthorize('productOwner(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    void unDone(List<Story> stories) {
        def product = stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->

            if (story.state != Story.STATE_DONE) {
                throw new IllegalStateException('is.story.error.declareAsUnDone.state.not.done')
            }

            if (story.parentSprint.state != Sprint.STATE_INPROGRESS) {
                throw new IllegalStateException('is.sprint.error.declareAsUnDone.state.not.inProgress')
            }

            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            story.doneDate = null
            story.parentSprint.velocity -= story.effort

            //Move story to last rank of in progress stories in sprint
            rank(story, Story.countByParentSprintAndState(story.parentSprint, Story.STATE_INPROGRESS) + 1)

            if (!story.save())
                throw new RuntimeException()

            User u = (User) springSecurityService.currentUser

            story.addActivity(u, 'unDone', story.name)
            broadcast(function: 'update', message: story, channel:'product-'+product.id)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_UNDONE))
        }
        if (stories)
            clicheService.createOrUpdateDailyTasksCliche(stories[0]?.parentSprint)
        resumeBufferedBroadcast(channel:'product-'+product.id)
    }

    // TODO check rights
    private notDependsOn(Story story) {
        def oldDepends = story.dependsOn
        story.dependsOn = null
        oldDepends.lastUpdated = new Date()
        oldDepends.save()

        broadcast(function: 'update', message: story, channel:'product-'+story.backlog.id)
        User u = (User) springSecurityService.currentUser
        publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_UPDATED))
    }

    @PreAuthorize('inProduct(#stories[0].backlog) and !archivedProduct(#stories[0].backlog)')
    def copy(List<Story> stories) {
        def copiedStories = []
        def product =  stories[0].backlog
        bufferBroadcast(channel:'product-'+product.id)
        stories.each { story ->
            def copiedStory = new Story(
                    name: story.name + '_1',
                    state: Story.STATE_SUGGESTED,
                    description: story.description,
                    notes: story.notes,
                    dateCreated: new Date(),
                    type: story.type,
                    backlog: product,
                    affectVersion: story.affectVersion,
                    origin: story.name,
                    feature: story.feature,
                    executionFrequency: story.executionFrequency
            )

            copiedStory.validate()
            def i = 1
            while (copiedStory.hasErrors()) {
                if (copiedStory.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    copiedStory.name = copiedStory.name + '_' + i
                    copiedStory.validate()
                } else if (story.errors.getFieldError('name')?.defaultMessage?.contains("maximum size")) {
                    copiedStory.name = copiedStory.name[0..20]
                    copiedStory.validate()
                }else {
                    throw new RuntimeException()
                }
            }
            save(copiedStory, (Product) story.backlog, (User) springSecurityService.currentUser)

            story.attachments?.each{ Attachment a ->
                def currentFile = attachmentableService.getFile(a)
                def newFile = File.createTempFile(a.name, a.ext)
                FileUtils.copyFile(currentFile, newFile)
                copiedStory.addAttachment(a.poster, newFile, a.name+(a.ext? '.'+a.ext :''))
            }

            story.comments?.each{ Comment c ->
                copiedStory.addComment(c.poster, c.body)
            }

            copiedStory.tags = story.tags

            story.acceptanceTests?.each{
                acceptanceTestService.save(new AcceptanceTest(name:it.name, description:it.description), copiedStory, (User) springSecurityService.currentUser)
            }

            copiedStories << copiedStory.refresh()
        }
        resumeBufferedBroadcast(channel:'product-'+product.id)
        return copiedStories
    }

    @Transactional(readOnly = true)
    def unMarshall(def story, Product p = null, Sprint sp = null) {
        try {
            def acceptedDate = null
            if (story.acceptedDate?.text() && story.acceptedDate?.text() != "")
                acceptedDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.acceptedDate.text()) ?: null

            def estimatedDate = null
            if (story.estimatedDate?.text() && story.estimatedDate?.text() != "")
                estimatedDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.estimatedDate.text()) ?: null

            def plannedDate = null
            if (story.plannedDate?.text() && story.plannedDate?.text() != "")
                plannedDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.plannedDate.text()) ?: null

            def inProgressDate = null
            if (story.inProgressDate?.text() && story.inProgressDate?.text() != "")
                inProgressDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.inProgressDate.text()) ?: null

            def doneDate = null
            if (story.doneDate?.text() && story.doneDate?.text() != "")
                doneDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.doneDate.text()) ?: null

            def s = new Story(
                    name: story."${'name'}".text(),
                    description: story.description.text(),
                    notes: story.notes.text(),
                    creationDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.creationDate.text()),
                    effort: story.effort.text().isEmpty() ? null : story.effort.text().toBigDecimal(),
                    value: story.value.text().isEmpty() ? null : story.value.text().toInteger(),
                    rank: story.rank.text().toInteger(),
                    state: story.state.text().toInteger(),
                    suggestedDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(story.suggestedDate.text()),
                    acceptedDate: acceptedDate,
                    estimatedDate: estimatedDate,
                    plannedDate: plannedDate,
                    inProgressDate: inProgressDate,
                    doneDate: doneDate,
                    type: story.type.text().toInteger(),
                    executionFrequency: story.executionFrequency.text().toInteger(),
                    affectVersion: story.affectVersion.text(),
                    uid: story.@uid.text()?.isEmpty() ? story.@id.text().toInteger() : story.@uid.text().toInteger(),
                    origin: story.origin.text()
            )

            if (!story.feature?.@uid?.isEmpty() && p) {
                def f = p.features.find { it.uid == story.feature.@uid.text().toInteger() } ?: null
                if (f) {
                    f.addToStories(s)
                }
            }else if(!story.feature?.@id?.isEmpty() && p){
                def f = p.features.find { it.uid == story.feature.@id.text().toInteger() } ?: null
                if (f) {
                    f.addToStories(s)
                }
            }

            if (!story.actor?.@uid?.isEmpty() && p) {
                def a = p.actors.find { it.uid == story.actor.@uid.text().toInteger() } ?: null
                if (a) {
                    a.addToStories(s)
                }
            }else if(!story.actor?.@id?.isEmpty() && p){
                def a = p.actors.find { it.uid == story.actor.@id.text().toInteger() } ?: null
                if (a) {
                    a.addToStories(s)
                }
            }

            if (story.textAs || story.textICan || story.textTo) {
                def i18n = { g.message(code: "is.story.template." + it) }
                migrateTemplatesOnStory(story.textAs.text().trim(), story.textICan.text().trim(), story.textTo.text().trim(), s, i18n)
            }

            if (p) {
                def u
                if (!story.creator?.@uid?.isEmpty())
                    u = ((User) p.getAllUsers().find { it.uid == story.creator.@uid.text() } ) ?: null
                else{
                    u = ApplicationSupport.findUserUIDOldXMl(story,'creator',p.getAllUsers())
                }
                if (u)
                    s.creator = u
                else
                    s.creator = p.productOwners.first()
            }



            story.tasks?.task?.each {
                def t = taskService.unMarshall(it, p)
                if (sp) {
                    t.backlog = sp
                    s.addToTasks(t)
                }
            }

            story.acceptanceTests?.acceptanceTest?.each {
                def at = acceptanceTestService.unMarshall(it, p, s)
                s.addToAcceptanceTests(at)
            }

            if (p) {
                p.addToStories(s)
            }

            if (sp) {
                sp.addToStories(s)
            }

            return s
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }

    void migrateTemplatesInDb(){
        try {
            if (!System.properties['icescrum.oracle']){
                def session = sessionFactory.getCurrentSession()
                def storiesToMigrate = session.createSQLQuery("""SELECT id, text_as, textican, text_to
                                                                 FROM icescrum2_story
                                                                 WHERE text_as IS NOT NULL OR textican IS NOT NULL OR text_to IS NOT NULL""")
                        .addScalar("id", Hibernate.LONG)
                        .addScalar("text_as", Hibernate.STRING)
                        .addScalar("textican", Hibernate.STRING)
                        .addScalar("text_to", Hibernate.STRING)
                        .list()
                if (storiesToMigrate){
                    if (log.debugEnabled) {
                        log.debug("Old story templates to migrate: " + storiesToMigrate.size())
                    }
                    def (iId, iAs, iIcan, iTo) = 0..3
                    def i18n = { messageSource.getMessage("is.story.template." + it, null, null, new Locale("en")) }
                    storiesToMigrate.each { storyToMigrate ->
                        // Old school because no GORM Static API at the point where it is called in IcescrumCoreGrailsPlugin
                        Story story = (Story) session.get(Story.class, storyToMigrate[iId])
                        migrateTemplatesOnStory(storyToMigrate[iAs], storyToMigrate[iIcan], storyToMigrate[iTo], story, i18n)
                        session.save(story)
                    }
                    def removeOldTemplates = session.createSQLQuery("UPDATE icescrum2_story SET text_as = NULL, textican = NULL, text_to = NULL");
                    removeOldTemplates.executeUpdate()
                    if (log.debugEnabled) {
                        log.debug("Old story templates migrated")
                    }
                }
            }
        } catch (SQLGrammarException e){
            if (log.debugEnabled) {
                log.debug("No old story template to migrate")
            }
        }
    }

    private void migrateTemplatesOnStory(oldAs, oldIcan, oldTo, story, i18n) {
        def storyTemplateContent = [as: oldAs, ican: oldIcan, to: oldTo]
        if (story.actor) {
            def actor = story.actor
            storyTemplateContent.as = oldAs.replaceAll(actor.name, 'A[' + actor.uid + '-' + actor.name + ']')
        }
        if (storyTemplateContent.values().any { it }) {
            def storyTemplate = generateTemplateWithContent(storyTemplateContent, i18n)
            if (!story.description) {
                story.description = storyTemplate
            } else if ((storyTemplate.size() + story.description.size()) < 3000) {
                story.description += ("\n" + storyTemplate)
            } else if (!story.notes) {
                story.notes = storyTemplate
            } else if ((storyTemplate.size() + story.notes.size()) < 5000) {
                story.notes += ("\n" + storyTemplate)
            } else {
                def templateError = "Unable to migrate template: notes and description are full. STORY: $story.uid-$story.name TEMPLATE: $storyTemplate"
                if (log.debugEnabled) {
                    log.debug(templateError)
                } else {
                    println templateError
                }
            }
        }
    }

    private String generateTemplateWithContent(Map fields, i18n) {
        return ['as', 'ican', 'to'].collect {
            i18n(it) + " " + (fields[it] ?: '')
        }.join("\n")
    }

    private void manageActors(story, product) {
        def newActor
        if (story.description) {
            def actorIdMatcher = story.description =~ /A\[(.+?)-.*?\]/
            if (actorIdMatcher) {
                def idString = actorIdMatcher[0][1]
                if (idString.isInteger()) {
                    newActor = product.actors.find{ it.uid == idString.toInteger() }
                }
            }
        }
        if (newActor) {
            if (story.actor != newActor) {
                story.actor?.removeFromStories(story)
                newActor.addToStories(story)
            }
        } else if (story.actor) {
            story.actor.removeFromStories(story)
        }
    }

    private int checkRankDependencies(story, rank){
        if (story.dependsOn){
            if (story.state in [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]){
                if (rank <= story.dependsOn.rank){
                    rank = story.dependsOn.rank + 1
                }
            }
            else if (story.dependsOn.parentSprint == story.parentSprint){
                if (rank <= story.dependsOn.rank){
                    rank = story.dependsOn.rank + 1
                }
            }
        }
        if (story.dependences){
            if (story.state in [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]){
                def highestRank = story.dependences.findAll{ it.state in [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]}?.collect{it.rank}?.min()
                if (highestRank && highestRank <= rank){
                    rank = highestRank - 1
                }
            }
            else if (story.state > Story.STATE_ESTIMATED){
                def highestRank = story.dependences.findAll{ it.parentSprint == story.parentSprint }?.collect{it.rank}?.min()
                if (highestRank && highestRank <= rank){
                    rank = highestRank - 1
                }
            }
        }
        return rank
    }
}
