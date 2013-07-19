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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Jeroen Broekhuizen (Jeroen.Broekhuizen@quintiq.com)
 */



package org.icescrum.core.domain

import grails.util.GrailsNameUtils
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumTaskEvent
import org.springframework.security.core.context.SecurityContextHolder as SCH

class Task extends BacklogElement implements Serializable {

    static final long serialVersionUID = -7399441592678920364L

    static final int STATE_WAIT = 0
    static final int STATE_BUSY = 1
    static final int STATE_DONE = 2
    static final int TYPE_RECURRENT = 10
    static final int TYPE_URGENT = 11

    String color = "yellow"
	
    Integer type
    Float estimation
    Float initial
    Integer rank = 0
    boolean blocked = false

    Date doneDate
    Date inProgressDate

    int state = Task.STATE_WAIT

    static belongsTo = [
            responsible: User,
            creator: User,
            impediment: Impediment,
            parentStory: Story,
    ]

    static hasMany = [participants: User]

    static transients = ['sprint', 'parentProduct']

    static mapping = {
        cache true
        table 'icescrum2_task'
        parentStory index: 't_name_index'
        name index: 't_name_index'
        participants cache: true
    }

    static constraints = {
        estimation nullable: true
        initial nullable: true
        responsible nullable: true
        impediment nullable: true
        parentStory nullable: true
        type nullable: true
        doneDate nullable: true
        inProgressDate nullable: true
        name unique: 'parentStory'
        color nullable: true
    }

    static namedQueries = {
        findNextTask {t, u ->
            backlog {
                eq 'id', t.backlog.id
            }
            or {
                responsible {
                    eq 'id', u.id
                }
                isNull('responsible')
            }
            ne 'state', Task.STATE_DONE
            gt 'id', t.id
            maxResults(1)
            order("id", "asc")
        }

        findNextTaskInSprint { Task task ->
            backlog {
                eq 'id', task.backlog.id
            }
            gt 'id', task.id
            maxResults(1)
            order("id", "asc")
        }

        findPreviousTaskInSprint { Task task ->
            backlog {
                eq 'id', task.backlog.id
            }
            lt 'id', task.id
            maxResults(1)
            order("id", "desc")
        }

        findUrgentTasksFilter { s, term = null, u = null, userid = null ->
            backlog {
                eq 'id', s.id
            }
            if (term) {
                or {
                    def termInteger = term.replaceAll('%','')
                    if (termInteger?.isInteger()){
                        eq 'uid', termInteger.toInteger()
                    }
                    ilike 'name', term
                    ilike 'description', term
                    ilike 'notes', term
                }
            }
            if (userid) {
                responsible {
                    eq 'id', userid
                }
            } else if (u) {
                responsible {
                    if (u.preferences.filterTask == 'myTasks') {
                        eq 'id', u.id
                    }
                }
                if (u.preferences.filterTask == 'freeTasks') {
                    isNull('responsible')
                }
                if (u.preferences.hideDoneState && s?.state == Sprint.STATE_INPROGRESS) {
                    ne 'state', Task.STATE_DONE
                }
                if (u.preferences.filterTask == 'blockedTasks') {
                    eq 'blocked', true
                }
            }
            eq 'type', Task.TYPE_URGENT
        }

        findRecurrentTasksFilter { s, term = null, u = null, userid = null ->
            backlog {
                eq 'id', s.id
            }
            if (term) {
                or {
                    def termInteger = term.replaceAll('%','')
                    if (termInteger?.isInteger()){
                        eq 'uid', termInteger.toInteger()
                    }else{
                        ilike 'name', term
                        ilike 'description', term
                        ilike 'notes', term
                    }
                }
            }
            if (userid) {
                responsible {
                    eq 'id', userid
                }
            } else if (u) {
                responsible {
                    if (u.preferences.filterTask == 'myTasks') {
                        eq 'id', u.id
                    }
                }
                if (u.preferences.filterTask == 'freeTasks') {
                    isNull('responsible')
                }
                if (u.preferences.hideDoneState && s?.state == Sprint.STATE_INPROGRESS) {
                    ne 'state', Task.STATE_DONE
                }
                if (u.preferences.filterTask == 'blockedTasks') {
                    eq 'blocked', true
                }
            }
            eq 'type', Task.TYPE_RECURRENT
        }

        getUserTasks { s, u ->
            backlog {
                eq 'id', s
            }
            responsible {
                    eq 'id', u
            }
        }

        getFreeTasks { s ->
            backlog {
                eq 'id', s
            }
            isNull('responsible')
        }

        getAllTasksInSprint { s ->
            backlog {
                eq 'id', s
            }
        }

        findLastUpdated {storyId ->
            parentStory {
                eq 'id', storyId
            }
            projections {
                property 'lastUpdated'
            }
            order("lastUpdated", "desc")
            maxResults(1)
            cache true
        }
    }

