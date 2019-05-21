/*
 * Copyright (c) 2019 Kagilum SAS
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors: Vincent Barrier (vbarrier@kagilum.com)
 *
 */

package org.icescrum.core.domain

class Hook implements Cloneable, Serializable {

    static final long serialVersionUID = -6800258407987149001L

    String url
    Long workspaceId
    String workspaceType
    Set<String> events
    String eventMessageRendererClass

    boolean enabled = true

    int countErrors = 0
    String lastError

    Date dateCreated
    Date lastUpdated

    static hasMany = [events: String]

    static constraints = {
        workspaceId(nullable: true, validator: { workspaceId, hook ->
            (workspaceId == null && hook.workspaceType != null) || (workspaceId != null && hook.workspaceType == null) ? 'invalid' : true
        })
        workspaceType(nullable: true, validator: { workspaceType, hook ->
            (workspaceType == null && hook.workspaceId != null) || (workspaceType != null && hook.workspaceId == null) ? 'invalid' : true
        }, shared: 'keyMaxSize')
        events(nullable: false)
        lastError(nullable: true)
        eventMessageRendererClass(nullable: true)
        url(maxSize: 1000)
    }

    static mapping = {
        cache true
        table 'is_hook'
        workspaceId index: 'indx_hook'
        workspaceType index: 'indx_hook'
        url length: 1000, type: 'text'
        lastError type: 'text'
    }

    static List<Hook> queryFindAllByWorkspaceTypeAndWorkspaceIdAndEventsFromList(String workspaceType, long workspaceId, events) {
        executeQuery("""
                SELECT distinct h
                FROM Hook h inner join h.events events
                WHERE h.enabled = :enabled 
                AND h.workspaceId = :workspaceId
                AND h.workspaceType like :workspaceType
                AND events in (:events)""", [enabled: true, workspaceId: workspaceId, workspaceType: workspaceType, events: events])
    }

    static List<Hook> queryFindAllByWorkspaceTypeNullAndWorkspaceIdNullAndEventsFromList(events) {
        executeQuery("""
                SELECT distinct h
                FROM Hook h inner join h.events events
                WHERE h.enabled = :enabled
                AND h.workspaceId is NULL
                AND h.workspaceType is NULL
                AND events in (:events)""", [enabled: true, events: events])
    }

    def xml(builder) {
        builder.hook() {
            builder.url { builder.mkp.yieldUnescaped("<![CDATA[${this.url}]]>") }
            builder.workspaceId(this.workspaceId)
            builder.workspaceType { builder.mkp.yieldUnescaped("<![CDATA[${this.workspaceType}]]>") }
            builder.events { builder.mkp.yieldUnescaped("<![CDATA[${this.events?.join(',')}]]>") }
            builder.enabled(this.enabled)
            builder.countErrors(this.countErrors)
            builder.lastError { builder.mkp.yieldUnescaped("<![CDATA[${this.lastError}]]>") }
            builder.lastUpdated(this.lastUpdated)
            builder.dateCreated(this.dateCreated)
            exportDomainsPlugins(builder)
        }
    }
}