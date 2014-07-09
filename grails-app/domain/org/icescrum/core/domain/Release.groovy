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

import org.icescrum.plugins.attachmentable.interfaces.Attachmentable

import java.sql.Timestamp


class Release extends TimeBox implements Cloneable, Attachmentable {

    static final long serialVersionUID = -8505932836642777504L

    static final int STATE_WAIT = 1
    static final int STATE_INPROGRESS = 2
    static final int STATE_DONE = 3

    int state = Release.STATE_WAIT
    String vision = "" // Beware of distinct, it won't work in MSSQL since this attribute is TEXT
    String name = "R"
    SortedSet<Sprint> sprints

    static belongsTo = [parentProduct: Product]

    static hasMany = [sprints: Sprint, features: Feature]

    static mappedBy = [sprints: 'parentRelease',features: 'parentRelease']

    static transients = ['firstDate', 'closable', 'activable', 'meanVelocity', 'previousRelease', 'nextRelease']

    static mapping = {
        cache true
        table 'icescrum2_release'
        vision type: 'text'
        sprints cascade: 'all-delete-orphan', sort: 'id', cache: true
        name index: 'rel_name_index'
    }

    static constraints = {
        vision nullable: true
        name(blank: false, unique: 'parentProduct')
        endDate(validator:{ val, obj ->
            if (!val){
                return ['blank']
            }
            if(val.before(obj.startDate)){
                return ['before.startDate']
            }
            return true
        })
        startDate(validator:{ val, obj ->
            if (!val){
                return ['blank']
            }
            if(val == obj.endDate){
                return ['equals.endDate']
            }
            if (val.before(obj.parentProduct.startDate)){
                return ['before.productStartDate']
            }
            def r = obj.parentProduct.releases?.find{ it.orderNumber == obj.orderNumber - 1}
            if (r && val.before(r.endDate)) {
                return ['before.previous']
            }
            return true
        })
        state(validator:{ val, obj ->
            if (val == STATE_DONE && obj.sprints.any { it.state != Sprint.STATE_DONE })
                return ['sprint.not.done']
            return true
        })
    }

    static namedQueries = {
        findCurrentOrNextRelease {p ->
            parentProduct {
                eq 'id', p
            }
            or {
                eq 'state', Release.STATE_INPROGRESS
                eq 'state', Release.STATE_WAIT
            }
            order("orderNumber", "asc")
            maxResults(1)
        }

        findCurrentOrLastRelease {p ->
            parentProduct {
                eq 'id', p
            }
            or {
                eq 'state', Release.STATE_INPROGRESS
                eq 'state', Release.STATE_DONE
            }
            order("orderNumber", "desc")
            maxResults(1)
        }

        getInProduct {p, id ->
            parentProduct {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Timestamp findLastUpdatedSprint(Release release) {
        executeQuery(
                """SELECT max(sprint.lastUpdated)
                   FROM Release release
                   INNER JOIN release.sprints as sprint
                   WHERE release = :release""", [release: release]).first() as Timestamp
    }

    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!parentProduct) ? 0 : parentProduct.hashCode())
        return result
    }

    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        final Release other = (Release) obj
        if (name == null) {
            if (other.name != null)
                return false
        } else if (name != other.name)
            return false
        if (parentProduct == null) {
            if (other.parentProduct != null)
                return false
        } else if (!parentProduct.equals(other.parentProduct))
            return false
        return true
    }

    Date getFirstDate() {
        if (sprints?.size() > 0)
            return sprints.asList().last().endDate
        else
            return startDate
    }

    boolean getClosable(){
        if (state != STATE_INPROGRESS)
            return false

        if (sprints?.size()) {
            if (sprints.asList().last().state == Sprint.STATE_DONE)
                return true
        }
       return false
    }

    boolean getActivable(){
        if (state == STATE_WAIT && parentProduct.releases?.find{it.state == STATE_INPROGRESS} == null){
            if (orderNumber >= 2 && parentProduct.releases.find{it.orderNumber == orderNumber - 1}.state == STATE_DONE)
                return true
            if (orderNumber == 1)
                return true
        }
        return false
    }

    Release getPreviousRelease() {
        return parentProduct.releases.findAll { it.orderNumber < orderNumber }?.max { it.orderNumber }
    }

    Release getNextRelease() {
        return parentProduct.releases.findAll { it.orderNumber > orderNumber }?.min { it.orderNumber }
    }

    Integer getMeanVelocity() {
        def doneSprints = sprints.findAll { it.state == Sprint.STATE_DONE }
        return doneSprints ? ((Integer) doneSprints.sum { it.velocity.toBigDecimal() }).intdiv(doneSprints.size()) : 0
    }
}