    static Task getInProduct(Long pid, Long id) {
        executeQuery(
                """SELECT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid
                   AND t.id = :id """, [pid: pid, id:id], [max:1])[0]
    }

    static Task getInProductByUid(Long pid, int uid) {
        executeQuery(
                """SELECT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid
                   AND t.uid = :uid """, [pid: pid, uid:uid], [max:1])[0]
    }

    static List<Task> getAllInProduct(Long pid, List id) {
        executeQuery(
                """SELECT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid
                   AND t.id IN (:id) """, [pid: pid, id:id])
    }

    static List<Task> getAllInProductUID(Long pid, List uid) {
        executeQuery(
                """SELECT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid
                   AND t.uid IN (:uid) """, [pid: pid, uid:uid])
    }

    static List<Task> getAllInProduct(Long pid) {
        executeQuery(
                """SELECT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid """, [pid: pid])
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(t.uid)
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid """, [pid: pid])[0]?:0) + 1
    }

    static findLastUpdatedComment(def element) {
        executeQuery("SELECT c.lastUpdated " +
            "FROM org.grails.comments.Comment as c, org.grails.comments.CommentLink as cl, ${element.class.name} as b " +
            "WHERE c = cl.comment " +
            "AND cl.commentRef = b " +
            "AND cl.type = :type " +
            "AND b.id = :id " +
            "ORDER BY c.lastUpdated DESC",
            [id: element.id, type: GrailsNameUtils.getPropertyName(element.class)],
            [max: 1])[0]
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        return result
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        final Task other = (Task) obj
        if (name == null) {
            if (other.name != null)
                return false
        } else if (!name.equals(other.name))
            return false
        if (id != other.id) {
            return false;
        }
        return true
    }

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumTaskEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE, true))
        }
    }

    Sprint getSprint(){
        if (this.getBacklog()?.id)
            return (Sprint)this.getBacklog()
        return null
    }

    Product getParentProduct(){
        return this.sprint?.parentProduct
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumTaskEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE, true))
        }
    }

    static search(product, options){
        def criteria = {
            backlog {
                if (options.task?.parentSprint?.isLong() && options.task.parentSprint.toLong() in product.releases*.sprints*.id.flatten()){
                    eq 'id', options.task.parentSprint.toLong()
                } else if (options.task?.parentRelease?.isLong() && options.task.parentRelease.toLong() in product.releases*.id){
                    'in' 'id', product.releases.find{it.id == options.task.parentRelease.toLong()}.sprints*.id
                } else {
                    'in' 'id', product.releases*.sprints*.id.flatten()
                }
            }

            if (options.term || options.task != null){
                if (options.term){
                    or {
                        if (options.term?.isInteger()){
                            eq 'uid', options.term.toInteger()
                        }else{
                            ilike 'name', '%'+options.term+'%'
                            ilike 'description', '%'+options.term+'%'
                            ilike 'notes', '%'+options.term+'%'
                        }
                    }
                }
                if (options.task?.type?.isInteger()){
                    eq 'type', options.task.type.toInteger()
                }
                if (options.task?.state?.isInteger()){
                    eq 'state', options.task.state.toInteger()
                }
                if (options.task?.parentStory?.isLong()){
                    parentStory{
                        eq 'id', options.task.parentStory.toLong()
                    }
                }
                if (options.task?.creator?.isLong()){
                    creator {
                        eq 'id', options.task.creator.toLong()
                    }
                }
                if (options.task?.responsible?.isLong()){
                    responsible {
                        eq 'id', options.task.responsible.toLong()
                    }
                }
            }
        }
        if (options.tag){
            return Task.findAllByTagWithCriteria(options.tag) {
                criteria.delegate = delegate
                criteria.call()
            }
        } else if(options.term || options.task)  {
            return Task.createCriteria().list {
                criteria.delegate = delegate
                criteria.call()
            }
        } else {
            return Collections.EMPTY_LIST
        }
    }

    static searchByTermOrTag(product, searchOptions, term) {
        search(product, addTermOrTagToSearch(searchOptions, term))
    }

    static searchAllByTermOrTag(product, term) {
        def searchOptions = [task: [:]]
        searchByTermOrTag(product, searchOptions, term)
    }
}
