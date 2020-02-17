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

import grails.plugin.springsecurity.acl.AclUtilService
import grails.util.Holders
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.services.SecurityService
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Permission

class Portfolio {

    String name
    String fkey
    String description
    Date dateCreated
    Date lastUpdated
    PortfolioFramework framework = PortfolioFramework.DEFAULT

    static hasMany = [
            projects: Project,
            features: Feature,
            widgets : Widget
    ]

    static mapping = {
        cache true
        table 'is_portfolio'
        description(length: 5000)
        widgets(sort: 'position')
        features cascade: 'all-delete-orphan', sort: 'rank', batchSize: 25, cache: true
    }

    static constraints = {
        name(blank: false, maxSize: 200)
        fkey(blank: false, maxSize: 10, matches: /^[A-Z0-9]*[A-Z][A-Z0-9]*$/, unique: true)
        description(maxSize: 5000, nullable: true)
        projects(maxSize: 20)
        framework(nullable: true) // Required for migration but should not be null
    }

    static transients = ['businessOwners', 'stakeHolders', 'invitedBusinessOwners', 'invitedStakeHolders']

    List<User> businessOwners
    List<User> stakeHolders

    List<User> getBusinessOwners() {
        if (this.businessOwners != null) {
            this.businessOwners // Used only when portfolio is being imported
        } else if (this.id) {
            findAllUsersByPermissions(SecurityService.businessOwnerPermissions)
        } else {
            return []
        }
    }

    List<User> getStakeHolders() {
        if (this.stakeHolders != null) {
            this.stakeHolders // Used only when the portfolio is being imported
        } else if (this.id) {
            findAllUsersByPermissions(SecurityService.portfolioStakeHolderPermissions)
        } else {
            return []
        }
    }

    static List<Portfolio> findAllByTerm(params = [:], String term = '') {
        return createCriteria().list(params) {
            or {
                ilike 'name', "%${term}%"
                ilike 'fkey', "%${term}%"
            }
        }
    }

    static findAllByUser(User user, Map params = [:], String term = '%%') {
        executeQuery("""SELECT portfolio
                        FROM Portfolio portfolio,
                             grails.plugin.springsecurity.acl.AclObjectIdentity aoi,
                             grails.plugin.springsecurity.acl.AclEntry ae
                        WHERE aoi.objectId = portfolio.id
                        AND aoi.aclClass.className = 'org.icescrum.core.domain.Portfolio'
                        AND ae.aclObjectIdentity.id = aoi.id
                        AND ae.mask IN(:permissions)
                        AND lower(portfolio.name) LIKE lower(:term)
                        AND ae.sid.sid = :sid""", [sid: user.username, permissions: [BasePermission.WRITE, BasePermission.READ]*.mask, term: term], params)
    }


    List<Invitation> getInvitedBusinessOwners() {
        return Invitation.findAllByTypeAndPortfolioAndFutureRole(Invitation.InvitationType.PORTFOLIO, this, Authority.BUSINESSOWNER)
    }

    List<Invitation> getInvitedStakeHolders() {
        return Invitation.findAllByTypeAndPortfolioAndFutureRole(Invitation.InvitationType.PORTFOLIO, this, Authority.PORTFOLIOSTAKEHOLDER)
    }

    private List<User> findAllUsersByPermissions(List<Permission> permissions) {
        def aclUtilService = (AclUtilService) Holders.grailsApplication.mainContext.getBean('aclUtilService')
        def acl = aclUtilService.readAcl(this.getClass(), this.id)
        def users = acl.entries.findAll { it.permission in permissions }*.sid*.principal
        if (users) {
            return User.findAll("from User as u where u.username in (:users)", [users: users], [cache: true])
        } else {
            return []
        }
    }

    static Portfolio withPortfolio(long id) {
        Portfolio portfolio = Portfolio.get(id)
        if (!portfolio) {
            throw new ObjectNotFoundException(id, 'Portfolio')
        }
        return portfolio
    }

    enum PortfolioFramework {
        DEFAULT,
        SAFE
    }
}
