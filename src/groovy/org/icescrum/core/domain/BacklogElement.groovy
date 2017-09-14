/*
 * Copyright (c) 2014 Kagilum.
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

import org.grails.comments.Commentable
import org.grails.taggable.Taggable
import org.icescrum.core.utils.ServicesUtils
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable

abstract class BacklogElement implements Attachmentable, Commentable, Serializable, Taggable {

    static final long serialVersionUID = -6800252500987149051L

    String description
    String notes
    String name
    Date dateCreated
    Date lastUpdated
    Date todoDate = new Date()
    int uid

    Integer comments_count = 0
    Integer attachments_count = 0

    TimeBox backlog
    SortedSet<Activity> activities

    static belongsTo = [backlog: TimeBox]

    static hasMany = [activities: Activity]

    static constraints = {
        description(maxSize: 3000, nullable: true)
        notes(maxSize: 5000, nullable: true)
        name(blank: false, maxSize: 100)
        attachments_count(nullable: true) // Must be nullable at creation for postgres because it doesn't set default value. The not nullable constraint is added in migration.
        comments_count(nullable: true) // Must be nullable at creation for postgres because it doesn't set default value. The not nullable constraint is added in migration.
    }

    static mapping = {
        cache true
        table 'icescrum2_backlogelement'
        description length: 3000
        notes length: 5000
        backlog lazy: true
        tablePerHierarchy false
    }

    def beforeValidate() {
        name = ServicesUtils.cleanXml(name)
        notes = ServicesUtils.cleanXml(notes)
        description = ServicesUtils.cleanXml(description)
    }
}
