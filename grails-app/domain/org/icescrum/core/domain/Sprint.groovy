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




package org.icescrum.core.domain

import org.icescrum.core.event.IceScrumSprintEvent
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.springframework.security.core.context.SecurityContextHolder as SCH


class Sprint extends TimeBox implements Serializable, Attachmentable {

    static final long serialVersionUID = -7022481404086376233L

    static final int STATE_WAIT = 1
    static final int STATE_INPROGRESS = 2
    static final int STATE_DONE = 3

    String deliveredVersion
    int state = Sprint.STATE_WAIT
    String retrospective  // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    String doneDefinition // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    Date activationDate
    Date closeDate
    Double velocity = 0d
    Double capacity = 0d
    Double dailyWorkTime = 8d
    Float initialRemainingHours

    static mappedBy = [
            stories: "parentSprint",
            tasks: "backlog"
    ]

    static hasMany = [
            stories: Story,
            tasks: Task,
    ]

    static belongsTo = [
            parentRelease: Release
    ]

    static transients = [
            'recurrentTasks', 'urgentTasks', 'hasNextSprint', 'parentReleaseId', 'activable', 'effectiveEndDate', 'effectiveStartDate', 'totalRemainingHours', 'parentProduct', 'totalEffort'
    ]

    static namedQueries = {
        getInProduct {p, id ->
            parentRelease {
                parentProduct {
                    eq 'id', p
                }
            }
            eq 'id', id
            uniqueResult = true
        }

        findCurrentSprint {p ->
            parentRelease {
                parentProduct {
                    eq 'id', p
                }
                eq 'state', Release.STATE_INPROGRESS
                order("orderNumber", "asc")
            }
            eq 'state', Sprint.STATE_INPROGRESS
            uniqueResult = true
        }

        findCurrentOrNextSprint {p ->
            parentRelease {
                parentProduct {
                    eq 'id', p
                }
                or {
                    eq 'state', Release.STATE_INPROGRESS
                    eq 'state', Release.STATE_WAIT
                }
                order("orderNumber", "asc")
            }
            and {
                or {
                    eq 'state', Sprint.STATE_INPROGRESS
                    eq 'state', Sprint.STATE_WAIT
                }
            }
            maxResults(1)
            order("orderNumber", "asc")
        }

        findHasNextSprint {rid, sOrderNumber ->
            parentRelease {
                eq 'id', rid
            }
            and {
                eq 'orderNumber', (sOrderNumber + 1)
            }
            projections {
                rowCount()
            }
        }

        findCurrentOrLastSprint {p ->
            parentRelease {
                parentProduct {
                    eq 'id', p
                }
                or {
                    eq 'state', Release.STATE_INPROGRESS
                    eq 'state', Release.STATE_DONE
                }
                order("orderNumber", "desc")
            }
            and {
                or {
                    eq 'state', Sprint.STATE_INPROGRESS
                    eq 'state', Sprint.STATE_DONE
                }
            }
            maxResults(1)
            order("orderNumber", "desc")
        }
    }

    static mapping = {
        cache true
        table 'icescrum2_sprint'
        retrospective type: 'text'
        doneDefinition type: 'text'
        stories cascade: 'delete', batchSize: 15, cache: true
        tasks cascade: 'all-delete-orphan', batchSize: 25
        orderNumber index: 's_order_index'
    }

    static constraints = {
        deliveredVersion nullable: true
        retrospective nullable: true
        doneDefinition nullable: true
        activationDate nullable: true
        closeDate nullable: true
        initialRemainingHours nullable: true
        endDate(validator:{ val, obj ->
            if (!val)
                return ['no.endDate']

            if (val > obj.parentRelease.endDate)
                return ['out.of.release.bounds']
            return true
        })
        startDate(validator:{ val, obj ->
            if (!val)
                return ['no.startDate']
            if (val < obj.parentRelease.startDate)
                return ['out.of.release.bounds']
            if (val > obj.endDate) {
                println val
                println obj.endDate
                println val.class
                println obj.endDate.class
                return ['after.endDate']
            }
            if (val == obj.endDate)
                return ['equals.endDate']

            def previousSprint = obj.parentRelease.sprints?.find { it.orderNumber == obj.orderNumber - 1}
            if (previousSprint && val <= previousSprint.endDate)
                return ['previous.overlap']
            return true
        })
    }

