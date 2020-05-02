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
import org.icescrum.core.utils.DateUtils

@Transactional
class ClicheService {

    def grailsApplication

    private static void save(Cliche cliche, TimeBox timeBox) {
        timeBox.addToCliches(cliche)
        cliche.save()
    }

    private Map getPointsByType(stories) {
        int allTotal = 0
        int remainingTotal = 0
        def storyTypes = grailsApplication.config.icescrum.resourceBundles.storyTypes.keySet()
        def allPointsByType = storyTypes.collectEntries { storyType -> [(storyType): 0] }
        def remainingPointsByType = storyTypes.collectEntries { storyType -> [(storyType): 0] }
        stories.each { Story story ->
            if (story.effort > 0) {
                allPointsByType[story.type] += story.effort
                allTotal += story.effort
                if (story.state != Story.STATE_DONE) {
                    remainingPointsByType[story.type] += story.effort
                    remainingTotal += story.effort
                }
            }
        }
        return [all: allPointsByType, remaining: remainingPointsByType, allTotal: allTotal, remainingTotal: remainingTotal]
    }

    void createSprintCliche(Sprint s, Date d, int type) {
        Cliche c = new Cliche(
                type: type,
                datePrise: d,
                data: generateSprintClicheData(s, type)
        )
        save(c, s.parentRelease)
    }

    void removeLastSprintCliche(Sprint s) {
        def cliche = Cliche.findByTypeAndParentTimeBox(Cliche.TYPE_CLOSE, s.parentRelease, [sort: "datePrise", order: "desc"])
        if (cliche) {
            s.parentRelease.removeFromCliches(cliche)
            cliche.delete()
        }
    }

    def generateSprintClicheData(Sprint sprint, int clicheType) {
        def storyTypes = grailsApplication.config.icescrum.resourceBundles.storyTypes.keySet()
        // Retrieve the current release and the current sprint
        Release release = sprint.parentRelease
        Project project = release.parentProject
        // Browse the stories and add their estimated velocity to the corresponding counter
        def sprintPoints = getPointsByType(sprint.stories)
        def releasePoints = getPointsByType(Story.storiesByRelease(release).list())
        def projectPoints = getPointsByType(project.stories)
        // Stories by state
        def storyStates = [Story.STATE_SUGGESTED, Story.STATE_ACCEPTED, Story.STATE_ESTIMATED, Story.STATE_PLANNED, Story.STATE_INPROGRESS, Story.STATE_DONE]
        def countByState = storyStates.collectEntries { storyState -> [(storyState): 0] }
        project.stories.each { story ->
            if (story.state in storyStates) {
                countByState[story.state]++
            }
        }
        // Data
        def clicheData = {
            cliche {
                "${Cliche.SPRINT_ID}"("R${release.orderNumber}S${sprint.orderNumber}")
                if (clicheType == Cliche.TYPE_ACTIVATION) {
                    "${Cliche.INPROGRESS_DATE}"(sprint.inProgressDate) // TODO NOT USED
                    "${Cliche.SPRINT_CAPACITY}"(sprintPoints.allTotal)
                    "${Cliche.FUNCTIONAL_STORY_CAPACITY}"(sprintPoints.all[Story.TYPE_USER_STORY]) // TODO NOT USED
                    "${Cliche.TECHNICAL_STORY_CAPACITY}"(sprintPoints.all[Story.TYPE_TECHNICAL_STORY]) // TODO NOT USED
                    "${Cliche.DEFECT_STORY_CAPACITY}"(sprintPoints.all[Story.TYPE_DEFECT]) // TODO NOT USED
                }
                if (clicheType == Cliche.TYPE_CLOSE) {
                    "${Cliche.DONE_DATE}"(sprint.doneDate) // TODO NOT USED
                    "${Cliche.SPRINT_VELOCITY}"(sprintPoints.allTotal)
                    "${Cliche.FUNCTIONAL_STORY_VELOCITY}"(sprintPoints.all[Story.TYPE_USER_STORY])
                    "${Cliche.TECHNICAL_STORY_VELOCITY}"(sprintPoints.all[Story.TYPE_TECHNICAL_STORY])
                    "${Cliche.DEFECT_STORY_VELOCITY}"(sprintPoints.all[Story.TYPE_DEFECT])
                }
                // Project points
                "${Cliche.FUNCTIONAL_STORY_PROJECT_POINTS}"(projectPoints.all[Story.TYPE_USER_STORY]) // TODO NOT USED
                "${Cliche.TECHNICAL_STORY_PROJECT_POINTS}"(projectPoints.all[Story.TYPE_TECHNICAL_STORY]) // TODO NOT USED
                "${Cliche.DEFECT_STORY_PROJECT_POINTS}"(projectPoints.all[Story.TYPE_DEFECT]) // TODO NOT USED
                "${Cliche.PROJECT_POINTS}"(projectPoints.allTotal)
                // Project remaining points
                storyTypes.each { storyType ->
                    "${grailsApplication.config.icescrum.resourceBundles.storyTypesCliche[storyType]}"(projectPoints.remaining[storyType])
                }
                "${Cliche.PROJECT_REMAINING_POINTS}"(projectPoints.remainingTotal)
                // Release remaining points
                "${Cliche.FUNCTIONAL_STORY_RELEASE_REMAINING_POINTS}"(releasePoints.remaining[Story.TYPE_USER_STORY]) // TODO NOT USED
                "${Cliche.TECHNICAL_STORY_RELEASE_REMAINING_POINTS}"(releasePoints.remaining[Story.TYPE_TECHNICAL_STORY]) // TODO NOT USED
                "${Cliche.DEFECT_STORY_RELEASE_REMAINING_POINTS}"(releasePoints.remaining[Story.TYPE_DEFECT]) // TODO NOT USED
                // Stories points by states
                "${Cliche.FINISHED_STORIES}"(countByState[Story.STATE_DONE])
                "${Cliche.INPROGRESS_STORIES}"(countByState[Story.STATE_INPROGRESS])
                "${Cliche.PLANNED_STORIES}"(countByState[Story.STATE_PLANNED])
                "${Cliche.ESTIMATED_STORIES}"(countByState[Story.STATE_ESTIMATED])
                "${Cliche.ACCEPTED_STORIES}"(countByState[Story.STATE_ACCEPTED])
                "${Cliche.SUGGESTED_STORIES}"(countByState[Story.STATE_SUGGESTED])
            }
        }
        StreamingMarkupBuilder xmlBuilder = new StreamingMarkupBuilder()
        return xmlBuilder.bind(clicheData).toString()
    }

