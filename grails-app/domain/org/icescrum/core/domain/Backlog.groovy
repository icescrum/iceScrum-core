/*
 * Copyright (c) 2015 Kagilum SAS.
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
 */

package org.icescrum.core.domain

import grails.converters.JSON
import org.hibernate.ObjectNotFoundException


class Backlog {

    String  name
    String  code
    String  notes
    String  filter
    String  chartType

    User    owner
    boolean shared

    static belongsTo = [
            project: Project
    ]

    static mapping = {
        cache true
        table 'is_backlog'
        notes length: 5000
    }

    static transients = ['count', 'isDefault']

    static constraints = {
        name(blank: false, maxSize: 100)
        code(blank: false, maxSize: 100, unique: 'project', matches: '[a-z0-9_]+')
        notes(maxSize: 5000, nullable: true)
        owner(nullable: true)
        chartType(nullable: true) // Must be nullable at creation for postgres because it doesn't set default value. The not nullable constraint is added in migration.
    }

    static namedQueries = {
        getInProject { p, id ->
            project {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Backlog withBacklog(long projectId, long id) {
        Backlog backlog = (Backlog)getInProject(projectId, id).list()
        if (!backlog) {
            throw new ObjectNotFoundException(id, 'Backlog')
        }
        return backlog
    }

    def getCount() {
        def count
        try {
            count = Story.search(project.id, JSON.parse(filter), true)
        } catch (RuntimeException e) {
            count = 0
        }
        return count
    }

    def getIsDefault() {
        return owner == null
    }

    def xml(builder) {
        builder.backlog(id: this.id) {
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.code(this.code)
            builder.shared(this.shared)
            builder.filter(this.filter)
            builder.chartType(this.chartType)
            builder.notes { builder.mkp.yieldUnescaped("<![CDATA[${this.notes ?: ''}]]>") }
            if (this.owner) {
                builder.owner(uid: this.owner.uid)
            }
            exportDomainsPlugins(builder)
        }
    }
}
