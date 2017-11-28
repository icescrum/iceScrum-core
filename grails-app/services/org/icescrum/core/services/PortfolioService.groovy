/*
 * Copyright (c) 2017 Kagilum SAS.
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

package org.icescrum.core.services

import grails.transaction.Transactional
import org.icescrum.core.domain.Portfolio
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class PortfolioService {

    def securityService

    void save(Portfolio portfolio) {
        portfolio.save()
        securityService.secureDomain(portfolio)
        portfolio.save(flush: true)
    }

    @PreAuthorize('owner(#portfolio)')
    void delete(Portfolio portfolio) {
        portfolio.delete()
        securityService.unsecureDomain(portfolio)
    }
}
