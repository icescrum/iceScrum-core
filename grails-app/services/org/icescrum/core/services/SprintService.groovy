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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.utils.DateUtils
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class SprintService extends IceScrumEventPublisher {

    def clicheService
    def taskService
    def storyService
    def springSecurityService

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void save(Sprint sprint, Release release) {
        if (release.state == Release.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.error.release.done')
        }
        sprint.orderNumber = (release.sprints?.size() ?: 0) + 1
        release.addToSprints(sprint)
        sprint.save()
        publishSynchronousEvent(IceScrumEventType.CREATE, sprint)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProject) or scrumMaster(#sprint.parentRelease.parentProject)) and !archivedProject(#sprint.parentRelease.parentProject)')
    void update(Sprint sprint, Date startDate = null, Date endDate = null, def checkIntegrity = true, boolean updateRelease = true) {
        if (startDate) {
            startDate = DateUtils.getMidnightDate(startDate)
        }
        if (endDate) {
            endDate = DateUtils.getMidnightDate(endDate)
        }
        if (checkIntegrity) {
            if (sprint.state == Sprint.STATE_DONE) {
                def illegalDirtyProperties = sprint.dirtyPropertyNames - ['goal', 'deliveredVersion', 'retrospective', 'doneDefinition']
                if (illegalDirtyProperties) {
                    throw new BusinessException(code: 'is.sprint.error.update.done')
                }
            }
            if (sprint.state == Sprint.STATE_INPROGRESS && startDate && DateUtils.getMidnightDate(sprint.startDate) != startDate) {
                throw new BusinessException(code: 'is.sprint.error.update.startdate.inprogress')
            }
        }
        if (startDate && endDate) {
            def nextSprint = sprint.parentRelease.sprints?.find { it.orderNumber == sprint.orderNumber + 1 }
            if (DateUtils.getMidnightDate(sprint.endDate) != endDate && nextSprint && endDate >= DateUtils.getMidnightDate(nextSprint.startDate)) {
                def nextSprintStartDate = DateUtils.getMidnightDate(nextSprint.startDate)
                def nextSprintEndDate = DateUtils.getMidnightDate(nextSprint.endDate)
                def parentReleaseEndDate = DateUtils.getMidnightDate(sprint.parentRelease.endDate)
                def deltaDays = (endDate - nextSprintStartDate) + 1
                if (nextSprintEndDate + deltaDays <= parentReleaseEndDate) {
                    update(nextSprint, nextSprintStartDate + deltaDays, nextSprintEndDate + deltaDays, false)
                } else {
                    if (nextSprintStartDate + deltaDays >= parentReleaseEndDate) {
                        delete(nextSprint)
                    } else {
                        update(nextSprint, nextSprintStartDate + deltaDays, parentReleaseEndDate, false, updateRelease)
                    }
                }
            }
            sprint.startDate = startDate
            sprint.endDate = endDate
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, sprint)
        if (updateRelease) {
            sprint.parentRelease.lastUpdated = new Date()
            sprint.parentRelease.save(flush: true)
        } else {
            sprint.save()
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, sprint, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProject) or scrumMaster(#sprint.parentRelease.parentProject)) and !archivedProject(#sprint.parentRelease.parentProject)')
    void delete(Sprint sprint) {
        if (sprint.state >= Sprint.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.sprint.error.delete.inprogress')
        }
        def release = sprint.parentRelease
        def nextSprints = release.sprints.findAll { it.orderNumber > sprint.orderNumber }
        if (nextSprints) {
            delete(nextSprints.first()) // cascades the delete recursively
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, sprint)
        sprint.tasks.findAll { it.parentStory == null }.each {
            taskService.delete(it, springSecurityService.currentUser)
        }
        storyService.unPlanAll([sprint])
        release.removeFromSprints(sprint)
        release.save()
        publishSynchronousEvent(IceScrumEventType.DELETE, sprint, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    def generateSprints(Release release, Date startDate = release.startDate) {
        if (release.state == Release.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.error.release.done')
        }
        if (release.sprints) {
            startDate = release.sprints*.endDate.max() + 1
        }
        int freeDays = DateUtils.getMidnightDate(release.endDate) - DateUtils.getMidnightDate(startDate) + 1
        int sprintDuration = release.parentProject.preferences.estimatedSprintsDuration
        int nbNewSprints = Math.floor(freeDays / sprintDuration)
        if (nbNewSprints == 0) {
            throw new BusinessException(code: 'is.release.sprints.not.enough.time')
        }
        def newSprints = []
        nbNewSprints.times {
            Date endDate = startDate + sprintDuration - 1
            def newSprint = new Sprint(goal: 'Generated Sprint', startDate: startDate, endDate: endDate)
            save(newSprint, release)
            newSprints << newSprint
            startDate = endDate + 1
        }
        return newSprints
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProject) or scrumMaster(#sprint.parentRelease.parentProject)) and !archivedProject(#sprint.parentRelease.parentProject)')
    void activate(Sprint sprint) {
        if (sprint.parentRelease.state != Release.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.sprint.error.activate.release.not.inprogress')
        }
        sprint.parentRelease.sprints.each {
            if (it.state == Sprint.STATE_INPROGRESS) {
                throw new BusinessException(code: 'is.sprint.error.activate.other.inprogress')
            } else if (it.orderNumber < sprint.orderNumber && it.state < Sprint.STATE_DONE) {
                throw new BusinessException(code: 'is.sprint.error.activate.previous.not.closed')
            }
        }
        def autoCreateTaskOnEmptyStory = sprint.parentRelease.parentProject.preferences.autoCreateTaskOnEmptyStory
        sprint.stories?.sort { it.rank }?.each { Story story ->
            story.state = Story.STATE_INPROGRESS
            story.inProgressDate = new Date()
            storyService.update(story)
            if (autoCreateTaskOnEmptyStory && story.tasks?.size() == 0) {
                def newTask = new Task(name: story.name, description: story.description, backlog: sprint, parentStory: story)
                taskService.save(newTask, (User) springSecurityService.currentUser)
            }
        }
        def previousSprintDoneDefinition = sprint.previousSprint?.doneDefinition // Before updating sprint properties because "previouSprint" flushes the session and clean dirtyProperties
        sprint.state = Sprint.STATE_INPROGRESS
        sprint.inProgressDate = new Date()
        if (previousSprintDoneDefinition && !sprint.doneDefinition) {
            sprint.doneDefinition = previousSprintDoneDefinition
        }
        sprint.initialRemainingTime = sprint.totalRemaining
        update(sprint)
        clicheService.createSprintCliche(sprint, new Date(), Cliche.TYPE_ACTIVATION)
        clicheService.createOrUpdateDailyTasksCliche(sprint)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProject) or scrumMaster(#sprint.parentRelease.parentProject)) and !archivedProject(#sprint.parentRelease.parentProject)')
    void reactivate(Sprint sprint) {
        if (!sprint.reactivable) {
            if (sprint.parentRelease.state != Release.STATE_INPROGRESS) {
                throw new BusinessException(code: 'is.sprint.error.activate.release.not.inprogress')
            } else {
                throw new BusinessException(code: 'is.sprint.error.reactivate.next.not.wait')
            }
        }
        if (sprint.parentRelease.sprints.find { it.state == Sprint.STATE_INPROGRESS }) {
            throw new BusinessException(code: 'is.sprint.error.activate.other.inprogress')
        }
        sprint.state = Sprint.STATE_INPROGRESS
        sprint.doneDate = null
        sprint.velocity = 0d
        update(sprint)
        clicheService.removeLastSprintCliche(sprint.refresh())
        clicheService.createOrUpdateDailyTasksCliche(sprint)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProject) or scrumMaster(#sprint.parentRelease.parentProject)) and !archivedProject(#sprint.parentRelease.parentProject)')
    void close(Sprint sprint) {
        if (sprint.state != Sprint.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.sprint.error.close.not.inprogress')
        }
        def nextSprint = sprint.nextSprint
        if (nextSprint) {
            sprint.tasks.findAll { it.type == Task.TYPE_URGENT && it.state != Task.STATE_DONE }?.each { Task task ->
                task.backlog = nextSprint
                task.state = Task.STATE_WAIT
                taskService.update(task, springSecurityService.currentUser)
            }
            def notDoneStories = sprint.stories.findAll { it.state != Story.STATE_DONE }.sort { -it.rank }
            notDoneStories.each { notDoneStory ->
                storyService.plan(nextSprint, notDoneStory, 1)
            }
        } else {
            sprint.tasks.findAll { it.type == Task.TYPE_URGENT && it.state != Task.STATE_DONE }?.each { Task task ->
                task.state = Task.STATE_DONE
                taskService.update(task, springSecurityService.currentUser)
            }
            storyService.unPlanAll([sprint])
        }
        def doneStories = sprint.stories.findAll { it.state == Story.STATE_DONE }
        sprint.velocity = (Double) doneStories*.effort.sum() ?: 0
        sprint.state = Sprint.STATE_DONE
        sprint.doneDate = new Date()
        sprint.tasks.findAll { it.type == Task.TYPE_URGENT || it.type == Task.TYPE_RECURRENT }?.each {
            it.lastUpdated = new Date()
        }
        update(sprint, null, null, false)
        clicheService.createSprintCliche(sprint.refresh(), new Date(), Cliche.TYPE_CLOSE)
        clicheService.createOrUpdateDailyTasksCliche(sprint)
    }

    def sprintBurndownRemainingValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.inProgressDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.doneDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate
        clicheService.createOrUpdateDailyTasksCliche(sprint)
        sprint.cliches?.sort { a, b -> a.datePrise <=> b.datePrise }?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    def currentRemaining = xmlRoot."${Cliche.REMAINING_TIME}".toFloat()
                    if ((DateUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !DateUtils.isDateWeekend(lastDaycliche)) {
                        values << [
                                remainingTime: currentRemaining,
                                label        : lastDaycliche.clone().clearTime().time
                        ]
                    }
                }
            }
        }
        // Insert missing days because we need them for idealTime that needs every point because it is not linear anymore
        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((DateUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !DateUtils.isDateWeekend(lastDaycliche + (it + 1))) {
                    values << [
                            remainingTime: null,
                            label        : (lastDaycliche + (it + 1)).clearTime().time
                    ]
                }
            }
        }
        // Hiding weekends is hard on d3 timescale, here we work around that by creating every point of the ideal line
        // As weekends days don't appear in values if the option is enabled, the line will decrease more slowly during weekends
        if (!values.isEmpty() && sprint.initialRemainingTime) {
            def unit = sprint.initialRemainingTime / (values.size() - 1)
            values.eachWithIndex { value, index ->
                value.idealTime = sprint.initialRemainingTime - index * unit
            }
            values.last().idealTime = 0 // Fix floating point errors
        }
        return values
    }

    def sprintBurnupTasksValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.inProgressDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.doneDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate
        clicheService.createOrUpdateDailyTasksCliche(sprint)
        sprint.cliches?.sort { a, b -> a.datePrise <=> b.datePrise }?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    if ((DateUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !DateUtils.isDateWeekend(lastDaycliche)) {
                        values << [
                                tasksDone: xmlRoot."${Cliche.TASKS_DONE}".toInteger(),
                                tasks    : xmlRoot."${Cliche.TOTAL_TASKS}".toInteger(),
                                label    : lastDaycliche.clone().clearTime().time
                        ]
                    }
                }
            }
        }
        return values
    }

    def sprintBurnupStoriesValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.inProgressDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.doneDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate
        clicheService.createOrUpdateDailyTasksCliche(sprint)
        sprint.cliches?.sort { a, b -> a.datePrise <=> b.datePrise }?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    if ((DateUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !DateUtils.isDateWeekend(lastDaycliche)) {
                        values << [
                                storiesDone: xmlRoot."${Cliche.STORIES_DONE}".toInteger(),
                                stories    : xmlRoot."${Cliche.TOTAL_STORIES}".toInteger(),
                                pointsDone : xmlRoot."${Cliche.STORIES_POINTS_DONE}"?.toString()?.isBigDecimal() ? xmlRoot."${Cliche.STORIES_POINTS_DONE}".toBigDecimal() : 0,
                                totalPoints: xmlRoot."${Cliche.STORIES_TOTAL_POINTS}"?.toString()?.isBigDecimal() ? xmlRoot."${Cliche.STORIES_TOTAL_POINTS}".toBigDecimal() : 0,
                                label      : lastDaycliche.clone().clearTime().time
                        ]
                    }
                }
            }
        }
        return values
    }

    def copyRecurrentTasks(Sprint sprint) {
        if (sprint.orderNumber == 1 && sprint.parentRelease.orderNumber == 1) {
            throw new BusinessException(code: 'is.sprint.copyRecurrentTasks.error.no.sprint.before')
        }
        if (sprint.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.copyRecurrentTasks.error.sprint.done')
        }
        def tasks = sprint.previousSprint.tasks.findAll { it.type == Task.TYPE_RECURRENT }
        if (!tasks) {
            throw new BusinessException(code: 'is.sprint.copyRecurrentTasks.error.no.recurrent.tasks')
        }
        tasks.each { task ->
            Task clonedTask = new Task(
                    name: task.name,
                    responsible: task.responsible,
                    color: task.color,
                    type: task.type,
                    description: task.description,
                    notes: task.notes,
                    estimation: task.initial,
                    backlog: sprint
            )
            taskService.save(clonedTask, task.creator)
        }
    }

    def unMarshall(def sprintXml, def options) {
        Project project = options.project
        def release = options.release
        Sprint.withTransaction(readOnly: !options.save) { transaction ->
            def sprint = new Sprint(
                    retrospective: sprintXml.retrospective.text() ?: null,
                    doneDefinition: sprintXml.doneDefinition.text() ?: null,
                    doneDate: DateUtils.parseDateFromExport(sprintXml.doneDate.text()),
                    inProgressDate: DateUtils.parseDateFromExport(sprintXml.inProgressDate.text()),
                    state: sprintXml.state.text().toInteger(),
                    velocity: sprintXml.velocity.text().isNumber() ? sprintXml.velocity.text().toDouble() : 0d,
                    dailyWorkTime: (sprintXml.dailyWorkTime.text().isNumber()) ? sprintXml.dailyWorkTime.text().toDouble() : 8d,
                    capacity: sprintXml.capacity.text().isNumber() ? sprintXml.capacity.text().toDouble() : 0d,
                    todoDate: DateUtils.parseDateFromExport(sprintXml.todoDate.text()),
                    startDate: DateUtils.parseDateFromExport(sprintXml.startDate.text()),
                    endDate: DateUtils.parseDateFromExport(sprintXml.endDate.text()),
                    orderNumber: sprintXml.orderNumber.text().toInteger(),
                    description: sprintXml.description.text() ?: null,
                    goal: sprintXml.goal.text() ?: null,
                    deliveredVersion: sprintXml.deliveredVersion.text() ?: null,
                    initialRemainingTime: sprintXml.initialRemainingTime.text().isNumber() ? sprintXml.initialRemainingTime.text().toFloat() : null
            )
            // References other objects
            if (release) {
                release.addToSprints(sprint)
            }
            // Save before some hibernate stuff
            if (options.save) {
                sprint.validate()
                // Fix for R6 import
                if (sprint.hasErrors() && sprint.errors.getFieldError('startDate')) {
                    if (log.debugEnabled) {
                        log.debug("Warning: sprint with startDate $sprint.startDate overlaps the previous sprint. Fixing...")
                    }
                    sprint.startDate = sprint.startDate + 1
                }
                sprint.save()
            }
            options.sprint = sprint
            options.timebox = sprint
            // Child objects
            sprintXml.cliches.cliche.each {
                clicheService.unMarshall(it, options)
            }
            options.timebox = null
            if (project) {
                sprintXml.stories.story.each {
                    storyService.unMarshall(it, options)
                }
                sprintXml.tasks.task.each {
                    taskService.unMarshall(it, options)
                }
            }
            if (options.save) {
                sprint.save()
                if (project) {
                    sprintXml.attachments.attachment.each { _attachmentXml ->
                        def uid = options.userUIDByImportedID?."${_attachmentXml.posterId.text().toInteger()}" ?: null
                        User user = project.getUserByUidOrOwner(uid)
                        ApplicationSupport.importAttachment(sprint, user, options.path, _attachmentXml)
                    }
                    sprint.attachments_count = sprintXml.attachments.attachment.size() ?: 0
                }
            }
            options.sprint = null
            return (Sprint) importDomainsPlugins(sprintXml, sprint, options)
        }
    }
}
