/*
 * Copyright (c) 2014 Kagilum SAS.
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
 * Jeroen Broekhuizen (Jeroen.Broekhuizen@quintiq.com)
 */


package org.icescrum.core.domain

import grails.util.GrailsNameUtils
import org.hibernate.ObjectNotFoundException

class Task extends BacklogElement implements Serializable {

    static final long serialVersionUID = -7399441592678920364L

    static final int STATE_WAIT = 0
    static final int STATE_BUSY = 1
    static final int STATE_DONE = 2
    static final int TYPE_RECURRENT = 10
    static final int TYPE_URGENT = 11

    String color = "#f9f157"

    Integer type
    Float estimation
    Float initial
    Integer rank = 0
    boolean blocked = false

    Date doneDate
    Date inProgressDate

    int state = Task.STATE_WAIT

    static belongsTo = [
            creator      : User,
            responsible  : User,
            parentStory  : Story,
            parentProject: Project
    ]

    static hasMany = [participants: User]

    static transients = ['sprint', 'activity']

    static mapping = {
        cache true
        table 'is_task'
        participants cache: true, lazy: false
        activities cascade: 'delete-orphan', batchSize: 25, cache: true // Doesn't work on BacklogElement
    }

    static constraints = {
        type nullable: true, validator: { newType, task -> (task.parentStory == null ? newType in [TYPE_URGENT, TYPE_RECURRENT] : newType == null) ?: 'invalid' }
        color nullable: true
        initial nullable: true
        backlog nullable: true
        doneDate nullable: true
        estimation nullable: true, validator: { newEstimation, task -> newEstimation == null || newEstimation >= 0 ?: 'invalid' }
        responsible nullable: true
        parentStory nullable: true, validator: { newParentStory, task -> newParentStory != null && newParentStory.backlog != task.parentProject ? 'invalid' : true }
        parentProject validator: { newParentProject, task -> newParentProject == task.backlog?.parentProject || newParentProject == task.parentStory?.backlog ?: 'invalid' }
        inProgressDate nullable: true
    }

