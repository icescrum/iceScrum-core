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
 *
 */
package org.icescrum.core.domain

import grails.util.Holders
import org.springframework.security.acls.domain.BasePermission

class Activity implements Serializable, Comparable {

    static final CODE_DELETE = 'delete'
    static final CODE_SAVE = 'save'
    static final CODE_UPDATE = 'update'

    Date dateCreated
    Date lastUpdated
    String code
    String label
    String field
    String beforeValue
    String afterValue
    String afterLabel
    Long parentRef
    String parentType

    User poster // BelongsTo or not belongTo ? that's the question

    static transients = ['important']

    static constraints = {
        code blank: false
        label blank: false
        field(nullable: true, validator: { newField, activity -> (activity.beforeValue == null && activity.afterValue == null) || newField != null ?: 'invalid' })
        beforeValue nullable: true
        afterValue nullable: true
        afterLabel nullable: true
        parentType blank: false
    }

    static mapping = {
        label type: "text"
        beforeValue type: "text" // TODO check if relevant
        afterValue type: "text" // TODO check if relevant
        afterLabel type: "text"
        cache true
        table 'is_activity'
    }

    @Override
    int compareTo(Object o) {
        parentType.compareTo(o.parentType) ?:
                parentRef.compareTo(o.parentRef) ?:
                        dateCreated.compareTo(o.dateCreated) ?:
                                code.compareTo(o.code) ?:
                                        field?.compareTo(o.field) ?:
                                                0
    }

    boolean getImportant() {
        code in Holders.grailsApplication.config.icescrum.activities.important
    }

    // May not work on ORACLE
    static List<List> storyActivities(User user) {
        def projects = Project.findAllByRole(user, [BasePermission.WRITE, BasePermission.READ], [cache: true], true, false)
        def activitiesAndStories = []
        if (projects) {
            activitiesAndStories = executeQuery("""SELECT DISTINCT a, s
                        FROM org.icescrum.core.domain.Activity as a, org.icescrum.core.domain.Story as s
                        WHERE a.parentType = 'story'
                        AND a.poster.id != :uid
                        AND a.parentRef = s.id
                        AND s.backlog.id in (${projects*.id.join(',')})
                        ORDER BY a.dateCreated DESC""", [uid: user.id], [cache: true])
            activitiesAndStories = activitiesAndStories.findAll { it[0].important }
        }
        activitiesAndStories
    }

    static recentProjectActivity(Project project) {
        executeQuery("""SELECT a
                        FROM org.icescrum.core.domain.Activity as a
                        WHERE a.parentType = 'project'
                        AND a.parentRef = :p
                        ORDER BY a.dateCreated DESC""", [p: project.id], [max: 15])
    }

    static recentStoryActivity(Project project) {
        executeQuery("""SELECT a
                        FROM org.icescrum.core.domain.Activity as a, org.icescrum.core.domain.Story as s
                        WHERE a.parentType = 'story'
                        AND a.parentRef = s.id
                        AND NOT (a.code LIKE 'task')
                        AND s.backlog = :p
                        ORDER BY a.dateCreated DESC""", [p: project], [max: 15])
    }

    def xml(builder) {
        builder.activity() {
            builder.poster(uid: this.poster.uid)
            builder.dateCreated(this.dateCreated)
            builder.lastUpdated(this.lastUpdated)
            builder.code(this.code)
            builder.label { builder.mkp.yieldUnescaped("<![CDATA[${this.label}]]>") }
            builder.field { builder.mkp.yieldUnescaped("<![CDATA[${this.field}]]>") }
            builder.beforeValue { builder.mkp.yieldUnescaped("<![CDATA[${this.beforeValue ?: ''}]]>") }
            builder.afterValue { builder.mkp.yieldUnescaped("<![CDATA[${this.afterValue ?: ''}]]>") }
            builder.afterLabel { builder.mkp.yieldUnescaped("<![CDATA[${this.afterLabel ?: ''}]]>") }
            builder.parentRef(this.parentRef)
            builder.parentType(this.parentType)
            exportDomainsPlugins(builder)
        }
    }
}
