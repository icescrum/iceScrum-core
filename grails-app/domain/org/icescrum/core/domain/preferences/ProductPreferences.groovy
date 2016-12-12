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
 */


package org.icescrum.core.domain.preferences

import org.icescrum.core.domain.Product

class ProductPreferences implements Serializable {

    static final long serialVersionUID = 813639045272950126L

    //Project
    boolean hidden = false
    boolean archived = false
    boolean hideWeekend = false
    boolean webservices = false
    String timezone = TimeZone.default.ID

    //Planification
    boolean noEstimation = false

    //Sprint preferences
    boolean autoDoneStory = false
    boolean displayRecurrentTasks = true
    boolean displayUrgentTasks = true
    boolean assignOnCreateTask = false
    boolean assignOnBeginTask = true
    boolean autoCreateTaskOnEmptyStory = false
    int estimatedSprintsDuration = 14
    int limitUrgentTasks = 0

    // Meeting hours
    String releasePlanningHour = "9:00"
    String sprintPlanningHour = "9:00"
    String dailyMeetingHour = "11:00"
    String sprintReviewHour = "14:00"
    String sprintRetrospectiveHour = "16:00"
    String stakeHolderRestrictedViews

    static constraints = {
        stakeHolderRestrictedViews(nullable: true)
    }

    static belongsTo = [
            product: Product
    ]

    static mapping = {
        cache true
        table 'is_product_preferences'
    }

    def xml(builder) {
        builder.preferences(id: this.id) {
            builder.hidden(this.hidden)
            builder.timezone(this.timezone)
            builder.hideWeekend(this.hideWeekend)
            builder.noEstimation(this.noEstimation)
            builder.autoDoneStory(this.autoDoneStory)
            builder.sprintReviewHour(this.sprintReviewHour)
            builder.dailyMeetingHour(this.dailyMeetingHour)
            builder.limitUrgentTasks(this.limitUrgentTasks)
            builder.assignOnBeginTask(this.assignOnBeginTask)
            builder.displayUrgentTasks(this.displayUrgentTasks)
            builder.assignOnCreateTask(this.assignOnCreateTask)
            builder.sprintPlanningHour(this.sprintPlanningHour)
            builder.releasePlanningHour(this.releasePlanningHour)
            builder.displayRecurrentTasks(this.displayRecurrentTasks)
            builder.sprintRetrospectiveHour(this.sprintRetrospectiveHour)
            builder.estimatedSprintsDuration(this.estimatedSprintsDuration)
            builder.autoCreateTaskOnEmptyStory(this.autoCreateTaskOnEmptyStory)
            builder.stakeHolderRestrictedViews(this.stakeHolderRestrictedViews)
            exportDomainsPlugins(builder)
        }
    }
}
