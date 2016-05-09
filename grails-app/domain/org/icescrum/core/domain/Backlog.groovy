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
    String  code
    String  notes
    String  filter
    User    owner
    boolean shared

    static belongsTo = [
        product: Product
    ]

    static mapping = {
        cache true
        table 'icescrum2_backlog'
        notes length: 5000
    }

    static transients = [
        'count', 'isDefault'
    ]

    static constraints = {
        name(blank: false, maxSize: 100)
        code(blank: false, maxSize: 100, unique: 'product', matches: '[a-z0-9_]+')
        notes(maxSize: 5000, nullable: true)
        owner(nullable: true)
    }

    def getCount() {
        return Story.search(product.id, JSON.parse(filter), true)
    }

    def getIsDefault() {
        return owner == null
    }
}