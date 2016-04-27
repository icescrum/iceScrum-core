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


class Backlog {

    String  name
    String  filter
    String  code
    User    owner
    boolean shared

    static belongsTo = [
        product: Product
    ]

    static mapping = {
        cache true
        table 'icescrum2_backlog'
    }

    static transients = [
        'count', 'isDefault'
    ]

    static constraints = {
        owner(nullable:true)
        name(blank: false, maxSize: 200)
    }

    def getCount() {
        return Story.search(product.id, JSON.parse(filter), false, true)
    }

    def getIsDefault() {
        return owner == null
    }
}