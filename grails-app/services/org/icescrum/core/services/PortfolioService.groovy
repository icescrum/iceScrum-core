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
import org.icescrum.core.domain.Invitation
import org.icescrum.core.domain.Portfolio
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.User
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

@Transactional
class PortfolioService extends IceScrumEventPublisher {

    def securityService
    def springSecurityService
    def userService

    void save(Portfolio portfolio, List<Project> projects, List<User> businessOwners, List<User> portfolioStakeHolders) {
        portfolio.save()
        projects.each { project ->
            addProjectToPortfolio(portfolio, project)
        }
        businessOwners.each { businessOwner ->
            addBusinessOwner(portfolio, businessOwner)
        }
        portfolioStakeHolders.each { portfolioStakeHolder ->
            addStakeHolder(portfolio, portfolioStakeHolder)
        }
        publishSynchronousEvent(IceScrumEventType.CREATE, portfolio)
        portfolio.save(flush: true)
    }

    void update(Portfolio portfolio, List<Project> projects, List<User> businessOwners, List<User> portfolioStakeHolders) {
        if (!portfolio.name?.trim()) {
            throw new BusinessException(code: 'is.portfolio.error.no.name')
        }
        if (projects != null) {
            portfolio.projects.collect().each { project ->
                if (!projects.contains(project)) {
                    portfolio.removeFromProjects(project)
                }
            }
            projects.each { project ->
                if (!portfolio.projects?.contains(project)) {
                    addProjectToPortfolio(portfolio, project)
                }
            }
        }
        if (businessOwners != null) {
            portfolio.businessOwners.each { businessOwner ->
                if (!businessOwners.contains(businessOwner)) {
                    removeBusinessOwner(portfolio, businessOwner)
                }
            }
            businessOwners.each { businessOwner ->
                if (!portfolio.businessOwners.contains(businessOwner)) {
                    addBusinessOwner(portfolio, businessOwner)
                }
            }
        }
        if (portfolioStakeHolders != null) {
            portfolio.stakeHolders.each { portfolioStakeHolder ->
                if (!portfolioStakeHolders.contains(portfolioStakeHolder)) {
                    removeStakeHolder(portfolio, portfolioStakeHolder)
                }
            }
            portfolioStakeHolders.each { portfolioStakeHolder ->
                if (!portfolio.stakeHolders.contains(portfolioStakeHolder)) {
                    addStakeHolder(portfolio, portfolioStakeHolder)
                }
            }
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, portfolio)
        portfolio.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, portfolio, dirtyProperties)
    }

    void delete(Portfolio portfolio) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, portfolio)
        portfolio.projects.collect().each { project -> // Use collect first to avoid mutating portfolio.projects
            portfolio.removeFromProjects(project)
        }
        portfolio.businessOwners.each { bo ->
            removeBusinessOwner(portfolio, bo)
        }
        portfolio.stakeHolders.each { psh ->
            removeStakeHolder(portfolio, psh)
        }
        portfolio.invitedStakeHolders*.delete()
        portfolio.invitedBusinessOwners*.delete()
        portfolio.delete()
        publishSynchronousEvent(IceScrumEventType.DELETE, portfolio, dirtyProperties)
    }

    List<Portfolio> getAllPortfoliosByUser(User user, String searchTerm = '%%') {
        def portfolios = Portfolio.findAllByUser(user, [sort: "name", order: "asc", cache: true], searchTerm)
        return portfolios
    }

    void addRole(portfolio, User user, int role) {
        if (role == Authority.BUSINESSOWNER) {
            addBusinessOwner(portfolio, user)
        } else if (role == Authority.PORTFOLIOSTAKEHOLDER) {
            addStakeHolder(portfolio, user)
        }
    }

    void managePortfolioEvents(Portfolio portfolio, Map oldMembers) {
        Map newMembers = getAllMembersPortfolioByRole(portfolio)
        if (portfolio.hasProperty('membersByRole')) {
            portfolio.membersByRole = newMembers
        } else {
            portfolio.metaClass.membersByRole = newMembers
        }
        publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, portfolio, [membersByRole: oldMembers])
        publishSynchronousEvent(IceScrumEventType.UPDATE, portfolio, [membersByRole: oldMembers])
    }

    Map getAllMembersPortfolioByRole(Portfolio portfolio) {
        def usersByRole = [:]
        def businessOwners = portfolio.businessOwners
        businessOwners?.each { User businessOwner ->
            if (!usersByRole.containsKey(businessOwner)) {
                usersByRole[businessOwner] = Authority.BUSINESSOWNER
            }
        }
        portfolio.stakeHolders?.each { User stakeHolder ->
            usersByRole[stakeHolder] = Authority.PORTFOLIOSTAKEHOLDER
        }
        return usersByRole
    }

    void managePortfolioInvitations(Portfolio porfolio, invitedBusinessOwners, invitedStakeHolders) {
        invitedBusinessOwners = invitedBusinessOwners*.toLowerCase()
        invitedStakeHolders = invitedStakeHolders*.toLowerCase()
        def type = Invitation.InvitationType.PORTFOLIO
        def currentInvitations = Invitation.findAllByTypeAndPortfolio(type, porfolio)
        def newInvitations = []
        assert !invitedBusinessOwners.intersect(invitedStakeHolders)
        newInvitations.addAll(invitedBusinessOwners.collect { [role: Authority.BUSINESSOWNER, email: it] })
        if (invitedStakeHolders) {
            newInvitations.addAll(invitedStakeHolders.collect { [role: Authority.PORTFOLIOSTAKEHOLDER, email: it] })
        }
        userService.manageInvitations(currentInvitations, newInvitations, type, porfolio)
    }

    private void addBusinessOwner(Portfolio portfolio, User businessOwner) {
        securityService.createBusinessOwnerPermissions businessOwner, portfolio
    }

    private void addStakeHolder(Portfolio portfolio, User portfolioStakeHolder) {
        securityService.createPortfolioStakeHolderPermissions portfolioStakeHolder, portfolio
    }

    private void removeBusinessOwner(Portfolio portfolio, User businessOwner) {
        securityService.deleteBusinessOwnerPermissions businessOwner, portfolio
    }

    private void removeStakeHolder(Portfolio portfolio, User portfolioStakeHolder) {
        securityService.deletePortfolioStakeHolderPermissions portfolioStakeHolder, portfolio
    }

    private void addProjectToPortfolio(Portfolio portfolio, Project project) {
        if (project.id && !project.productOwners.contains(springSecurityService.currentUser) && project.owner != springSecurityService.currentUser) {
            throw new BusinessException(code: 'is.portoflio.add.project.not.productOwner', args: [project.name])
        }
        if (project.portfolio) {
            throw new BusinessException(code: 'is.project.error.already.in.portfolio', args: [project.name])
        }
        portfolio.addToProjects(project)
    }
}
