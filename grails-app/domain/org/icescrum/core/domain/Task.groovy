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
 */



package org.icescrum.core.domain

import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumTaskEvent

class Task extends BacklogElement implements Serializable {

    static final long serialVersionUID = -7399441592678920364L

    static final int STATE_WAIT = 0
    static final int STATE_BUSY = 1
    static final int STATE_DONE = 2
    static final int TYPE_RECURRENT = 10
    static final int TYPE_URGENT = 11

    Integer type
    Integer estimation
    Integer rank = 0
    boolean blocked = false

    Date doneDate
    Date inProgressDate

    int state = Task.STATE_WAIT

    static belongsTo = [
            responsible: User,
            creator: User,
            impediment: Impediment,
            parentStory: Story
    ]

    static hasMany = [participants: User]

    static mapping = {
        cache true
        table 'icescrum2_task'
        parentStory index: 't_name_index'
        name index: 't_name_index'
        participants cache: true
    }

    static constraints = {
        estimation nullable: true
        responsible nullable: true
        impediment nullable: true
        parentStory nullable: true
        type nullable: true
        doneDate nullable: true
        inProgressDate nullable: true
        name unique: 'parentStory'
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

        findUrgentTasksFilter { s, term = null, u = null ->
            backlog {
                eq 'id', s.id
            }
            if (term) {
                or {
                    ilike 'name', term
                    ilike 'description', term
                    ilike 'notes', term
                }
            }
            if (u) {
                responsible {
                    if (u.preferences.filterTask == 'myTasks') {
                        eq 'id', u.id
                    }
                }
                if (u.preferences.filterTask == 'freeTasks') {
                    isNull('responsible')
                }
                if (u.preferences.hideDoneState) {
                    ne 'state', Task.STATE_DONE
                }
            }
            eq 'type', Task.TYPE_URGENT
        }

        findRecurrentTasksFilter { s, term = null, u = null ->
            backlog {
                eq 'id', s.id
            }
            if (term) {
                or {
                    ilike 'name', term
                    ilike 'description', term
                    ilike 'notes', term
                }
            }
            if (u) {
                responsible {
                    if (u.preferences.filterTask == 'myTasks') {
                        eq 'id', u.id
                    }
                }
                if (u.preferences.filterTask == 'freeTasks') {
                    isNull('responsible')
                }
                if (u.preferences.hideDoneState) {
                    ne 'state', Task.STATE_DONE
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

        getInProduct { p, id ->
            backlog {
                parentRelease {
                    parentProduct {
                        eq 'id', p
                    }
                }
            }
            eq 'id', id
        }
    }

    static Task getInProduct(Long pid, Long id) {
        executeQuery(
                """SELECT DISTINCT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid
                   AND t.id = :id """, [pid: pid, id:id], [max:1])[0]
    }

    static List<Task> getAllInProduct(Long pid, List id) {
        executeQuery(
                """SELECT DISTINCT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid
                   AND t.id IN (:id) """, [pid: pid, id:id])
    }

    static List<Task> getAllInProduct(Long pid) {
        executeQuery(
                """SELECT DISTINCT t
                   FROM org.icescrum.core.domain.Task as t, org.icescrum.core.domain.Sprint as s, org.icescrum.core.domain.Release as r
                   WHERE t.backlog = s
                   AND s.parentRelease = r
                   AND r.parentProduct.id = :pid """, [pid: pid])
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

    def springSecurityService

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumTaskEvent(this, this.class, User.get(springSecurityService.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE))
        }
    }

     def afterUpdate() {
        flushCache(cache:'project_'+this.backlog.parentRelease.parentProduct.id+'_taskCache_'+this.id)
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumTaskEvent(this, this.class, User.get(springSecurityService.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE))
        }
    }
}
