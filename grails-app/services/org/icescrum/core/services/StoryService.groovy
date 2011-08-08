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
 */

package org.icescrum.core.services

import grails.plugin.fluxiable.Activity
import org.springframework.security.access.prepost.PreAuthorize

import groovy.util.slurpersupport.NodeChild
import java.text.SimpleDateFormat
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumStoryEvent

class StoryService {
    def productService
    def taskService
    def springSecurityService
    def clicheService
    def featureService
    def attachmentableService
    def securityService
    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    static transactional = true

    void save(Story story, Product p, User u, Sprint s = null) {

        if (!story.effort)
            story.effort = null

        story.backlog = p
        story.creator = u

        if (story.textAs != '') {
            def actor = Actor.findByBacklogAndName(p, story.textAs)
            if (actor) {
                story.actor = actor
            }
        }

        story.state = Story.STATE_SUGGESTED
        story.suggestedDate = new Date()

        if (story.state < Story.STATE_ACCEPTED && story.effort >= 0)
            null;

        else if (story.effort > 0) {
            story.state = Story.STATE_ESTIMATED
            story.estimatedDate = new Date()
        }

        if (story.save()) {
            p.addToStories(story)
            story.addFollower(u)
            story.addActivity(u, Activity.CODE_SAVE, story.name)
            broadcast(function: 'add', message: story)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_CREATED))
        } else {
            throw new RuntimeException()
        }
    }

    void delete(Collection<Story> stories, history = true) {
        bufferBroadcast()
        stories.each { _item ->

            if (_item.state >= Story.STATE_PLANNED)
               throw new IllegalStateException()

            if (!(_item.creator.id == springSecurityService.currentUser.id) && !securityService.productOwner(_item.backlog.id, springSecurityService.authentication)) {
                throw new IllegalAccessException()
            }
            _item.removeAllAttachments()
            _item.removeLinkByFollow(_item.id)
            if (_item.state != Story.STATE_SUGGESTED)
                resetRank(_item)

            def p = _item.backlog
            p.removeFromStories(_item)

            def id = _item.id
            _item.delete()

            p.save()
            if (history) {
                p.addActivity(springSecurityService.currentUser, Activity.CODE_DELETE, _item.name)
            }
            broadcast(function: 'delete', message: [class: _item.class, id: id, state: _item.state])
        }
        resumeBufferedBroadcast()
    }

    void delete(Story _item, boolean history = true) {
        delete([_item], history)
    }

    @PreAuthorize('productOwner() or scrumMaster()')
    void update(Story story, Sprint sp = null) {
        if (story.textAs != '' && story.actor?.name != story.textAs) {
            def actor = Actor.findByBacklogAndName(story.backlog, story.textAs)
            if (actor) {
                story.actor = actor
            } else {
                story.actor = null
            }
        } else if (story.textAs == '' && story.actor) {
            story.actor = null
        }

        if (!sp && !story.parentSprint && (story.state == Story.STATE_ACCEPTED || story.state == Story.STATE_ESTIMATED)) {
            if (story.effort != null) {
                story.state = Story.STATE_ESTIMATED
                story.estimatedDate = new Date()
            }
            if (story.effort == null) {
                story.state = Story.STATE_ACCEPTED
                story.estimatedDate = null
            }
        } else if (story.parentSprint && story.parentSprint.state == Sprint.STATE_WAIT) {
            story.parentSprint.capacity = (Double) story.parentSprint.stories.sum { it.effort }
        }
        if (!story.save())
            throw new RuntimeException()

        User u = (User) springSecurityService.currentUser

        story.addActivity(u, Activity.CODE_UPDATE, story.name)
        broadcast(function: 'update', message: story)
        publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_UPDATED))
    }

    /**
     * Estimate a story (set the effort value)
     * @param story
     * @param estimation
     */
    @PreAuthorize('teamMember() or scrumMaster()')
    void estimate(Story story, estimation) {
        def oldState = story.state
        if (story.state < Story.STATE_ACCEPTED || story.state == Story.STATE_DONE)
            throw new IllegalStateException('is.story.error.estimated')
        if (!(estimation instanceof Number) && (estimation instanceof String && !estimation.isNumber())) {
            story.state = Story.STATE_ACCEPTED
            story.effort = null
            story.estimatedDate = null
        } else {
            if (story.state == Story.STATE_ACCEPTED)
                story.state = Story.STATE_ESTIMATED
            story.effort = estimation.toInteger()
            story.estimatedDate = new Date()
        }
        if (!story.save())
            throw new RuntimeException()

        User u = (User) springSecurityService.currentUser
        story.addActivity(u, Activity.CODE_UPDATE, story.name)

        broadcast(function: 'estimate', message: story)
        if (oldState != story.state && story.state == Story.STATE_ESTIMATED)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_ESTIMATED))
        else if (oldState != story.state && story.state == Story.STATE_ACCEPTED)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_ACCEPTED))
    }

    @PreAuthorize('productOwner(#p) or scrumMaster(#p)')
    void plan(Sprint sprint, Collection<Story> stories) {
        stories.each {
            this.plan(sprint, it)
        }
    }

    /**
     * Associate a story in a sprint
     * @param sprint The targeted sprint
     * @param story The story to associate
     * @param user The user performing the action
     */
    @PreAuthorize('productOwner(#p) or scrumMaster(#p)')
    void plan(Sprint sprint, Story story) {
        // It is possible to associate a story if it is at least in the "ESTIMATED" state and not in the "DONE" state
        // It is not possible to associate a story in a "DONE" sprint either
        if (sprint.state == Sprint.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.associate.done')
        if (story.state < Story.STATE_ESTIMATED)
            throw new IllegalStateException('is.sprint.error.associate.story.noEstimated')
        if (story.state == Story.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.associate.story.done')

        // If the story was already in a sprint, it is dissociated beforehand
        if (story.parentSprint != null) {
            //Shift to next Sprint (no delete tasks)
            unPlan(story, false)
        } else {
            resetRank(story)
        }

        User user = (User) springSecurityService.currentUser

        // Change the story state
        if (sprint.state == Sprint.STATE_INPROGRESS) {
            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            if (!story.plannedDate)
                story.plannedDate = story.inProgressDate

            def autoCreateTaskOnEmptyStory = sprint.parentRelease.parentProduct.preferences.autoCreateTaskOnEmptyStory
            if (autoCreateTaskOnEmptyStory)
                if (autoCreateTaskOnEmptyStory && !story.tasks) {
                    def emptyTask = new Task(name: story.name, state: Task.STATE_WAIT, description: story.description, creator: user, backlog: sprint)
                    story.addToTasks(emptyTask).save()
                    emptyTask.save()
                }
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        } else {
            story.state = Story.STATE_PLANNED
            story.plannedDate = new Date()
        }

        sprint.addToStories(story)
        setRank(story, 1)

        if (!story.save(flush: true))
            throw new RuntimeException()

        // Calculate the velocity of the sprint
        if (sprint.state == Sprint.STATE_WAIT)
            sprint.capacity = (Double) sprint.stories.sum { it.effort }
        sprint.save()

        broadcast(function: 'plan', message: story)

        story.tasks.findAll {it.state == Task.STATE_WAIT}.each {
            it.backlog = sprint
            taskService.update(it, user)
        }

        if (story.state == Story.STATE_INPROGRESS)
            publishEvent(new IceScrumStoryEvent(story, this.class, user, IceScrumStoryEvent.EVENT_INPROGRESS))
        else
            publishEvent(new IceScrumStoryEvent(story, this.class, user, IceScrumStoryEvent.EVENT_PLANNED))
    }

    /**
     * UnPlan the specified backlog item from the specified sprint
     * @param _sprint
     * @param pbi
     * @return
     */
    void unPlan(Story story, Boolean deleteTasks = true) {

        def sprint = story.parentSprint
        if (!sprint)
            throw new RuntimeException('is.story.error.not.associated')

        if (story.state == Story.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.dissociate.story.done')

        resetRank(story)

        sprint.removeFromStories(story)
        story.parentSprint = null

        User u = (User) springSecurityService.currentUser

        if (sprint.state == Sprint.STATE_WAIT)
            sprint.capacity = (Double) sprint.stories?.sum { it.effort } ?: 0

        if (sprint.state == Sprint.STATE_INPROGRESS) {
            def tasks = story.tasks.asList()
            for (task in tasks) {
                if (task.state == Task.STATE_DONE) {
                    taskService.storyTaskToSprintTask(task, Task.TYPE_URGENT, u)
                } else {
                    if (!deleteTasks) {
                        task.state = Task.STATE_WAIT
                        task.inProgressDate = null
                    } else {
                        taskService.delete(task, u)
                    }
                }
            }
        }

        def tasks = story.tasks.asList()
        if (deleteTasks) {
            for (task in tasks) {
                taskService.delete(task, u)
            }
        }

        story.state = Story.STATE_ESTIMATED
        setRank(story, 1)
        if (!story.save(flush: true))
            throw new RuntimeException()

        broadcast(function: 'update', message: sprint)
        broadcast(function: 'unPlan', message: story)
        publishEvent(new IceScrumStoryEvent(story, this.class, (User) springSecurityService.currentUser, IceScrumStoryEvent.EVENT_UNPLANNED))
    }

    /**
     * UnPlan all stories from t odo sprints
     * @param spList
     * @param state (optional) If this argument is specified, dissociate only the sprint with the specified state
     */
    def unPlanAll(Collection<Sprint> sprintList, Integer sprintState = null) {
        def spList = sprintList
        bufferBroadcast()
        def storiesUnPlanned = []
        spList.sort { sp1, sp2 -> sp2.orderNumber <=> sp1.orderNumber }.each { sp ->
            if ((!sprintState) || (sprintState && sp.state == sprintState)) {
                def stories = sp.stories.findAll { pbi ->
                    pbi.state != Story.STATE_DONE
                }.sort {st1, st2 -> st2.rank <=> st1.rank }
                stories.each {
                    unPlan(it)
                }
                // Recalculate the sprint estimated velocity (capacite)
                if (sp.state == Sprint.STATE_WAIT)
                    sp.capacity = (Double) sp.stories?.sum { it.effort } ?: 0
                storiesUnPlanned.addAll(stories)
            }
        }
        resumeBufferedBroadcast()
        return storiesUnPlanned
    }

    def autoPlan(Release release, Double capacity) {
        int nbPoints = 0
        int nbSprint = 0
        def product = release.parentProduct
        def sprints = release.sprints.findAll { it.state == Sprint.STATE_WAIT }.sort { it.orderNumber }.asList()
        int maxSprint = sprints.size()

        // Get the list of PBI that have been estimated
        Collection<Story> itemsList = product.stories.findAll { it.state == Story.STATE_ESTIMATED }.sort { it.rank };

        Sprint currentSprint = null

        def plannedStories = []
        // Associate pbi in each sprint
        for (Story pbi: itemsList) {
            if ((nbPoints + pbi.effort) > capacity || currentSprint == null) {
                nbPoints = 0
                if (nbSprint < maxSprint) {
                    currentSprint = sprints[nbSprint++]
                    nbPoints += currentSprint.capacity
                    while (nbPoints + pbi.effort > capacity && currentSprint.capacity > 0) {
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
                    this.plan(currentSprint, pbi)
                    plannedStories << pbi
                    nbPoints += pbi.effort

                } else {
                    break
                }
            } else {
                this.plan(currentSprint, pbi)
                plannedStories << pbi
                nbPoints += pbi.effort
            }
        }
        return plannedStories
    }

    void setRank(Story story, int rank) {
        def stories = null
        if (story.state == Story.STATE_ACCEPTED || story.state == Story.STATE_ESTIMATED)
            stories = Story.findAllAcceptedOrEstimated(story.backlog.id).list(order: 'asc', sort: 'rank')
        else if (story.state == Story.STATE_PLANNED || story.state == Story.STATE_INPROGRESS || story.state == Story.STATE_DONE) {
            stories = story.parentSprint?.stories
        }
        stories?.each { pbi ->
            if (pbi.rank >= rank) {
                pbi.rank++
                pbi.save()
            }
        }
        story.rank = rank
        if (!story.save())
            throw new RuntimeException()
    }

    void resetRank(Story story) {
        def stories = null
        if (story.state == Story.STATE_ACCEPTED || story.state == Story.STATE_ESTIMATED)
            stories = Story.findAllAcceptedOrEstimated(story.backlog.id).list(order: 'asc', sort: 'rank')
        else if (story.state == Story.STATE_PLANNED || story.state == Story.STATE_INPROGRESS || story.state == Story.STATE_DONE) {
            stories = story.parentSprint?.stories
        }
        stories.each { pbi ->
            if (pbi.rank > story.rank) {
                pbi.rank--
                pbi.save()
            }
        }
    }

    @PreAuthorize('productOwner(#p) or scrumMaster(#p)')
    boolean rank(Story movedItem, int rank) {
        if (movedItem.rank == rank) {
            return false
        }

        def stories = null
        if (movedItem.state == Story.STATE_ACCEPTED || movedItem.state == Story.STATE_ESTIMATED)
            stories = Story.findAllAcceptedOrEstimated(movedItem.backlog.id).list(order: 'asc', sort: 'rank')
        else if (movedItem.state == Story.STATE_PLANNED || movedItem.state == Story.STATE_INPROGRESS || movedItem.state == Story.STATE_DONE) {
            stories = movedItem.parentSprint.stories
            def maxRankInProgress = stories.findAll {it.state != Story.STATE_DONE}?.size()
            if (movedItem.state == Story.STATE_INPROGRESS && rank > maxRankInProgress) {
                rank = maxRankInProgress
            }
        }

        if (movedItem.rank > rank) {
            stories.each {it ->
                if (it.rank >= rank && it.rank <= movedItem.rank && it != movedItem) {
                    it.rank = it.rank + 1
                    it.save()
                }
            }
        } else {
            stories.each {it ->
                if (it.rank <= rank && it.rank >= movedItem.rank && it != movedItem) {
                    it.rank = it.rank - 1
                    it.save()
                }
            }
        }
        movedItem.rank = rank

        broadcast(function: 'update', message: movedItem)
        return movedItem.save() ? true : false
    }

    @PreAuthorize('productOwner()')
    def acceptToBacklog(Story story) {
        return acceptToBacklog([story])
    }

    @PreAuthorize('productOwner()')
    def acceptToBacklog(Collection<Story> stories) {
        def storiesA = []
        bufferBroadcast()
        stories.each { pbi ->
            if (pbi.state != Story.STATE_SUGGESTED)
                throw new IllegalStateException('is.story.error.not.state.suggested')

            pbi.rank = (Story.countAllAcceptedOrEstimated(pbi.backlog.id)?.list()[0] ?: 0) + 1
            pbi.state = Story.STATE_ACCEPTED
            pbi.acceptedDate = new Date()
            if (((Product) pbi.backlog).preferences.noEstimation) {
                pbi.estimatedDate = new Date()
                pbi.effort = 1
                pbi.state = Story.STATE_ESTIMATED
            }

            if (!pbi.save(flush: true))
                throw new RuntimeException()

            User u = (User) springSecurityService.currentUser
            storiesA << pbi

            pbi.addActivity(u, 'acceptAs', pbi.name)
            broadcast(function: 'accept', message: pbi)
            publishEvent(new IceScrumStoryEvent(pbi, this.class, u, IceScrumStoryEvent.EVENT_ACCEPTED))
        }
        resumeBufferedBroadcast()
        return storiesA
    }

    @PreAuthorize('productOwner()')
    def acceptToFeature(Story story) {
        return acceptToFeature([story])
    }

    @PreAuthorize('productOwner()')
    def acceptToFeature(Collection<Story> stories) {
        def features = []
        bufferBroadcast()
        stories.each { pbi ->
            if (pbi.state != Story.STATE_SUGGESTED)
                throw new IllegalStateException('is.story.error.not.state.suggested')

            User user = (User) springSecurityService.currentUser
            def feature = new Feature(pbi.properties)
            feature.description = (feature.description ?: '') + ' ' + getTemplateStory(pbi)
            feature.validate()
            def i = 1
            while (feature.hasErrors()) {
                if (feature.errors.getFieldError('name')) {
                    i += 1
                    feature.name = feature.name + '_' + i
                    feature.validate()
                } else {
                    throw new RuntimeException()
                }
            }

            featureService.save(feature, (Product) pbi.backlog)

            pbi.attachments.each { attachment ->
                feature.addAttachment(pbi.creator, attachmentableService.getFile(attachment), attachment.filename)
            }
            this.delete(pbi, false)
            features << feature

            feature.addActivity(user, 'acceptAs', feature.name)
            publishEvent(new IceScrumStoryEvent(feature, this.class, user, IceScrumStoryEvent.EVENT_ACCEPTED_AS_FEATURE))
        }
        resumeBufferedBroadcast()
        return features
    }

    @PreAuthorize('productOwner()')
    def acceptToUrgentTask(Story story) {
        return acceptToUrgentTask([story])
    }

    @PreAuthorize('productOwner()')
    def acceptToUrgentTask(Collection<Story> stories) {
        def tasks = []
        bufferBroadcast()
        stories.each { pbi ->

            if (pbi.state != Story.STATE_SUGGESTED)
                throw new IllegalStateException('is.story.error.not.state.suggested')

            def task = new Task(pbi.properties)

            task.state = Task.STATE_WAIT
            task.description = (task.description ?: '') + ' ' + getTemplateStory(pbi)

            def sprint = (Sprint) Sprint.findCurrentSprint(pbi.backlog.id).list()[0]
            if (!sprint)
                throw new IllegalStateException('is.story.error.notacceptedAsUrgentTask')

            task.validate()
            def i = 1
            while (task.hasErrors()) {
                if (task.errors.getFieldError('name')) {
                    i += 1
                    task.name = task.name + '_' + i
                    task.validate()
                } else {
                    throw new RuntimeException()
                }
            }

            taskService.saveUrgentTask(task, sprint, pbi.creator)


            pbi.attachments.each { attachment ->
                task.addAttachment(pbi.creator, attachmentableService.getFile(attachment), attachment.filename)
            }
            pbi.comments.each {
                comment ->
                task.notes = (task.notes ?: '') + '\n --- \n ' + comment.body + '\n --- \n '
            }
            tasks << task
            this.delete(pbi, false)

            publishEvent(new IceScrumStoryEvent(task, this.class, (User) springSecurityService.currentUser, IceScrumStoryEvent.EVENT_ACCEPTED_AS_TASK))
        }
        resumeBufferedBroadcast()
        return tasks
    }

    private String getTemplateStory(Story story) {
        def textStory = ''
        def tempTxt = [story.textAs, story.textICan, story.textTo]*.trim()
        if (tempTxt != ['null', 'null', 'null'] && tempTxt != ['', '', ''] && tempTxt != [null, null, null]) {
            textStory += g.message(code: 'is.story.template.as') + ' '
            textStory += (story.actor?.name ?: story.textAs ?: '') + ', '
            textStory += g.message(code: 'is.story.template.ican') + ' '
            textStory += (story.textICan ?: '') + ' '
            textStory += g.message(code: 'is.story.template.to') + ' '
            textStory += (story.textTo ?: '')
        }
        textStory
    }


    @PreAuthorize('inProduct()')
    void done(Story story) {
        done([story])
    }

    @PreAuthorize('productOwner()')
    void done(Collection<Story> stories) {
        bufferBroadcast()
        stories.each { story ->

            if (story.parentSprint.state != Sprint.STATE_INPROGRESS) {
                throw new IllegalStateException('is.sprint.error.declareAsDone.state.not.inProgress')
            }

            if (story.state != Story.STATE_INPROGRESS) {
                throw new IllegalStateException('is.story.error.declareAsDone.state.not.inProgress')
            }

            story.state = Story.STATE_DONE
            story.doneDate = new Date()
            story.parentSprint.velocity += story.effort

            //Move story to last rank in sprint
            rank(story, Story.countByParentSprint(story.parentSprint))

            if (!story.save())
                throw new RuntimeException()

            User u = (User) springSecurityService.currentUser

            broadcast(function: 'done', message: story)
            story.addActivity(u, 'done', story.name)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_DONE))

            // Set all tasks to done (and pbi's estimation to 0)
            story.tasks?.findAll{ it.state != Task.STATE_DONE }?.each { t ->
                t.estimation = 0
                taskService.update(t, u)
            }
        }
        if (stories)
            clicheService.createOrUpdateDailyTasksCliche(stories[0]?.parentSprint)
        resumeBufferedBroadcast()
    }

    @PreAuthorize('productOwner()')
    void unDone(Story story) {
        unDone([story])
    }

    @PreAuthorize('productOwner()')
    void unDone(Collection<Story> stories) {
        bufferBroadcast()
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
            broadcast(function: 'unDone', message: story)
            publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_UNDONE))
        }
        if (stories)
            clicheService.createOrUpdateDailyTasksCliche(stories[0]?.parentSprint)
        resumeBufferedBroadcast()
    }

    void associateFeature(Feature feature, Story story) {
        feature.addToStories(story)
        if (!feature.save(flush:true))
            throw new RuntimeException()

        broadcast(function: 'associated', message: story)
        User u = (User) springSecurityService.currentUser
        publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_FEATURE_ASSOCIATED))
    }

    void dissociateFeature(Story story) {
        def feature = story.feature
        feature.removeFromStories(story)
        if (!feature.save(flush:true))
            throw new RuntimeException()

        broadcast(function: 'dissociated', message: story)
        User u = (User) springSecurityService.currentUser
        publishEvent(new IceScrumStoryEvent(story, this.class, u, IceScrumStoryEvent.EVENT_FEATURE_DISSOCIATED))
    }

    @PreAuthorize('inProduct()')
    def copy(Story story) {
        copy([story])
    }

    @PreAuthorize('inProduct()')
    def copy(Collection<Story> stories) {
        def copiedStories = []
        bufferBroadcast()
        stories.each { story ->
            def copiedStory = new Story(
                    name: story.name + '_1',
                    state: Story.STATE_SUGGESTED,
                    description: story.description,
                    notes: story.notes,
                    dateCreated: new Date(),
                    type: story.type,
                    textAs: story.textAs,
                    textICan: story.textICan,
                    textTo: story.textTo,
                    backlog: story.backlog,
                    affectVersion: story.affectVersion,
                    origin: story.name,
                    feature: story.feature,
                    actor: story.actor
            )

            copiedStory.validate()
            def i = 1
            while (copiedStory.hasErrors()) {
                if (copiedStory.errors.getFieldError('name')) {
                    i += 1
                    copiedStory.name = story.name + '_' + i
                    copiedStory.validate()
                } else {
                    throw new RuntimeException()
                }
            }
            save(copiedStory, (Product) story.backlog, (User) springSecurityService.currentUser)
            copiedStories << copiedStory
        }
        resumeBufferedBroadcast()
        return copiedStories
    }

    @Transactional(readOnly = true)
    def unMarshall(NodeChild story, Product p = null, Sprint sp = null) {
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
                    effort: story.effort.text().isEmpty() ? null : story.effort.text().toInteger(),
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
                    textAs: story.textAs.text(),
                    textICan: story.textICan.text(),
                    textTo: story.textTo.text(),
                    affectVersion: story.affectVersion.text(),
                    origin: story.origin.text()
            )

            if (!story.feature?.@id?.isEmpty() && p) {
                def f = p.features.find {
                    def id = it.idFromImport ?: it.id
                    id == story.feature.@id.text().toInteger()
                } ?: null
                if (f) {
                    f.addToStories(s)
                }
            }

            if (!story.actor?.@id?.isEmpty() && p) {
                def a = p.actors.find {
                    def id = it.idFromImport ?: it.id
                    id == story.actor.@id.text().toInteger()
                } ?: null
                if (a) {
                    a.addToStories(s)
                }
            }

            if (!story.creator?.@id?.isEmpty() && p) {
                def u = null
                if (story.creator.@id.text().isNumber())
                    u = (User) p.getAllUsers().find {
                        def id = it.idFromImport ?: it.id
                        id == story.creator.@id.text().toInteger()
                    } ?: null
                if (u)
                    s.creator = u
                else
                    s.creator = p.productOwners.first()
            }


            story.tasks.task.each {
                def t = taskService.unMarshall(it, p)
                if (sp) {
                    t.backlog = sp
                    s.addToTasks(t)
                }
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

}