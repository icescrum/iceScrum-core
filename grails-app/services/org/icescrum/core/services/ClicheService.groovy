/*
 * Copyright (c) 2016 Kagilum SAS
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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */


package org.icescrum.core.services

import grails.transaction.Transactional
import groovy.xml.StreamingMarkupBuilder
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport

@Transactional
class ClicheService {

    private static void save(Cliche cliche, TimeBox timeBox) {
        timeBox.addToCliches(cliche)
        cliche.save()
    }

    /**
     * Closure analysing effort from a PBI list and returning a map containing the data retrieved
     * @param pbis The list of pbis to analyse
     * @param inLoop A optional closure, to process additional instructions during the pbi analysing loop
     * (the current pbi is passed in argument of the closure each time). If not null, the closure is called as the
     * first instruction of the loop.
     * @return A map containing the result of the analyse.
     * Index availables in the map : compteurUS, compteurDefect, compteurTechnical,
     * compteurUSFinish, compteurDefectFinish, compteurTechnicalFinish
     */
    def computeDataOnType = { stories, Closure inLoop = null ->
        def cUS = 0
        def cDefect = 0
        def cTechnical = 0
        def cUSDone = 0
        def cDefectDone = 0
        def cTechnicalDone = 0
        stories.each { story ->
            inLoop?.call(story)
            if (story.effort > 0) {
                switch (story.type) {
                    case Story.TYPE_USER_STORY:
                        cUS += story.effort
                        if (story.state == Story.STATE_DONE)
                            cUSDone += story.effort
                        break
                    case Story.TYPE_DEFECT:
                        cDefect += story.effort
                        if (story.state == Story.STATE_DONE)
                            cDefectDone += story.effort
                        break
                    case Story.TYPE_TECHNICAL_STORY:
                        cTechnical += story.effort
                        if (story.state == Story.STATE_DONE)
                            cTechnicalDone += story.effort
                        break
                    default:
                        break
                }
            }
        }
        [
                compteurUS             : cUS,
                compteurDefect         : cDefect,
                compteurTechnical      : cTechnical,
                compteurUSFinish       : cUSDone,
                compteurDefectFinish   : cDefectDone,
                compteurTechnicalFinish: cTechnicalDone
        ]
    }


    void createSprintCliche(Sprint s, Date d, int type) {
        Cliche c = new Cliche(
                type: type,
                datePrise: d,
                data: generateSprintClicheData(s, type)
        )
        save(c, s.parentRelease)
    }