    void createOrUpdateDailyTasksCliche(Sprint sprint) {
        return
        if (sprint.state == Sprint.STATE_WAIT || sprint.state == Sprint.STATE_DONE) {
            return
        }
        def taskStates = [Task.STATE_WAIT, Task.STATE_BUSY, Task.STATE_DONE]
        def tasksByState = taskStates.collectEntries { taskState -> [(taskState): 0] }
        def taskTypes = [null, Task.TYPE_RECURRENT, Task.TYPE_URGENT]
        def tasksByType = taskTypes.collectEntries { taskType -> [(taskType): 0] }
        float spentTime = 0
        float remainingTime = 0
        sprint.tasks.each { task ->
            tasksByState[task.state]++
            tasksByType[task.type]++
            spentTime += task.spent ?: 0
            remainingTime += task.estimation ?: 0
        }
        int storiesDoneCount = 0
        int storiesInProgressCount = 0
        int storiesInReviewCount = 0
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
                case Story.STATE_INREVIEW:
                    storiesInReviewCount++
                    break
            }
            totalPointsStories += story.effort
        }
        def clicheData = {
            cliche {
                "${Cliche.TOTAL_STORIES}"(sprint.stories.size())
                "${Cliche.STORIES_INPROGRESS}"(storiesInProgressCount) // TODO NOT USED
                "${Cliche.STORIES_INREVIEW}"(storiesInReviewCount) // TODO NOT USED
                "${Cliche.STORIES_DONE}"(storiesDoneCount)
                "${Cliche.STORIES_TOTAL_POINTS}"(totalPointsStories)
                "${Cliche.STORIES_POINTS_DONE}"(pointsDoneStories)
                "${Cliche.TOTAL_TASKS}"(tasksByState[Task.STATE_WAIT] + tasksByState[Task.STATE_BUSY] + tasksByState[Task.STATE_DONE])
                "${Cliche.TASKS_WAIT}"(tasksByState[Task.STATE_WAIT]) // TODO NOT USED
                "${Cliche.TASKS_INPROGRESS}"(tasksByState[Task.STATE_BUSY]) // TODO NOT USED
                "${Cliche.TASKS_DONE}"(tasksByState[Task.STATE_DONE])
                "${Cliche.TASKS_SPRINT}"(tasksByType[Task.TYPE_RECURRENT] + tasksByType[Task.TYPE_URGENT]) // TODO NOT USED
                "${Cliche.TASKS_RECURRENT}"(tasksByType[Task.TYPE_RECURRENT]) // TODO NOT USED
                "${Cliche.TASKS_URGENT}"(tasksByType[Task.TYPE_URGENT]) // TODO NOT USED
                "${Cliche.TASKS_STORY}"(tasksByType[null]) // TODO NOT USED
                "${Cliche.REMAINING_TIME}"(remainingTime)
                "${Cliche.TIME_SPENT}"(spentTime)
            }
        }
        StreamingMarkupBuilder xmlBuilder = new StreamingMarkupBuilder()
        def today = new Date()
        def lastCliche = sprint.cliches?.size() ? sprint.cliches.asList().sort { it.datePrise }.last() : null
        if (lastCliche) {
            def days = today - lastCliche.datePrise
            if (days < 1) {
                // Horrible hack:
                // Cliche data may be written outside of here from plugins
                // That means that if there is already a cliche to update, we need to merge data instead of replacing thus preserving existing data we don't own
                // There is no easy way to merge data with XML nodes so we convert them to maps to merge them, then convert them back to XML through the builder
                def xmlToMap = { String data ->
                    new XmlSlurper().parseText(data).children().collectEntries {
                        [it.name(), it.text()]
                    }
                }
                Map newData = xmlToMap(xmlBuilder.bind(clicheData).toString())
                Map oldData = xmlToMap(lastCliche.data)
                String mergedData = xmlBuilder.bind {
                    cliche {
                        (oldData + newData).each { k, v -> // The "+" operator merges newData into oldData
                            "$k"(v)
                        }
                    }
                }.toString()
                if (mergedData.encodeAsMD5() != lastCliche.data.encodeAsMD5()) {
                    sprint.lastUpdated = new Date()
                    sprint.save()
                    lastCliche.data = mergedData
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
                    datePrise: DateUtils.parseDateFromExport(clicheXml.datePrise.text()),
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
