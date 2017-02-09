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

class Feature extends BacklogElement implements Serializable {
    static final long serialVersionUID = 7072515028109185168L

    static final int TYPE_FUNCTIONAL = 0
    static final int TYPE_ARCHITECTURAL = 1
    static final int STATE_WAIT = 0
    static final int STATE_BUSY = 1
    static final int STATE_DONE = 2

    String color = "#2d8ccc" // Blue by default

    Integer value = null
    int type = Feature.TYPE_FUNCTIONAL
    int rank

    static transients = ['countDoneStories', 'state', 'effort', 'inProgressDate', 'doneDate']

    static belongsTo = [
            parentRelease: Release
    ]

    static hasMany = [stories: Story]

    static mappedBy = [stories: "feature"]

    static mapping = {
        cache true
        table 'is_feature'
        stories cascade: "refresh", sort: 'rank', 'name': 'asc', cache: true
        sort "id"
        activities cascade: 'delete-orphan' // Doesn't work on BacklogElement
    }

    static constraints = {
        name(unique: 'backlog')
        parentRelease(nullable: true)
        value(nullable: true)
    }

    static namedQueries = {

        getInProject { p, id ->
            backlog {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Feature withFeature(long projectId, long id) {
        Feature feature = (Feature) getInProject(projectId, id).list()
        if (!feature) {
            throw new ObjectNotFoundException(id, 'Feature')
        }
        return feature
    }

    static List<Feature> withFeatures(def params, def id = 'id') {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Feature> features = ids ? getAll(ids).findAll { it && it.backlog.id == params.project.toLong() } : null
        if (!features) {
            throw new ObjectNotFoundException(ids, 'Feature')
        }
        return features
    }

    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!backlog) ? 0 : backlog.hashCode())
        return result
    }

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
        final Feature other = (Feature) obj
        if (name == null) {
            if (other.name != null) {
                return false
            }
        } else if (!name.equals(other.name)) {
            return false
        }
        if (backlog == null) {
            if (other.backlog != null) {
                return false
            }
        } else if (!backlog.equals(other.backlog)) {
            return false
        }
        return true
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(f.uid)
                   FROM org.icescrum.core.domain.Feature as f, org.icescrum.core.domain.Project as p
                   WHERE f.backlog = p
                   AND p.id = :pid """, [pid: pid])[0] ?: 0) + 1
    }

    def getCountDoneStories() {
        return stories?.sum { (it.state == Story.STATE_DONE) ? 1 : 0 } ?: 0
    }

    def getState() {
        if (!stories || stories.find { it.state > Story.STATE_PLANNED } == null) {
            return STATE_WAIT
        }
        if (stories.collect { it.state }.count(Story.STATE_DONE) == stories.size()) {
            return STATE_DONE
        } else {
            return STATE_BUSY
        }
    }

    def getEffort() {
        return stories?.sum { it.effort ?: 0 } ?: 0
    }

    Date getInProgressDate() {
        return state > STATE_WAIT ? stories.collect { it.inProgressDate }.findAll { it != null }.sort().last() : null
    }

    Date getDoneDate() {
        return state == STATE_DONE ? stories.collect { it.doneDate }.findAll { it != null }.sort().first() : null
    }

    static search(project, options) {
        def criteria = {
            backlog {
                eq 'id', project
            }
            if (options.term || options.feature) {
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
                if (options.feature?.type?.isInteger()) {
                    eq 'type', options.feature.type.toInteger()
                }
            }
        }
        if (options.tag) {
            return Feature.findAllByTagWithCriteria(options.tag) {
                criteria.delegate = delegate
                criteria.call()
            }
        } else if (options.term || options.feature != null) {
            return Feature.createCriteria().list {
                criteria.delegate = delegate
                criteria.call()
            }
        } else {
            return Collections.EMPTY_LIST
        }
    }

    static searchByTermOrTag(projectId, searchOptions, term) {
        search(projectId, addTermOrTagToSearch(searchOptions, term))
    }

    static searchAllByTermOrTag(projectId, term) {
        def searchOptions = [feature: [:]]
        searchByTermOrTag(projectId, searchOptions, term)
    }

    def xml(builder) {
        builder.feature(uid: this.uid) {
            builder.type(this.type)
            builder.rank(this.rank)
            builder.color(this.color)
            builder.value(this.value ?: '')
            builder.todoDate(this.todoDate)
            builder.tags { builder.mkp.yieldUnescaped("<![CDATA[${this.tags}]]>") }
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.notes { builder.mkp.yieldUnescaped("<![CDATA[${this.notes ?: ''}]]>") }
            builder.description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            builder.stories() {
                this.stories.sort { it.uid }.each { _story ->
                    story(uid: _story.uid)
                }
            }
            builder.activities() {
                this.activities.each { _activity ->
                    _activity.xml(builder)
                }
            }
            builder.attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
            exportDomainsPlugins(builder)
        }
    }

}