    def generateSprintClicheData(Sprint sprint, int clicheType) {
        // Retrieve the current release and the current sprint
        Release release = sprint.parentRelease
        Project project = release.parentProject
        // Browse the stories and add their estimated velocity to the corresponding counter
        def currentSprintData = computeDataOnType(sprint.stories)
        // Retrieve all the stories of the release
        List<Story> allItemsInRelease = Story.storiesByRelease(release).list()
        def allItemsReleaseData = computeDataOnType(allItemsInRelease)
        // Product Backlog points + Remaining project points
        def allItemsProjectData = computeDataOnType(project.stories)
        // Stories by state
        int doneCount = 0
        int inprogressCount = 0
        int plannedCount = 0
        int estimatedCount = 0
        int acceptedCount = 0
        int suggestedCount = 0
        project.stories.each { story ->
            switch (story.state) {
                case Story.STATE_DONE:
                    doneCount++
                    break
                case Story.STATE_INPROGRESS:
                    inprogressCount++
                    break
                case Story.STATE_PLANNED:
                    plannedCount++
                    break
                case Story.STATE_ESTIMATED:
                    estimatedCount++
                    break
                case Story.STATE_ACCEPTED:
                    acceptedCount++
                    break
                case Story.STATE_SUGGESTED:
                    suggestedCount++
                    break
                default:
                    break
            }
        }
        def clicheData = {
            cliche {
                "${Cliche.SPRINT_ID}"("R${release.orderNumber}S${sprint.orderNumber}")
                if (clicheType == Cliche.TYPE_ACTIVATION) {
                    // Activation Date
                    "${Cliche.INPROGRESS_DATE}"(sprint.inProgressDate)
                    // Capacity
                    "${Cliche.SPRINT_CAPACITY}"(currentSprintData['compteurUS'] + currentSprintData['compteurTechnical'] + currentSprintData['compteurDefect'])
                    "${Cliche.FUNCTIONAL_STORY_CAPACITY}"(currentSprintData['compteurUS'])
                    "${Cliche.TECHNICAL_STORY_CAPACITY}"(currentSprintData['compteurTechnical'])
                    "${Cliche.DEFECT_STORY_CAPACITY}"(currentSprintData['compteurDefect'])
                }
                if (clicheType == Cliche.TYPE_CLOSE) {
                    // Close Date
                    "${Cliche.DONE_DATE}"(sprint.doneDate)
                    // Capacity
                    "${Cliche.SPRINT_VELOCITY}"(currentSprintData['compteurUS'] + currentSprintData['compteurTechnical'] + currentSprintData['compteurDefect'])
                    "${Cliche.FUNCTIONAL_STORY_VELOCITY}"(currentSprintData['compteurUS'])
                    "${Cliche.TECHNICAL_STORY_VELOCITY}"(currentSprintData['compteurTechnical'])
                    "${Cliche.DEFECT_STORY_VELOCITY}"(currentSprintData['compteurDefect'])
                }
                // Project points
                "${Cliche.FUNCTIONAL_STORY_PROJECT_POINTS}"(allItemsProjectData['compteurUS'])
                "${Cliche.TECHNICAL_STORY_PROJECT_POINTS}"(allItemsProjectData['compteurTechnical'])
                "${Cliche.DEFECT_STORY_PROJECT_POINTS}"(allItemsProjectData['compteurDefect'])
                "${Cliche.PROJECT_POINTS}"(allItemsProjectData['compteurUS'] + allItemsProjectData['compteurTechnical'] + allItemsProjectData['compteurDefect'])
                // Remaining backlog points
                def srp = allItemsProjectData['compteurUS'] - allItemsProjectData['compteurUSFinish']
                def trp = allItemsProjectData['compteurTechnical'] - allItemsProjectData['compteurTechnicalFinish']
                def drp = allItemsProjectData['compteurDefect'] - allItemsProjectData['compteurDefectFinish']
                "${Cliche.FUNCTIONAL_STORY_PROJECT_REMAINING_POINTS}"(srp)
                "${Cliche.TECHNICAL_STORY_PROJECT_REMAINING_POINTS}"(trp)
                "${Cliche.DEFECT_STORY_PROJECT_REMAINING_POINTS}"(drp)
                "${Cliche.PROJECT_REMAINING_POINTS}"(srp + trp + drp)
                // Release remaining points
                "${Cliche.FUNCTIONAL_STORY_RELEASE_REMAINING_POINTS}"(allItemsReleaseData['compteurUS'] - allItemsReleaseData['compteurUSFinish'])
                "${Cliche.TECHNICAL_STORY_RELEASE_REMAINING_POINTS}"(allItemsReleaseData['compteurTechnical'] - allItemsReleaseData['compteurTechnicalFinish'])
                "${Cliche.DEFECT_STORY_RELEASE_REMAINING_POINTS}"(allItemsReleaseData['compteurDefect'] - allItemsReleaseData['compteurDefectFinish'])
                // Stories points by states
                "${Cliche.FINISHED_STORIES}"(doneCount)
                "${Cliche.INPROGRESS_STORIES}"(inprogressCount)
                "${Cliche.PLANNED_STORIES}"(plannedCount)
                "${Cliche.ESTIMATED_STORIES}"(estimatedCount)
                "${Cliche.ACCEPTED_STORIES}"(acceptedCount)
                "${Cliche.SUGGESTED_STORIES}"(suggestedCount)
            }
        }
        StreamingMarkupBuilder xmlBuilder = new StreamingMarkupBuilder()
        return xmlBuilder.bind(clicheData).toString()
    }