    void setDone() {
        this.state = Sprint.STATE_DONE
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!endDate) ? 0 : endDate.hashCode())
        result = prime * result + ((!parentRelease) ? 0 : parentRelease.hashCode())
        result = prime * result + ((!startDate) ? 0 : startDate.hashCode())
        return result
    }

    /**
     * Clone method override.
     *
     * @return
     */
    @Override
    Object clone() {
        Sprint copy
        try {
            copy = (Sprint) super.clone()
        } catch (Exception e) {
            return null
        }

        return copy
    }

    @Override
    boolean equals(Object obj) {
        if (obj == null) {
            return false
        }
        if (getClass() != obj.getClass()) {
            return false
        }
        final Sprint other = (Sprint) obj
        if (this.orderNumber != other.orderNumber && (this.orderNumber == null || !this.orderNumber.equals(other.orderNumber))) {
            return false
        }
        if (this.state != other.state && (this.state == null || !this.state.equals(other.state))) {
            return false
        }
        if ((this.goal == null) ? (other.goal != null) : !this.goal.equals(other.goal)) {
            return false
        }
        if (this.startDate != other.startDate && (this.startDate == null || !this.startDate.equals(other.startDate))) {
            return false
        }
        if (this.endDate != other.endDate && (this.endDate == null || !this.endDate.equals(other.endDate))) {
            return false
        }
        if (this.parentRelease != other.parentRelease && (this.parentRelease == null || !this.parentRelease.equals(other.parentRelease))) {
            return false
        }
        if (this.velocity != other.velocity && (this.velocity == null || !this.velocity.equals(other.velocity))) {
            return false
        }
        if (this.capacity != other.capacity && (this.capacity == null || !this.capacity.equals(other.capacity))) {
            return false
        }
        if (this.dailyWorkTime != other.dailyWorkTime && (this.dailyWorkTime == null || !this.dailyWorkTime.equals(other.dailyWorkTime))) {
            return false
        }
        return true
    }

    def getRecurrentTasks() {
        return tasks?.findAll { it.type == Task.TYPE_RECURRENT }
    }

    def getUrgentTasks() {
        return tasks?.findAll { it.type == Task.TYPE_URGENT }
    }

    def getHasNextSprint() {
        if (Sprint.findHasNextSprint(parentRelease.id, orderNumber).list()[0]){
            return true
        }
        def nextRelease = Release.findByOrderNumberAndParentProduct(parentRelease.orderNumber + 1, parentRelease.parentProduct)
        if (nextRelease && Sprint.findHasNextSprint(nextRelease.id, 1).list()[0]){
            return true
        }
        return false
    }

    def getParentReleaseId() {
        return parentRelease.id
    }

    def getActivable() {
        if (this.state != Sprint.STATE_WAIT)
            return false
        else if (this.parentRelease.state == Release.STATE_INPROGRESS && (this.orderNumber == 1 || this.orderNumber == Sprint.countByStateAndParentRelease(Sprint.STATE_DONE, this.parentRelease) + 1))
            return true
        else if (this.parentRelease.state == Release.STATE_WAIT && Release.countByStateAndParentProduct(Release.STATE_INPROGRESS, this.parentProduct) == 0 && this.orderNumber == 1)
            return true
        else if(Release.countByStateAndParentProduct(Release.STATE_INPROGRESS, this.parentProduct) == 1){
            def previous = Release.findByStateAndParentProduct(Release.STATE_INPROGRESS, this.parentProduct)
            if (!previous.sprints || previous.sprints.find{ it.state != Sprint.STATE_DONE }){
                return false
            }else if(this.parentRelease.state == Release.STATE_WAIT && this.orderNumber == 1){
                return true
            }
        }
        else
            return false;
    }

    //Get the right endDate from the sprint state
    Date getEffectiveEndDate(){
        return this.state == STATE_DONE ? closeDate : endDate
    }

    //Get the right startDate from the sprint state
    Date getEffectiveStartDate(){
        return this.state == STATE_WAIT ? startDate : activationDate
    }

    Float getTotalRemainingHours(){
        Float raf = 0
        this.tasks*.estimation?.each{
            if (it)
                raf += it
        }
        raf
    }

    def getParentProduct(){
        return this.parentRelease.parentProduct
    }

    Integer getTotalEffort() {
        return (Integer) this.stories.sum { it.effort }
    }

    def afterInsert(){
        withNewSession {
            publishEvent(new IceScrumSprintEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_CREATED, true))
        }
    }

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumSprintEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE, true))
        }
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumSprintEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE, true))
        }
    }
}