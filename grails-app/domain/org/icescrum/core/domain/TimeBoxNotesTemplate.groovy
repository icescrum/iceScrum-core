/*
 * Copyright (c) 2017 Kagilum SAS
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
 * Colin Bontemps (cbontemps@kagilum.com)
 */
package org.icescrum.core.domain

import grails.converters.JSON
import org.hibernate.ObjectNotFoundException

class TimeBoxNotesTemplate implements Serializable {

    String name
    String header
    String footer
    String configsData

    static transients = ['configs']

    static belongsTo = [
            parentProject: Project
    ]

    static constraints = {
        name(blank: false, unique: 'parentProject')
        header(nullable: true, maxSize: 5000)
        footer(nullable: true, maxSize: 5000)
        configsData(blank: false, maxSize: 5000)
    }

    static mapping = {
        configsData length: 5000
        header length: 5000
        footer length: 5000
    }

    void setConfigs(List configs) {
        configsData = configs ? configs as JSON : null
    }

    List getConfigs() {
        configsData ? JSON.parse(configsData) as List : []
    }

    static TimeBoxNotesTemplate withTimeBoxNotesTemplate(long projectId, long id) {
        TimeBoxNotesTemplate template = (TimeBoxNotesTemplate) getInProject(projectId, id).list()
        if (!template) {
            throw new ObjectNotFoundException(id, 'TimeBoxNotesTemplate')
        }
        return template
    }

    def xml(builder) {
        builder.rnTemplate(this.id) {
            builder.name(this.name)
            builder.header(this.header)
            builder.footer(this.footer)
            builder.configsData(this.configsData)
            exportDomainsPlugins(builder)
        }
    }

    static namedQueries = {
        getInProject { p, id ->
            parentProject {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }
}
