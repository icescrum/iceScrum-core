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
import org.icescrum.core.utils.ServicesUtils
import org.springframework.security.access.prepost.PreAuthorize

import java.text.SimpleDateFormat

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
        if (checkIntegrity) {
            if (sprint.state == Sprint.STATE_DONE) {
                def illegalDirtyProperties = sprint.dirtyPropertyNames - ['goal', 'deliveredVersion', 'retrospective', 'doneDefinition']
                if (illegalDirtyProperties) {
                    throw new BusinessException(code: 'is.sprint.error.update.done')
                }
            }
            if (sprint.state == Sprint.STATE_INPROGRESS && startDate && sprint.startDate != startDate) {
                throw new BusinessException(code: 'is.sprint.error.update.startdate.inprogress')
            }
        }
        if (startDate && endDate) {
            def nextSprint = sprint.parentRelease.sprints?.find { it.orderNumber == sprint.orderNumber + 1 }
            if (sprint.endDate != endDate && nextSprint && endDate >= nextSprint.startDate) {
                if (nextSprint) {
                    def deltaDays = (endDate - nextSprint.startDate) + 1
                    if (nextSprint.endDate + deltaDays <= sprint.parentRelease.endDate) {
                        update(nextSprint, nextSprint.startDate + deltaDays, nextSprint.endDate + deltaDays, false)
                    } else {
                        if (nextSprint.startDate + deltaDays >= sprint.parentRelease.endDate) {
                            delete(nextSprint)
                        } else {
                            update(nextSprint, nextSprint.startDate + deltaDays, sprint.parentRelease.endDate, false, updateRelease)
                        }
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
        int freeDays = ApplicationSupport.getMidnightTime(release.endDate) - ApplicationSupport.getMidnightTime(startDate) + 1
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
        sprint.stories?.each { Story story ->
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
            def notDoneStories = sprint.stories.findAll { it.state != Story.STATE_DONE }
            storyService.plan(nextSprint, notDoneStories)
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

    // TODO check rights
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
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche))
                        values << [
                                remainingTime: currentRemaining,
                                label        : lastDaycliche.clone().clearTime().time
                        ]
                }
            }
        }
        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)))
                    values << [
                            remainingTime: null,
                            label        : (lastDaycliche + (it + 1)).clearTime().time
                    ]
            }
        }
        if (!values.isEmpty()) {
            values.first()?.idealTime = sprint.initialRemainingTime ?: 0
            values.last()?.idealTime = 0
        }
        return values
    }

    // TODO check rights
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
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche)) {
                        values << [
                                tasksDone: xmlRoot."${Cliche.TASKS_DONE}".toInteger(),
                                tasks    : xmlRoot."${Cliche.TOTAL_TASKS}".toInteger(),
                                label    : lastDaycliche.clone().clearTime().time
                        ]
                    }
                }
            }
        }

        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche + (it + 1))) {
                    values << [
                            tasksDone: null,
                            tasks    : null,
                            label    : (lastDaycliche + (it + 1)).clearTime().time
                    ]
                }
            }
        }
        return values
    }

    // TODO check rights
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
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche)) {
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
        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProject.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche + (it + 1))) {
                    values << [
                            storiesDone: null,
                            stories    : null,
                            pointsDone : null,
                            totalPoints: null,
                            label      : (lastDaycliche + (it + 1)).clearTime().time
                    ]
                }
            }
        }
        return values
    }

    // TODO check rights
    def copyRecurrentTasksFromPreviousSprint(Sprint sprint) {
        if (sprint.orderNumber == 1 && sprint.parentRelease.orderNumber == 1) {
            throw new BusinessException(code: 'is.sprint.copyRecurrentTasks.error.no.sprint.before')
        }
        if (sprint.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.copyRecurrentTasks.error.sprint.done')
        }
        def previousSprint = sprint.previousSprint
        def tasks = previousSprint.tasks.findAll { it.type == Task.TYPE_RECURRENT }
        if (!tasks) {
            throw new BusinessException(code: 'is.sprint.copyRecurrentTasks.error.no.recurrent.tasks')
        }
        def copiedTasks = []
        tasks.each { it ->
            def tmp = new Task()
            tmp.properties = it.properties
            tmp.todoDate = new Date()
            tmp.state = Task.STATE_WAIT
            tmp.backlog = sprint
            tmp.responsible = null
            tmp.participants = null
            tmp.inProgressDate = null
            tmp.doneDate = null
            taskService.save(tmp, it.creator)
            copiedTasks << tmp
        }
        return copiedTasks
    }

    def unMarshall(def sprintXml, def options) {
        Project project = options.project
        def release = options.release
        Sprint.withTransaction(readOnly: !options.save) { transaction ->
            def sprint = new Sprint(
                    retrospective: sprintXml.retrospective.text() ?: null,
                    doneDefinition: sprintXml.doneDefinition.text() ?: null,
                    doneDate: ApplicationSupport.parseDate(sprintXml.doneDate.text()),
                    inProgressDate: ApplicationSupport.parseDate(sprintXml.inProgressDate.text()),
                    state: sprintXml.state.text().toInteger(),
                    velocity: sprintXml.velocity.text().isNumber() ? sprintXml.velocity.text().toDouble() : 0d,
                    dailyWorkTime: (sprintXml.dailyWorkTime.text().isNumber()) ? sprintXml.dailyWorkTime.text().toDouble() : 8d,
                    capacity: sprintXml.capacity.text().isNumber() ? sprintXml.capacity.text().toDouble() : 0d,
                    todoDate: ApplicationSupport.parseDate(sprintXml.todoDate.text()),
                    startDate: ApplicationSupport.parseDate(sprintXml.startDate.text()),
                    endDate: ApplicationSupport.parseDate(sprintXml.endDate.text()),
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
                }
            }
            options.sprint = null
            return (Sprint) importDomainsPlugins(sprintXml, sprint, options)
        }
    }
}
