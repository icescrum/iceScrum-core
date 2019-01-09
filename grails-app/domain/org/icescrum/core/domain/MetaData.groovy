/*
 * Copyright (c) 2018 Kagilum SAS.
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

class MetaData implements Serializable, Comparable {

    String metaKey
    String metaValue

    Long parentRef
    String parentType

    Date dateCreated
    Date lastUpdated

    static constraints = {
        metaKey blank: false, shared: 'keyMaxSize'
        metaValue nullable: true
        parentType blank: false, shared: 'keyMaxSize'
    }

    static mapping = {
        cache true
        table 'is_metadata'
        parentRef index: 'metadata_parent_ref_index'
        parentType index: 'metadata_parent_type_index'
        metaKey index: 'metadata_key_index'
        metaValue type: 'text'
    }

    @Override
    int compareTo(Object o) {
        return parentType.compareTo(o.parentType) ?:
               parentRef.compareTo(o.parentRef) ?:
               dateCreated.compareTo(o.dateCreated) ?:
               metaKey.compareTo(o.metaKey) ?: 0
    }

    def xml(builder) {
        builder.metaData() {
            builder.dateCreated(this.dateCreated)
            builder.lastUpdated(this.lastUpdated)
            builder.metaKey(this.metaKey)
            builder.metaValue { builder.mkp.yieldUnescaped("<![CDATA[${this.metaValue}]]>") }
            builder.parentRef(this.parentRef)
            builder.parentType(this.parentType)
            exportDomainsPlugins(builder)
        }
    }
}