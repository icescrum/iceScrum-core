/*
 * Copyright (c) 2011 Kagilum / 2010 iceScrum Technlogies.
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
 * Authors:
 *
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.domain

class AcceptanceTest implements Serializable {

    String name
    String description
    int uid

    Date dateCreated
    Date lastUpdated

    static belongsTo = [
        creator: User,
        parentStory: Story
    ]

    static constraints = {
        description(nullable: true, maxSize: 1000)
        name(blank: false)
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT DISTINCT MAX(t.uid)
                   FROM org.icescrum.core.domain.AcceptanceTest as t, org.icescrum.core.domain.Story as s
                   WHERE t.parentStory = s
                   AND s.backlog.id = :pid """, [pid: pid])[0]?:0) + 1
    }
}
