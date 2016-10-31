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


package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable


class Sprint extends TimeBox implements Serializable, Attachmentable {

    static final int STATE_WAIT = 1
    static final int STATE_INPROGRESS = 2
    static final int STATE_DONE = 3

    String deliveredVersion
    int state = Sprint.STATE_WAIT
    String retrospective  // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    String doneDefinition // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    Date inProgressDate
    Date doneDate
    Double velocity = 0d
    Double capacity = 0d
    Double dailyWorkTime = 8d
    Float initialRemainingTime

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
            'recurrentTasks', 'urgentTasks', 'hasNextSprint', 'parentReleaseId', 'activable', 'effectiveEndDate', 'effectiveStartDate', 'totalRemaining', 'parentProduct', 'totalEffort', 'previousSprint', 'nextSprint', 'parentReleaseName', 'index'
    ]

    static namedQueries = {
        getInProduct { p, id ->
            parentRelease {
                parentProduct {
                    eq 'id', p
                }
            }
            eq 'id', id
            uniqueResult = true
        }

        findCurrentSprint { p ->
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

        findCurrentOrNextSprint { p ->
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

        findCurrentOrLastSprint { p ->
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
        orderNumber index: 's_order_index'
    }

    static constraints = {
        deliveredVersion nullable: true
        retrospective nullable: true
        doneDefinition nullable: true
        inProgressDate nullable: true
        doneDate nullable: true
        initialRemainingTime nullable: true
        endDate(validator: { val, obj ->
            if (val > obj.parentRelease.endDate)
                return ['out.of.release.bounds']
            return true
        })
        startDate(validator: { val, obj ->
            if (val < obj.parentRelease.startDate)
                return ['out.of.release.bounds']
            def previousSprint = obj.parentRelease.sprints?.find { it.orderNumber == obj.orderNumber - 1 }
            if (previousSprint && val <= previousSprint.endDate)
                return ['previous.overlap']
            return true
        })
    }

    void setDone() {
        this.state = Sprint.STATE_DONE
    }

    static Sprint withSprint(long productId, long id) {
        Sprint sprint = (Sprint) getInProduct(productId, id).list()
        if (!sprint) {
            throw new ObjectNotFoundException(id, 'Sprint')
        }
        return sprint
    }

    static List<Sprint> withSprints(def params, def id = 'id') {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Sprint> sprints = ids ? getAll(ids).findAll { it && it.parentProduct.id == params.product.toLong() } : null
        if (!sprints) {
            throw new ObjectNotFoundException(ids, 'Sprint')
        }
        return sprints
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

    boolean getHasNextSprint() {
        return nextSprint != null
    }

    def getParentReleaseId() {
        return parentRelease.id
    }

    Sprint getPreviousSprint() {
        def previousSprintSameRelease = Sprint.findByParentReleaseAndOrderNumber(parentRelease, orderNumber - 1)
        if (previousSprintSameRelease) {
            return previousSprintSameRelease
        } else {
            def previousRelease = parentRelease.previousRelease
            Sprint.findByParentReleaseAndOrderNumber(previousRelease, previousRelease?.sprints?.size())
        }
    }

    Sprint getNextSprint() {
        def nextSprintSameRelease = Sprint.findByParentReleaseAndOrderNumber(parentRelease, orderNumber + 1)
        if (nextSprintSameRelease) {
            return nextSprintSameRelease
        } else {
            def nextRelease = parentRelease.nextRelease
            return nextRelease ? Sprint.findByParentReleaseAndOrderNumber(nextRelease, 1) : null
        }
    }

    def getActivable() {
        return state == STATE_WAIT && parentRelease.state == Release.STATE_INPROGRESS && (orderNumber == 1 || previousSprint && previousSprint.state == STATE_DONE)
    }

    Date getEffectiveEndDate() {
        return this.state == STATE_DONE ? doneDate : endDate
    }

    Date getEffectiveStartDate() {
        return this.state == STATE_WAIT ? startDate : inProgressDate
    }

    BigDecimal getTotalRemaining() {
        (BigDecimal) tasks?.sum { Task t -> t.estimation ? t.estimation.toBigDecimal() : 0.0 } ?: 0.0
    }

    def getParentProduct() {
        return this.parentRelease.parentProduct
    }

    BigDecimal getTotalEffort() {
        return (BigDecimal) (this.stories.sum { it.effort } ?: 0)
    }

    String getParentReleaseName() {
        return parentRelease.name
    }

    int getIndex() {
        return orderNumber + parentRelease.firstSprintIndex - 1
    }

    def xml(builder) {
        builder.sprint(id: this.id) {
            state(this.state)
            endDate(this.endDate)
            velocity(this.velocity)
            capacity(this.capacity)
            todoDate(this.todoDate)
            doneDate(this.doneDate)
            startDate(this.startDate)
            orderNumber(this.orderNumber)
            lastUpdated(this.lastUpdated)
            dateCreated(this.dateCreated)
            dailyWorkTime(this.dailyWorkTime)
            inProgressDate(this.inProgressDate)
            deliveredVersion(this.deliveredVersion)
            initialRemainingTime(this.initialRemainingTime)

            goal { builder.mkp.yieldUnescaped("<![CDATA[${this.goal ?: ''}]]>") }
            description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            retrospective { builder.mkp.yieldUnescaped("<![CDATA[${this.retrospective ?: ''}]]>") }
            doneDefinition { builder.mkp.yieldUnescaped("<![CDATA[${this.doneDefinition ?: ''}]]>") }

            attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
            stories() {
                this.stories.each { _story ->
                    _story.xml(builder)
                }
            }
            tasks() {
                this.tasks.each { _task ->
                    _task.xml(builder)
                }
            }
            cliches() {
                this.cliches.each { _cliche ->
                    _cliche.xml(builder)
                }
            }
        }
    }
}