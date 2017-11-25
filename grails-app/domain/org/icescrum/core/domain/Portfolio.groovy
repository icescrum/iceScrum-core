/*
 * Copyright (c) 2017 Kagilum SAS
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
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.Project

class Portfolio {

    String fkey
    Date dateCreated
    Date lastUpdated

    static hasMany = [projects: Project]

    static mapping = {
        cache true
        table 'is_portfolio'
        fkey(index: 'portfolio_key_index')
    }

    static constraints = {
        fkey(blank: false, maxSize: 10, matches: /^[A-Z0-9]*$/, unique: true)
    }

    static Portfolio withPortfolio(long id) {
        Portfolio portfolio = get(id)
        if (!portfolio) {
            throw new ObjectNotFoundException(id, 'Portfolio')
        }
        return portfolio
    }
}
