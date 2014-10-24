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
 *
 */
package org.icescrum.core.domain

class Activity implements Serializable, Comparable {

    static final CODE_DELETE = 'delete'
    static final CODE_SAVE = 'save'
    static final CODE_UPDATE = 'update'

    Date dateCreated
    Date lastUpdated
    String code
    String label
    String description
    String beforeValue
    String afterValue
    Long parentRef
    String parentType

    User poster // BelongsTo or not belongTo ? that's the question

    static constraints = {
        code blank: false
        label blank: false
        description nullable: true
        beforeValue nullable: true
        afterValue nullable: true
        parentType blank: false
    }

    static mapping = {
        label type: "text"
        description type: "text"
        beforeValue type: "text" // TODO check if relevant
        afterValue type: "text" // TODO check if relevant
        cache true
        table 'icescrum2_activity'
    }

    @Override
    int compareTo(Object o) {
        dateCreated.compareTo(o.dateCreated)
    }
}