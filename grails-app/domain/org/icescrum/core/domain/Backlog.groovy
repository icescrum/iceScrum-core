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

    String name
    String filter
    boolean shared

    static belongsTo = [
            product: Product,
            owner  : User
    ]

    static mapping = {
        cache true
        name(blank: false)
        table 'icescrum2_backlogs'
    }

    static transients = [
            'count', 'stories'
    ]

    static constraints = {
        name(blank: false, maxSize: 200, unique: true)
    }

    def getCount() {
        return Story.search(product, JSON.parse(this.filter), false, true)
    }

    def getStories() {
        return Story.search(product, JSON.parse(this.filter), false)
    }

    static namedQueries = {
        findAllByProductAndSharedOrOwner { p, s, u ->
            product {
                eq 'id', p
            }
            or {
                eq 'shared', s
                owner {
                    eq 'id', u
                }
            }
        }
    }
}