    static namedQueries = {
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
        findLastUpdated { storyId ->
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

    static Task getInProject(Long pid, Long id) {
        executeQuery("""SELECT t
                        FROM org.icescrum.core.domain.Task as t
                        WHERE t.parentProject.id = :pid
                        AND t.id = :id""", [pid: pid, id: id], [max: 1])[0]
    }

    static Task getInProjectByUid(Long pid, int uid) {
        executeQuery("""SELECT t
                        FROM org.icescrum.core.domain.Task as t
                        WHERE t.parentProject.id = :pid
                        AND t.uid = :uid""", [pid: pid, uid: uid], [max: 1])[0]
    }

    static List<Task> getAllInProject(Long pid, List id) {
        executeQuery("""SELECT t
                        FROM org.icescrum.core.domain.Task as t
                        WHERE t.parentProject.id = :pid
                        AND t.id IN (:id) """, [pid: pid, id: id])
    }

    static List<Task> getAllInProjectUID(Long pid, List uid) {
        executeQuery("""SELECT t
                        FROM org.icescrum.core.domain.Task as t
                        WHERE t.parentProject.id = :pid
                        AND t.uid IN (:uid)""", [pid: pid, uid: uid])
    }

    static List<Task> getAllInProject(Long pid) {
        executeQuery("""SELECT t
                        FROM org.icescrum.core.domain.Task as t
                        WHERE t.parentProject.id = :pid""", [pid: pid])
    }

    static List<User> getAllCreatorsInProject(Long pid) {
        User.executeQuery("""SELECT DISTINCT t.creator
                             FROM org.icescrum.core.domain.Task as t
                             WHERE t.parentProject.id = :pid""", [pid: pid])
    }

    static List<User> getAllResponsiblesInProject(Long pid) {
        User.executeQuery("""SELECT DISTINCT t.responsible
                             FROM org.icescrum.core.domain.Task as t
                             WHERE t.parentProject.id = :pid""", [pid: pid])
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(t.uid)
                   FROM org.icescrum.core.domain.Task as t
                   WHERE t.parentProject.id = :pid""", [pid: pid])[0] ?: 0) + 1
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

    static Task withTask(long projectId, long id) {
        Task task = (Task) getInProject(projectId, id)
        if (!task) {
            throw new ObjectNotFoundException(id, 'Task')
        }
        return task
    }

    static List<Task> withTasks(def params, def id = 'id') {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)*.toLong()
        List<Task> tasks = ids ? getAllInProject(params.project.toLong(), ids) : null
        if (!tasks) {
            throw new ObjectNotFoundException(ids, 'Task')
        }
        return tasks
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
        if (this.is(obj)) {
            return true
        }
        if (obj == null) {
            return false
        }
        if (getClass() != obj.getClass()) {
            return false
        }
        final Task other = (Task) obj
        if (name == null) {
            if (other.name != null) {
                return false
            }
        } else if (!name.equals(other.name)) {
            return false
        }
        if (id != other.id) {
            return false;
        }
        return true
    }

    Sprint getSprint() {
        return this.getBacklog()?.id ? (Sprint) this.getBacklog() : null
    }

    def getActivity() {
        return activities.sort { a, b -> b.dateCreated <=> a.dateCreated }
    }

    static search(project, options) {
        def criteria = {
            backlog {
                if (options.task?.parentSprint?.isLong() && options.task.parentSprint.toLong() in project.releases*.sprints*.id.flatten()) {
                    eq 'id', options.task.parentSprint.toLong()
                } else if (options.task?.parentRelease?.isLong() && options.task.parentRelease.toLong() in project.releases*.id) {
                    'in' 'id', project.releases.find { it.id == options.task.parentRelease.toLong() }.sprints*.id
                } else {
                    'in' 'id', project.releases*.sprints*.id.flatten()
                }
            }

            if (options.term || options.task != null) {
                if (options.term) {
                    or {
                        if (options.term?.isInteger()) {
                            eq 'uid', options.term.toInteger()
                        } else {
                            ilike 'name', '%' + options.term + '%'
                            ilike 'description', '%' + options.term + '%'
                            ilike 'notes', '%' + options.term + '%'
                        }
                    }
                }
                if (options.task?.type?.isInteger()) {
                    eq 'type', options.task.type.toInteger()
                }
                if (options.task?.state?.isInteger()) {
                    eq 'state', options.task.state.toInteger()
                }
                if (options.task?.parentStory?.isLong()) {
                    parentStory {
                        eq 'id', options.task.parentStory.toLong()
                    }
                }
                if (options.task?.creator?.isLong()) {
                    creator {
                        eq 'id', options.task.creator.toLong()
                    }
                }
                if (options.task?.responsible?.isLong()) {
                    responsible {
                        eq 'id', options.task.responsible.toLong()
                    }
                }
            }
        }
        if (options.tag) {
            return Task.findAllByTagWithCriteria(options.tag) {
                criteria.delegate = delegate
                criteria.call()
            }
        } else if (options.term || options.task) {
            return Task.createCriteria().list {
                criteria.delegate = delegate
                criteria.call()
            }
        } else {
            return Collections.EMPTY_LIST
        }
    }

    def xml(builder) {
        builder.task(uid: this.uid) {
            builder.type(this.type)
            builder.rank(this.rank)
            builder.color(this.color)
            builder.state(this.state)
            builder.blocked(this.blocked)
            builder.initial(this.initial)
            builder.doneDate(this.doneDate)
            builder.estimation(this.estimation)
            builder.todoDate(this.todoDate)
            builder.inProgressDate(this.inProgressDate)

            builder.creator(uid: this.creator.uid)

            if (this.responsible) {
                builder.responsible(uid: this.responsible.uid)
            }

            builder.tags { builder.mkp.yieldUnescaped("<![CDATA[${this.tags ?: ''}]]>") }
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.notes { builder.mkp.yieldUnescaped("<![CDATA[${this.notes ?: ''}]]>") }
            builder.description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }

            builder.attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }

            builder.comments() {
                this.comments.each { _comment ->
                    builder.comment() {
                        builder.dateCreated(_comment.dateCreated)
                        builder.posterId(_comment.posterId)
                        builder.posterClass(_comment.posterClass)
                        builder.body { builder.mkp.yieldUnescaped("<![CDATA[${_comment.body}]]>") }
                    }
                }
            }

            builder.activities() {
                this.activities.each { _activity ->
                    _activity.xml(builder)
                }
            }

            exportDomainsPlugins(builder)
        }
    }
}