    void createOrUpdateDailyTasksCliche(Sprint sprint) {
        if (sprint.state == Sprint.STATE_WAIT || sprint.state == Sprint.STATE_DONE) {
            return
        }
        int doneCount = 0
        int inprogressCount = 0
        int waitCount = 0
        int recurrentCount = 0
        int urgentCount = 0
        int storyCount = 0
        float remainingTime = 0
        sprint.tasks.each { task ->
            switch (task.state) {
                case Task.STATE_DONE:
                    doneCount++
                    break
                case Task.STATE_BUSY:
                    inprogressCount++
                    break
                case Task.STATE_WAIT:
                    waitCount++
                    break
                default:
                    break
            }
            switch (task.type) {
                case Task.TYPE_RECURRENT:
                    recurrentCount++
                    break
                case Task.TYPE_URGENT:
                    urgentCount++
                    break
                default:
                    storyCount++
                    break
            }
            remainingTime += task.estimation ?: 0
        }
        int storiesDoneCount = 0
        int storiesInProgressCount = 0
        def totalPointsStories = 0
        def pointsDoneStories = 0
        sprint.stories.each { story ->
            switch (story.state) {
                case Story.STATE_DONE:
                    storiesDoneCount++
                    pointsDoneStories += story.effort
                    break
                case Story.STATE_INPROGRESS:
                    storiesInProgressCount++
                    break
            }
            totalPointsStories += story.effort
        }
        def clicheData = {
            cliche {
                //Total stories
                "${Cliche.TOTAL_STORIES}"(storiesDoneCount + storiesInProgressCount)
                //Stories by state
                "${Cliche.STORIES_INPROGRESS}"(storiesInProgressCount)
                "${Cliche.STORIES_DONE}"(storiesDoneCount)
                //Points
                "${Cliche.STORIES_TOTAL_POINTS}"(totalPointsStories)
                "${Cliche.STORIES_POINTS_DONE}"(pointsDoneStories)
                //Total tasks
                "${Cliche.TOTAL_TASKS}"(waitCount + inprogressCount + doneCount)
                // Tasks by states
                "${Cliche.TASKS_WAIT}"(waitCount)
                "${Cliche.TASKS_INPROGRESS}"(inprogressCount)
                "${Cliche.TASKS_DONE}"(doneCount)
                // Tasks by type
                "${Cliche.TASKS_SPRINT}"(recurrentCount + urgentCount)
                "${Cliche.TASKS_RECURRENT}"(recurrentCount)
                "${Cliche.TASKS_URGENT}"(urgentCount)
                "${Cliche.TASKS_STORY}"(storyCount)
                //daily remainingTime
                "${Cliche.REMAINING_TIME}"(remainingTime)
            }
        }
        StreamingMarkupBuilder xmlBuilder = new StreamingMarkupBuilder()
        def today = new Date()
        def lastCliche = sprint.cliches?.size() ? sprint.cliches.asList().sort { it.datePrise }.last() : null
        if (lastCliche) {
            def days = today - lastCliche.datePrise
            if (days < 1) {
                def data = xmlBuilder.bind(clicheData).toString()
                if (data.encodeAsMD5() != lastCliche.data.encodeAsMD5()) {
                    sprint.lastUpdated = new Date()
                    sprint.save()
                    lastCliche.data = data
                    lastCliche.save()
                }
                return
            } else {
                for (def i = 1; i < days; i++) {
                    Cliche cliche = new Cliche(type: Cliche.TYPE_DAILY, datePrise: lastCliche.datePrise + i, data: lastCliche.data)
                    save(cliche, sprint)
                }
            }
        }
        Cliche cliche = new Cliche(type: Cliche.TYPE_DAILY, datePrise: today, data: xmlBuilder.bind(clicheData).toString())
        save(cliche, sprint)
    }

    def unMarshall(def clicheXml, def options) {
        def timebox = options.timebox
        Cliche.withTransaction(readOnly: !options.save) { transaction ->
            def cliche = new Cliche(
                    type: clicheXml.type.text().toInteger(),
                    datePrise: ApplicationSupport.parseDate(clicheXml.datePrise.text()),
                    data: clicheXml.data.text()
            )
            if (timebox) {
                timebox.addToCliches(cliche)
            }
            if (options.save) {
                cliche.save()
            }
            return (Cliche) importDomainsPlugins(clicheXml, cliche, options)
        }
    }
}
