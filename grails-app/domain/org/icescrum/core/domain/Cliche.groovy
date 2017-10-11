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
 */


package org.icescrum.core.domain

class Cliche implements Serializable {

    static final long serialVersionUID = -5768284779953609803L

    static belongsTo = [parentTimeBox: TimeBox]

    Date datePrise
    String data  // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    int type = Cliche.TYPE_ACTIVATION

    static mapping = {
        cache true
        data type: 'text'
        table 'is_cliche'
        sort:
        datePrise: 'asc'
    }

    static constraints = {
    }

    // ******************
    // Begin XML Sprint cliche Tags names
    // ******************
    static final SPRINT_ID = 'sprintID'

    //TYPE
    static final TYPE_ACTIVATION = 0
    static final TYPE_CLOSE = 1

    //type activate sprint
    // Capacity
    static final SPRINT_CAPACITY = 'sprintCapacity'
    static final FUNCTIONAL_STORY_CAPACITY = 'functionalStoryCapacity'
    static final DEFECT_STORY_CAPACITY = 'defectStoryCapacity'
    static final TECHNICAL_STORY_CAPACITY = 'technicalStoryCapacity'
    static final INPROGRESS_DATE = 'activationDate'

    //type close sprint
    // Velocity
    static final SPRINT_VELOCITY = 'sprintVelocity'
    static final FUNCTIONAL_STORY_VELOCITY = 'functionalStoryVelocity'
    static final DEFECT_STORY_VELOCITY = 'defectStoryVelocity'
    static final TECHNICAL_STORY_VELOCITY = 'technicalStoryVelocity'
    static final DONE_DATE = 'closeDate'

    // Project Total points
    static final PROJECT_POINTS = 'productBacklogPoints'
    static final FUNCTIONAL_STORY_PROJECT_POINTS = 'functionalStoryBacklogPoints'
    static final DEFECT_STORY_PROJECT_POINTS = 'defectStoryBacklogPoints'
    static final TECHNICAL_STORY_PROJECT_POINTS = 'technicalStoryBacklogPoints'
    // Project Remaining Points
    static final PROJECT_REMAINING_POINTS = 'productRemainingBacklogPoints'
    static final FUNCTIONAL_STORY_PROJECT_REMAINING_POINTS = 'functionalStoryProductRemainingPoints'
    static final DEFECT_STORY_PROJECT_REMAINING_POINTS = 'defectStoryProductRemainingPoints'
    static final TECHNICAL_STORY_PROJECT_REMAINING_POINTS = 'technicalStoryProductRemainingPoints'
    // Release Remaining Points
    static final RELEASE_REMAINING_POINTS = 'functionalStoryReleaseRemainingPoints'
    static final FUNCTIONAL_STORY_RELEASE_REMAINING_POINTS = 'functionalStoryReleaseRemainingPoints'
    static final DEFECT_STORY_RELEASE_REMAINING_POINTS = 'defectStoryReleaseRemainingPoints'
    static final TECHNICAL_STORY_RELEASE_REMAINING_POINTS = 'technicalStoryReleaseRemainingPoints'
    // Total Stories in the Backlog for each state
    static final FINISHED_STORIES = 'finishedStories'
    static final INPROGRESS_STORIES = 'inProgressStories'
    static final PLANNED_STORIES = 'plannedStories'
    static final ESTIMATED_STORIES = 'estimatedStories'
    static final ACCEPTED_STORIES = 'acceptedStories'
    static final SUGGESTED_STORIES = 'suggestedStories'

    // Sprint Resources
    static final SPRINT_RESOURCES = 'sprintResources'

    static final SOURCE = 'source'
    // ******************
    // End XML Sprint cliche Tags names
    // ******************

    // ******************
    // Begin XML Daily cliche Tags names
    // ******************
    //TYPE
    static final TYPE_DAILY = 2

    static final TOTAL_STORIES = 'stories'
    static final STORIES_DONE = 'doneStories'
    static final STORIES_INPROGRESS = 'inprogressStories'
    static final STORIES_TOTAL_POINTS = 'pointsStories'
    static final STORIES_POINTS_DONE = 'totalPointsStories'

    static final TOTAL_TASKS = 'tasks'
    static final TASKS_WAIT = 'waitTasks'
    static final TASKS_INPROGRESS = 'inprogressTasks'
    static final TASKS_DONE = 'doneTasks'

    static final TASKS_SPRINT = 'sprintTasks'
    static final TASKS_RECURRENT = 'recurrentTasks'
    static final TASKS_URGENT = 'urgentTasks'
    static final TASKS_STORY = 'storyTasks'

    static final REMAINING_TIME = 'remainingHours'
    // ******************
    // End XML Daily cliche Tags names
    // ******************


    boolean equals(o) {
        if (this.is(o)) return true

        if (getClass() != o.class) return false

        Cliche cliche = (Cliche) o
        if (datePrise != cliche.datePrise) return false
        if (data != cliche.data) return false
        if (parentTimeBox != cliche.parentTimeBox) return false

        return true
    }

    @Override
    int hashCode() {
        Integer hash = 3
        hash = 67 * hash + (datePrise != null ? datePrise.hashCode() : 0)
        hash = 67 * hash + (parentTimeBox != null ? parentTimeBox.hashCode() : 0)
        return hash
    }

    def xml(builder) {
        builder.cliche(id: this.id) {
            builder.id(this.id)
            builder.type(this.type)
            builder.datePrise(this.datePrise)
            builder.data { builder.mkp.yieldUnescaped("<![CDATA[${this.data}]]>") }
            exportDomainsPlugins(builder)
        }
    }
}
