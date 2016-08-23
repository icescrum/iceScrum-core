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
 */

package org.icescrum.core.domain

import grails.converters.JSON

class Template implements Serializable {

    String name
    String itemClass
    String serializedData

    static belongsTo = [parentProduct: Product]

    static transients = ['data']

    static constraints = {
        name blank: false, unique: 'parentProduct'
        itemClass blank: false
        serializedData blank: false
    }

    static mapping = {
        cache true
        serializedData type: "text"
        table 'icescrum2_template'
    }

    Map getData() {
        return JSON.parse(serializedData) as Map
    }
}