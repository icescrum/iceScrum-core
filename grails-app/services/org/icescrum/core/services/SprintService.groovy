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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

import java.text.SimpleDateFormat
import org.icescrum.core.utils.ServicesUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport

@Transactional
class SprintService extends IceScrumEventPublisher {

    def clicheService
    def taskService
    def storyService
    def springSecurityService
    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void save(Sprint sprint, Release release) {
        if (release.state == Release.STATE_DONE) {
            throw new IllegalStateException('is.sprint.error.release.done')
        }
        sprint.orderNumber = (release.sprints?.size() ?: 0) + 1
        release.addToSprints(sprint)
        if (!sprint.save()) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.CREATE, sprint)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProduct) or scrumMaster(#sprint.parentRelease.parentProduct)) and !archivedProduct(#sprint.parentRelease.parentProduct)')
    void update(Sprint sprint, Date startDate = null, Date endDate = null, def checkIntegrity = true, boolean updateRelease = true) {

        if (checkIntegrity) {
            if (sprint.state == Sprint.STATE_DONE) {
                def illegalDirtyProperties = sprint.dirtyPropertyNames - ['doneDefinition', 'retrospective']
                if (illegalDirtyProperties) {
                    throw new IllegalStateException('is.sprint.error.update.done')
                }
            }
            if (sprint.state == Sprint.STATE_INPROGRESS && startDate && sprint.startDate != startDate) {
                throw new IllegalStateException('is.sprint.error.update.startdate.inprogress')
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
            if (!sprint.parentRelease.save(flush: true)) {
                throw new RuntimeException()
            }
        } else {
            sprint.save()
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, sprint, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProduct) or scrumMaster(#sprint.parentRelease.parentProduct)) and !archivedProduct(#sprint.parentRelease.parentProduct)')
    void delete(Sprint sprint) {
        if (sprint.state >= Sprint.STATE_INPROGRESS) {
            throw new IllegalStateException('is.sprint.error.delete.inprogress')
        }
        def release = sprint.parentRelease
        def nextSprints = release.sprints.findAll { it.orderNumber > sprint.orderNumber }
        if (nextSprints) {
            delete(nextSprints.first()) // cascades the delete recursively
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, sprint)
        storyService.unPlanAll([sprint])
        release.removeFromSprints(sprint)
        release.save()
        publishSynchronousEvent(IceScrumEventType.DELETE, sprint, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    def generateSprints(Release release, Date startDate = release.startDate) {
        if (release.state == Release.STATE_DONE) {
            throw new IllegalStateException('is.sprint.error.release.done')
        }
        if (release.sprints) {
            startDate = release.sprints*.endDate.max() + 1
        }
        int freeDays = ApplicationSupport.getMidnightTime(release.endDate) - ApplicationSupport.getMidnightTime(startDate) + 1
        int sprintDuration = release.parentProduct.preferences.estimatedSprintsDuration
        int nbNewSprints = Math.floor(freeDays / sprintDuration)
        if (nbNewSprints == 0) {
            throw new IllegalStateException('is.release.sprints.not.enough.time')
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

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProduct) or scrumMaster(#sprint.parentRelease.parentProduct)) and !archivedProduct(#sprint.parentRelease.parentProduct)')
    void activate(Sprint sprint) {
        if (sprint.parentRelease.state != Release.STATE_INPROGRESS) {
            throw new IllegalStateException('is.sprint.error.activate.release.not.inprogress')
        }
        sprint.parentRelease.sprints.each {
            if (it.state == Sprint.STATE_INPROGRESS) {
                throw new IllegalStateException('is.sprint.error.activate.other.inprogress')
            } else if (it.orderNumber < sprint.orderNumber && it.state < Sprint.STATE_DONE) {
                throw new IllegalStateException('is.sprint.error.activate.previous.not.closed')
            }
        }
        def autoCreateTaskOnEmptyStory = sprint.parentRelease.parentProduct.preferences.autoCreateTaskOnEmptyStory
        sprint.stories?.each { Story story ->
            storyService.update(story, [state: Story.STATE_INPROGRESS])
            if (autoCreateTaskOnEmptyStory && story.tasks?.size() == 0) {
                def newTask = new Task(name: story.name, description: story.description, backlog: sprint, parentStory: story)
                taskService.save(newTask, (User) springSecurityService.currentUser)
            }
        }
        sprint.state = Sprint.STATE_INPROGRESS
        sprint.activationDate = new Date()
        if (sprint.previousSprint?.doneDefinition && !sprint.doneDefinition) {
            sprint.doneDefinition = sprint.previousSprint.doneDefinition
        }
        sprint.initialRemainingTime = sprint.totalRemaining
        update(sprint)
        clicheService.createSprintCliche(sprint, new Date(), Cliche.TYPE_ACTIVATION)
        clicheService.createOrUpdateDailyTasksCliche(sprint)
    }

    @PreAuthorize('(productOwner(#sprint.parentRelease.parentProduct) or scrumMaster(#sprint.parentRelease.parentProduct)) and !archivedProduct(#sprint.parentRelease.parentProduct)')
    void close(Sprint sprint) {
        if (sprint.state != Sprint.STATE_INPROGRESS) {
            throw new IllegalStateException('is.sprint.error.close.not.inprogress')
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
            storyService.unPlanAll([sprint])
        }
        def doneStories = sprint.stories.findAll { it.state == Story.STATE_DONE }
        sprint.velocity = (Double) doneStories*.effort.sum() ?: 0
        sprint.state = Sprint.STATE_DONE
        sprint.closeDate = new Date()
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
        def lastDaycliche = sprint.activationDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.closeDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate
        clicheService.createOrUpdateDailyTasksCliche(sprint)

        sprint.cliches?.sort { a, b -> a.datePrise <=> b.datePrise }?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    def currentRemaining = xmlRoot."${Cliche.REMAINING_TIME}".toFloat()
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche))
                        values << [
                                remainingTime: currentRemaining,
                                label: "${g.formatDate(date: lastDaycliche, formatName: 'is.date.format.short')}"
                        ]
                }
            }
        }
        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)))
                    values << [
                            remainingTime: null,
                            label: "${g.formatDate(date: lastDaycliche + (it + 1), formatName: 'is.date.format.short')}"
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
        def lastDaycliche = sprint.activationDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.closeDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate

        clicheService.createOrUpdateDailyTasksCliche(sprint)

        sprint.cliches?.sort { a, b -> a.datePrise <=> b.datePrise }?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche)) {
                        values << [
                                tasksDone: xmlRoot."${Cliche.TASKS_DONE}".toInteger(),
                                tasks: xmlRoot."${Cliche.TOTAL_TASKS}".toInteger(),
                                label: "${g.formatDate(date: lastDaycliche, formatName: 'is.date.format.short')}"
                        ]
                    }
                }
            }
        }

        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche + (it + 1))) {
                    values << [
                            tasksDone: null,
                            tasks: null,
                            label: "${g.formatDate(date: lastDaycliche + (it + 1), formatName: 'is.date.format.short')}"
                    ]
                }
            }
        }
        return values
    }

    // TODO check rights
    def sprintBurnupStoriesValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.activationDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.closeDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate

        clicheService.createOrUpdateDailyTasksCliche(sprint)

        sprint.cliches?.sort { a, b -> a.datePrise <=> b.datePrise }?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche)) {
                        values << [
                                storiesDone: xmlRoot."${Cliche.STORIES_DONE}".toInteger(),
                                stories: xmlRoot."${Cliche.TOTAL_STORIES}".toInteger(),
                                pointsDone: xmlRoot."${Cliche.STORIES_POINTS_DONE}"?.toString()?.isBigDecimal() ? xmlRoot."${Cliche.STORIES_POINTS_DONE}".toBigDecimal() :0,
                                totalPoints: xmlRoot."${Cliche.STORIES_TOTAL_POINTS}"?.toString()?.isBigDecimal() ? xmlRoot."${Cliche.STORIES_TOTAL_POINTS}".toBigDecimal() :0,
                                label: "${g.formatDate(date: lastDaycliche, formatName: 'is.date.format.short')}"
                        ]
                    }
                }
            }
        }

        if (Sprint.STATE_INPROGRESS == sprint.state) {
            def nbDays = sprint.endDate - lastDaycliche
            nbDays.times {
                if ((ServicesUtils.isDateWeekend(lastDaycliche + (it + 1)) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche + (it + 1))) {
                    values << [
                            storiesDone: null,
                            stories: null,
                            pointsDone: null,
                            totalPoints: null,
                            label: "${g.formatDate(date: lastDaycliche + (it + 1), formatName: 'is.date.format.short')}"
                    ]
                }
            }
        }

        return values
    }

    // TODO check rights
    def copyRecurrentTasksFromPreviousSprint(Sprint sprint) {
        if (sprint.orderNumber == 1 && sprint.parentRelease.orderNumber == 1) {
            throw new IllegalStateException('is.sprint.copyRecurrentTasks.error.no.sprint.before')
        }
        if (sprint.state == Sprint.STATE_DONE) {
            throw new IllegalStateException('is.sprint.copyRecurrentTasks.error.sprint.done')
        }
        def previousSprint = sprint.previousSprint
        def tasks = previousSprint.tasks.findAll { it.type == Task.TYPE_RECURRENT }
        if (!tasks) {
            throw new IllegalStateException('is.sprint.copyRecurrentTasks.error.no.recurrent.tasks')
        }
        def copiedTasks = []
        tasks.each { it ->
            def tmp = new Task()
            tmp.properties = it.properties
            tmp.creationDate = new Date()
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

    @Transactional(readOnly = true)
    def unMarshall(def sprint, Product p = null) {
        try {
            def activationDate = null
            if (sprint.activationDate?.text() && sprint.activationDate?.text() != "")
                activationDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.activationDate.text()) ?: null
            if (!activationDate && sprint.state.text().toInteger() >= Sprint.STATE_INPROGRESS) {
                activationDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.startDate.text())
            }
            def closeDate = null
            if (sprint.closeDate?.text() && sprint.closeDate?.text() != "")
                closeDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.closeDate.text()) ?: null
            if (!closeDate && sprint.state.text().toInteger() == Sprint.STATE_INPROGRESS) {
                closeDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.endDate.text())
            }
            def s = new Sprint(
                    retrospective: sprint.retrospective.text(),
                    doneDefinition: sprint.doneDefinition.text(),
                    activationDate: activationDate,
                    closeDate: closeDate,
                    state: sprint.state.text().toInteger(),
                    velocity: (sprint.velocity.text().isNumber()) ? sprint.velocity.text().toDouble() : 0d,
                    dailyWorkTime: (sprint.dailyWorkTime.text().isNumber()) ? sprint.dailyWorkTime.text().toDouble() : 8d,
                    capacity: (sprint.capacity.text().isNumber()) ? sprint.capacity.text().toDouble() : 0d,
                    dateCreated: sprint.dateCreated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.dateCreated.text()) : new Date(),
                    lastUpdated: sprint.lastUpdated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.lastUpdated.text()) : new Date(),
                    startDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.startDate.text()),
                    endDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(sprint.endDate.text()),
                    orderNumber: sprint.orderNumber.text().toInteger(),
                    description: sprint.description.text() ?: '',
                    goal: sprint.goal?.text() ?: '',
                    deliveredVersion: sprint.deliveredVersion?.text() ?: '',
                    initialRemainingTime: sprint.initialRemainingTime?.text()?.isNumber() ? sprint.initialRemainingTime.text().toFloat() : sprint.initialRemainingHours?.text()?.isNumber() ? sprint.initialRemainingHours.text().toFloat() : null
            )

            sprint.cliches.cliche.each {
                def c = clicheService.unMarshall(it)
                ((TimeBox) s).addToCliches(c)
            }
            if (p) {
                sprint.stories.story.each {
                    storyService.unMarshall(it, p, s)
                }
                sprint.tasks.task.each {
                    def t = taskService.unMarshall(it, p)
                    s.addToTasks(t)
                }
            }
            return s
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }
}