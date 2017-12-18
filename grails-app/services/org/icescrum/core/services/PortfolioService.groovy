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
    def notificationEmailService
    def springSecurityService

    void save(Portfolio portfolio, List<Project> projects, List<User> businessOwners, List<User> portfolioStakeHolders) {
        portfolio.save()
        projects.each { project ->
            if (project.id && !project.productOwners.contains(springSecurityService.currentUser)) {
                throw new BusinessException(code: 'is.portoflio.add.project.not.productOwner', args: [project.name])
            }
            if (project.portfolio) {
                throw new BusinessException(code: 'is.project.error.already.in.portfolio', args: [project.name])
            }
            portfolio.addToProjects(project)
        }
        businessOwners.each { businessOwner ->
            securityService.createBusinessOwnerPermissions(businessOwner, portfolio)
        }
        portfolioStakeHolders.each { portfolioStakeHolder ->
            securityService.createPortfolioStakeHolderPermissions(portfolioStakeHolder, portfolio)
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
                    if (project.id && !project.productOwners.contains(springSecurityService.currentUser)) {
                        throw new BusinessException(code: 'is.portoflio.add.project.not.productOwner', args: [project.name])
                    }
                    if (project.portfolio) {
                        throw new BusinessException(code: 'is.project.error.already.in.portfolio', args: [project.name])
                    }
                    portfolio.addToProjects(project)
                }
            }
        }
        if (businessOwners != null) {
            portfolio.businessOwners.each { businessOwner ->
                if (!businessOwners.contains(businessOwner)) {
                    securityService.deleteBusinessOwnerPermissions(businessOwner, portfolio)
                }
            }
            businessOwners.each { businessOwner ->
                if (!portfolio.businessOwners.contains(businessOwner)) {
                    securityService.createBusinessOwnerPermissions(businessOwner, portfolio)
                }
            }
        }
        if (portfolioStakeHolders != null) {
            portfolio.stakeHolders.each { portfolioStakeHolder ->
                if (!portfolioStakeHolders.contains(portfolioStakeHolder)) {
                    securityService.deletePortfolioStakeHolderPermissions(portfolioStakeHolder, portfolio)
                }
            }
            portfolioStakeHolders.each { portfolioStakeHolder ->
                if (!portfolio.stakeHolders.contains(portfolioStakeHolder)) {
                    securityService.createPortfolioStakeHolderPermissions(portfolioStakeHolder, portfolio)
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
            securityService.deleteBusinessOwnerPermissions(bo, portfolio)
        }
        portfolio.stakeHolders.each { psh ->
            securityService.deletePortfolioStakeHolderPermissions(psh, portfolio)
        }
        portfolio.invitedStakeHolders*.delete()
        portfolio.invitedBusinessOwners*.delete()
        portfolio.delete()
        publishSynchronousEvent(IceScrumEventType.DELETE, portfolio, dirtyProperties)
    }

    List<Portfolio> getAllPortfoliosByUser(User user, String searchTerm = '') {
        def portfolios = Portfolio.findAllByUser(user, [sort: "name", order: "asc", cache: true], searchTerm)
        return portfolios
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
        manageInvitations(currentInvitations, newInvitations, type, porfolio)
    }

    private void manageInvitations(List<Invitation> currentInvitations, List newInvitations, Invitation.InvitationType type, Portfolio portfolio) {
        newInvitations.each {
            def email = it.email
            int role = it.role
            Invitation currentInvitation = currentInvitations.find { it.email == email }
            if (currentInvitation) {
                if (currentInvitation.futureRole != role) {
                    currentInvitation.futureRole = role
                    currentInvitation.save()
                }
            } else {
                def invitation = new Invitation(email: email, futureRole: role, type: type)
                if (type == Invitation.InvitationType.PORTFOLIO) {
                    invitation.portfolio = portfolio
                }
                invitation.save()
                try {
                    notificationEmailService.sendInvitation(invitation, springSecurityService.currentUser)
                } catch (MailException) {
                    throw new BusinessException(code: 'is.mail.error')
                }
            }
        }
        currentInvitations.findAll { currentInvitation ->
            !newInvitations*.email.contains(currentInvitation.email)
        }*.delete()
    }
}
