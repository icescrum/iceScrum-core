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

import java.text.SimpleDateFormat
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumSprintEvent
import org.icescrum.core.event.IceScrumStoryEvent
import org.icescrum.core.utils.ServicesUtils
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport

class SprintService {

    static transactional = true

    /**
     * Declarations
     */
    def clicheService
    def taskService
    def storyService
    def springSecurityService
    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void save(Sprint sprint, Release release) {
        if (release.state == Release.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.release.done')

        sprint.orderNumber = (release.sprints?.size() ?: 0) + 1

        release.addToSprints(sprint)

        if (!sprint.validate())
            throw new RuntimeException()

        if (!sprint.save())
            throw new RuntimeException()
        broadcast(function: 'add', message: sprint, channel:'product-'+sprint.parentProduct.id)
    }

    /**
     * Update a sprint
     * @param sprint
     * @param uCurrent
     */
    void update(Sprint sprint, Date startDate, Date endDate, def checkIntegrity = true, boolean updateRelease = true) {

        Date oldStartDate = sprint.startDate
        Date oldEndDate = sprint.endDate

        if (!sprint.validate() && updateRelease)
            throw new RuntimeException()

        if (checkIntegrity) {
            // A done sprint cannot be modified
            if (sprint.state == Sprint.STATE_DONE){
                throw new IllegalStateException('is.sprint.error.update.done')
            }

            // If the sprint is in INPROGRESS state, cannot change the startDate
            if (sprint.state == Sprint.STATE_INPROGRESS) {
                if (sprint.startDate != startDate) {
                    throw new IllegalStateException('is.sprint.error.update.startdate.inprogress')
                }
            }
        }

        def nextSprint = sprint.parentRelease.sprints?.find { it.orderNumber == sprint.orderNumber + 1}
        // If the end date has changed and overlap the next sprint start date,
        // the sprints coming after are shifted
        if (sprint.endDate != endDate && nextSprint && endDate >= nextSprint.startDate) {
            def deltaDays = (endDate - nextSprint.startDate) + 1
            if (nextSprint) {
                if (nextSprint.endDate + deltaDays <= sprint.parentRelease.endDate) {
                    update(nextSprint, nextSprint.startDate + deltaDays, nextSprint.endDate + deltaDays, false)
                } else {
                    // If we have reached the release end date, we try to reduce de the sprint's duration if possible
                    // if not, the sprint is deleted and the stories that were planned are dissociated and return in the backlog
                    if (nextSprint.startDate + deltaDays >= sprint.parentRelease.endDate) {
                        delete(nextSprint)
                        // The delete method should automatically dissociate and delete the following sprints, so we can
                        // break out the loop
                    } else {
                        update(nextSprint, nextSprint.startDate + deltaDays, sprint.parentRelease.endDate, false, updateRelease)
                    }
                }
            }
        }

        sprint.startDate = startDate
        sprint.endDate = endDate

        if (updateRelease) {
            sprint.parentRelease.lastUpdated = new Date()
            if (!sprint.parentRelease.save(flush: true)) {
                throw new RuntimeException()
            }
        } else {
            sprint.save()
        }

        publishEvent(new IceScrumSprintEvent(sprint, oldStartDate, oldEndDate, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
        broadcast(function: 'update', message: sprint, channel:'product-'+sprint.parentProduct.id)
    }

    /**
     * Delete a sprint from the database
     * All its stories are dissociated before actual deletion.
     * @param sprint
     */
    def delete(Sprint sprint) {
        // Cannot delete a INPROGRESS or DONE sprint
        if (sprint.state >= Sprint.STATE_INPROGRESS)
            throw new IllegalStateException('is.sprint.error.delete.inprogress')

        sprint.removeAllAttachments()

        def release = sprint.parentRelease
        def nextSprints = release.sprints.findAll { it.orderNumber > sprint.orderNumber }

        if (nextSprints) {
            storyService.unPlanAll(nextSprints) // Every sprints coming after this one in the release are also deleted!
        }
        storyService.unPlanAll([sprint])

        def deletedSprints = []
        deletedSprints << [class: sprint.class, id: sprint.id, startDate: sprint.startDate, orderNumber: sprint.orderNumber, parentRelease: [id: release.id, class: release.class]]
        release.removeFromSprints(sprint)
        nextSprints.each {
            deletedSprints << [class: it.class, id: it.id, startDate: sprint.startDate, orderNumber: sprint.orderNumber, parentRelease: [id: release.id, class: release.class]]
            it.removeAllAttachments()
            release.removeFromSprints(it)
        }

        broadcast(function: 'delete', message: [class: sprint.class, sprints: deletedSprints], channel:'product-'+release.parentProduct.id)
        return deletedSprints
    }


    def generateSprints(Release release, Date startDate = null) {
        if (release.state == Release.STATE_DONE)
            throw new IllegalStateException('is.sprint.error.release.done')

        int daysBySprint = release.parentProduct.preferences.estimatedSprintsDuration
        Date firstDate
        Date lastDate = release.endDate
        int nbSprint = 0
        long day = (24 * 60 * 60 * 1000)

        firstDate = startDate ?: new Date(release.startDate.time)
        if (release.sprints?.size() >= 1) {
            // Search for the last sprint end date
            release.sprints.each { s ->
                if (s.endDate.after(firstDate))
                    firstDate.time = s.endDate.time
                if (s.orderNumber > nbSprint)
                    nbSprint = s.orderNumber
            }
            firstDate.time += day
        }

        Date lastDateMidnight = ApplicationSupport.getMidnightTime(lastDate)
        Date firstDateMidnight = ApplicationSupport.getMidnightTime(firstDate)
        int totalDays = (int) ((lastDateMidnight.time - firstDateMidnight.time) / day) + 1
        int nbSprints = Math.floor(totalDays / daysBySprint)
        if(nbSprints == 0) {
            throw new IllegalStateException('is.release.sprints.not.enough.time')
        }

        def sprints = []
        for (int i = 0; i < nbSprints; i++) {
            Date endDate = new Date(firstDate.time + (day * (daysBySprint - 1)))

            def newSprint = new Sprint(
                    orderNumber: (++nbSprint),
                    goal: 'Generated Sprint',
                    startDate: (Date) firstDate.clone(),
                    endDate: endDate
            )

            release.addToSprints(newSprint)

            if (!newSprint.save())
                throw new RuntimeException()

            firstDate.time = endDate.time + day
            sprints << newSprint
        }

        broadcast(function: 'add', message: [class: Sprint.class, sprints: sprints], channel:'product-'+release.parentProduct.id)
        return sprints
    }

    /**
     * Sprint activation
     * @param _sprint
     * @param uCurrent
     * @param pb
     */
    void activate(Sprint sprint) {
        // Release of the sprint is not the activated release
        if (sprint.parentRelease.state != Release.STATE_INPROGRESS)
            throw new IllegalStateException('is.sprint.error.activate.release.not.inprogress')

        // If there is a sprint opened before, throw an workflow error
        int lastSprintClosed = -1
        Sprint s = sprint.parentRelease.sprints.find {
            if (it.state == Sprint.STATE_DONE)
                lastSprintClosed = it.orderNumber
            it.state == Sprint.STATE_INPROGRESS
        }
        if (s)
            throw new IllegalStateException('is.sprint.error.activate.other.inprogress')

        // There is (in the release) sprints before 'sprint' which are not closed
        if (sprint.orderNumber != 1 && sprint.orderNumber > lastSprintClosed + 1)
            throw new IllegalStateException('is.sprint.error.activate.previous.not.closed')

        def autoCreateTaskOnEmptyStory = sprint.parentRelease.parentProduct.preferences.autoCreateTaskOnEmptyStory

        sprint.stories?.each {
            it.state = Story.STATE_INPROGRESS
            it.inProgressDate = new Date()
            if (autoCreateTaskOnEmptyStory && it.tasks?.size() == 0) {
                def emptyTask = new Task(name: it.name, state: Task.STATE_WAIT, description: it.description, backlog: sprint)
                taskService.saveStoryTask(emptyTask, it, (User) springSecurityService.currentUser)
            }
            it.save()
        }

        sprint.state = Sprint.STATE_INPROGRESS
        sprint.activationDate = new Date()

        sprint.parentRelease.lastUpdated = new Date()
        sprint.parentRelease.parentProduct.lastUpdated = new Date()

        //retrieve last done definition if no done definition in the current sprint
        if (sprint.orderNumber != 1 && !sprint.doneDefinition) {
            def previousSprint = Sprint.findByOrderNumberAndParentRelease(sprint.orderNumber - 1, sprint.parentRelease)
            if (previousSprint.doneDefinition) {
                sprint.doneDefinition = previousSprint.doneDefinition
            }
        }

        sprint.initialRemainingHours = sprint.totalRemainingHours

        if (!sprint.save()) {
            throw new RuntimeException()
        }

        clicheService.createSprintCliche(sprint, new Date(), Cliche.TYPE_ACTIVATION)
        clicheService.createOrUpdateDailyTasksCliche(sprint)

        bufferBroadcast(channel:'product-'+sprint.parentProduct.id)
        sprint.stories.each {
            broadcast(function: 'inProgress', message: it, channel:'product-'+sprint.parentProduct.id)
            publishEvent(new IceScrumStoryEvent(it, this.class, (User) springSecurityService.currentUser, IceScrumStoryEvent.EVENT_INPROGRESS))
        }
        resumeBufferedBroadcast(channel:'product-'+sprint.parentProduct.id)

        publishEvent(new IceScrumSprintEvent(sprint, this.class, (User) springSecurityService.currentUser, IceScrumSprintEvent.EVENT_ACTIVATED))
        broadcast(function: 'activate', message: sprint, channel:'product-'+sprint.parentProduct.id)
    }

    void close(Sprint sprint) {
        // The sprint must be in the state "INPROGRESS"
        if (sprint.state != Sprint.STATE_INPROGRESS)
            throw new IllegalStateException('is.sprint.error.close.not.inprogress')

        Double sum = (Double) sprint.stories?.sum { pbi ->
            if (pbi.state == Story.STATE_DONE)
                pbi.effort.doubleValue()
            else
                0
        } ?: 0
        bufferBroadcast(channel:'product-'+sprint.parentProduct.id)
        def nextSprint = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.orderNumber + 1) ?: Sprint.findByParentReleaseAndOrderNumber(Release.findByOrderNumberAndParentProduct(sprint.parentRelease.orderNumber + 1, sprint.parentProduct), 1)
        if (nextSprint) {
            //Move not finished urgent task to next sprint
            sprint.tasks?.findAll {it.type == Task.TYPE_URGENT && it.state != Task.STATE_DONE}?.each {
                it.backlog = nextSprint
                it.state = Task.STATE_WAIT
                it.inProgressDate = null
                if (!it.save()) {
                    throw new RuntimeException()
                }
                broadcast(function: 'update', message: it, channel:'product-'+sprint.parentProduct.id)
            }
            storyService.plan(nextSprint, sprint.stories.findAll {it.state != Story.STATE_DONE})
        } else {
            storyService.unPlanAll([sprint])
        }

        sprint.velocity = sum
        sprint.state = Sprint.STATE_DONE
        sprint.closeDate = new Date()

        sprint.parentRelease.lastUpdated = new Date()
        sprint.parentRelease.parentProduct.lastUpdated = new Date()

        sprint.tasks?.findAll {it.type == Task.TYPE_URGENT || it.type == Task.TYPE_RECURRENT}?.each {
            it.lastUpdated = new Date()
        }

        if (!sprint.save(flush: true)) {
            throw new RuntimeException()
        }

        clicheService.createSprintCliche(sprint.refresh(), new Date(), Cliche.TYPE_CLOSE)
        clicheService.createOrUpdateDailyTasksCliche(sprint)

        broadcast(function: 'close', message: sprint, channel:'product-'+sprint.parentProduct.id)
        resumeBufferedBroadcast(channel:'product-'+sprint.parentProduct.id)
        publishEvent(new IceScrumSprintEvent(sprint, this.class, (User) springSecurityService.currentUser, IceScrumSprintEvent.EVENT_CLOSED))

    }

    void updateDoneDefinition(Sprint sprint) {
        if (!sprint.save()) {
            throw new RuntimeException()
        }
        publishEvent(new IceScrumSprintEvent(sprint, this.class, (User) springSecurityService.currentUser, IceScrumSprintEvent.EVENT_UPDATED_DONE_DEFINITION))
        broadcast(function: 'doneDefinition', message: sprint, channel:'product-'+sprint.parentProduct.id)
    }

    void updateRetrospective(Sprint sprint) {
        if (!sprint.save()) {
            throw new RuntimeException()
        }
        publishEvent(new IceScrumSprintEvent(sprint, this.class, (User) springSecurityService.currentUser, IceScrumSprintEvent.EVENT_UPDATED_RETROSPECTIVE))
        broadcast(function: 'retrospective', message: sprint, channel:'product-'+sprint.parentProduct.id)
    }

    def sprintBurndownHoursValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.activationDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.closeDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate
        clicheService.createOrUpdateDailyTasksCliche(sprint)

        sprint.cliches?.sort {a, b -> a.datePrise <=> b.datePrise}?.eachWithIndex {cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    def currentRemaining = xmlRoot."${Cliche.REMAINING_HOURS}".toFloat()
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche))
                        values << [
                                remainingHours: currentRemaining,
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
                            remainingHours: null,
                            label: "${g.formatDate(date: lastDaycliche + (it + 1), formatName: 'is.date.format.short')}"
                    ]
            }
        }
        if (!values.isEmpty()) {
            values.first()?.idealHours = sprint.initialRemainingHours?:0
            values.last()?.idealHours = 0
        }
        return values
    }

    def sprintBurnupTasksValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.activationDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.closeDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate

        clicheService.createOrUpdateDailyTasksCliche(sprint)

        sprint.cliches?.sort {a, b -> a.datePrise <=> b.datePrise}?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche))
                        values << [
                                tasksDone: xmlRoot."${Cliche.TASKS_DONE}".toInteger(),
                                tasks: xmlRoot."${Cliche.TOTAL_TASKS}".toInteger(),
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
                            tasksDone: null,
                            tasks: null,
                            label: "${g.formatDate(date: lastDaycliche + (it + 1), formatName: 'is.date.format.short')}"
                    ]
            }
        }
        return values
    }

    def sprintBurnupStoriesValues(Sprint sprint) {
        def values = []
        def lastDaycliche = sprint.activationDate
        def date = (sprint.state == Sprint.STATE_DONE) ? sprint.closeDate : (sprint.state == Sprint.STATE_INPROGRESS) ? new Date() : sprint.endDate

        clicheService.createOrUpdateDailyTasksCliche(sprint)

        sprint.cliches?.sort {a, b -> a.datePrise <=> b.datePrise}?.eachWithIndex { cliche, index ->
            if (cliche.datePrise <= date) {
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    lastDaycliche = cliche.datePrise
                    if ((ServicesUtils.isDateWeekend(lastDaycliche) && !sprint.parentRelease.parentProduct.preferences.hideWeekend) || !ServicesUtils.isDateWeekend(lastDaycliche))
                        values << [
                                storiesDone: xmlRoot."${Cliche.STORIES_DONE}".toInteger(),
                                stories: xmlRoot."${Cliche.TOTAL_STORIES}".toInteger(),
                                pointsDone: xmlRoot."${Cliche.STORIES_POINTS_DONE}"?.toString()?.isInteger() ? xmlRoot."${Cliche.STORIES_POINTS_DONE}".toInteger() :0,
                                totalPoints: xmlRoot."${Cliche.STORIES_TOTAL_POINTS}"?.toString()?.isInteger() ? xmlRoot."${Cliche.STORIES_TOTAL_POINTS}".toInteger() :0,
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
                            storiesDone: null,
                            stories: null,
                            pointsDone: null,
                            totalPoints: null,
                            label: "${g.formatDate(date: lastDaycliche + (it + 1), formatName: 'is.date.format.short')}"
                    ]
            }
        }

        return values
    }

    def copyRecurrentTasksFromPreviousSprint(Sprint sprint) {
        if (sprint.orderNumber == 1 && sprint.parentRelease.orderNumber == 1) {
            throw new IllegalStateException('is.sprint.copyRecurrentTasks.error.no.sprint.before')
        }
        if (sprint.state == Sprint.STATE_DONE) {
            throw new IllegalStateException('is.sprint.copyRecurrentTasks.error.sprint.done')
        }
        def lastsprint
        if (sprint.orderNumber > 1){
            lastsprint = Sprint.findByParentReleaseAndOrderNumber(sprint.parentRelease, sprint.orderNumber - 1)
        }else{
            def previousRelease = Release.findByOrderNumber(sprint.parentRelease.orderNumber - 1)
            lastsprint = Sprint.findByParentReleaseAndOrderNumber(previousRelease, previousRelease.sprints.size())
        }
        def tasks = lastsprint.tasks.findAll {it.type == Task.TYPE_RECURRENT}

        if (!tasks) {
            throw new IllegalStateException('is.sprint.copyRecurrentTasks.error.no.recurrent.tasks')
        }

        def copiedTasks = []
        tasks.each {it ->
            def tmp = new Task()
            tmp.properties = it.properties
            tmp.creationDate = new Date()
            tmp.state = Task.STATE_WAIT
            tmp.backlog = sprint
            tmp.responsible = null
            tmp.participants = null
            tmp.inProgressDate = null
            tmp.doneDate = null
            taskService.save(tmp, sprint, it.creator)
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