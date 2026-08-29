/*
 * Copyright (c) 2015 Kagilum SAS
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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */

package org.icescrum.core.security;

import org.grails.web.util.GrailsApplicationAttributes;
import org.icescrum.core.domain.Portfolio;
import org.icescrum.core.domain.Project;
import org.icescrum.core.domain.Team;
import org.icescrum.core.services.SecurityService;
import org.springframework.security.core.Authentication;
import org.icescrum.core.security.OAuth2Support;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.WebSecurityExpressionRoot;

// Used in controllers through @Secured annotations
// Methods with object as params may seem useless but they are used when called when the param is null
public class WebScrumExpressionRoot extends WebSecurityExpressionRoot {

    private org.icescrum.core.services.SecurityService securityService;

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public WebScrumExpressionRoot(Authentication a, FilterInvocation fi) {
        super(a, fi);
    }

    public boolean permitAllWeb() { return !OAuth2Support.isOAuth(getAuthentication()); }
    public boolean isAuthenticatedWeb() { return isAuthenticated() && !OAuth2Support.isOAuth(getAuthentication()); }
    public boolean hasAnyScopeOauth2(String... scopes) {
        return OAuth2Support.hasAnyScope(getAuthentication(), scopes);
    }

    public boolean inProject(Project p) {
        return securityService.inProject(p, getAuthentication());
    }
    public boolean inProject(long p) {
        return securityService.inProject(p, getAuthentication());
    }
    public boolean inProject() {
        return inProject(null);
    }

    public boolean inTeam(Team t) {
        return securityService.inTeam(t, getAuthentication());
    }
    public boolean inTeam(long t) {
        return securityService.inTeam(t, getAuthentication());
    }
    public boolean inTeam() {
        return inTeam(null);
    }

    public boolean productOwner() {
        return securityService.productOwner(null, getAuthentication());
    }
    public boolean productOwner(long p) {
        return securityService.productOwner(p, getAuthentication());
    }
    public boolean productOwner(Project p) {
        return securityService.productOwner(p, getAuthentication());
    }

    public boolean scrumMaster() {
        return securityService.scrumMaster(null, getAuthentication());
    }
    public boolean scrumMaster(long t) {
        return securityService.scrumMaster(t, getAuthentication());
    }
    public boolean scrumMaster(Team t) {
        return securityService.scrumMaster(t, getAuthentication());
    }
    public boolean scrumMaster(Project p) {
        Team team = p.getTeam();
        return team != null && securityService.scrumMaster(team, getAuthentication());
    }

    public boolean stakeHolder() {
        return securityService.stakeHolder(null, getAuthentication(), false, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }
    public boolean stakeHolder(long p) {
        return securityService.stakeHolder(p, getAuthentication(), false, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }
    public boolean stakeHolder(Project p) {
        return securityService.stakeHolder(p, getAuthentication(), false, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }

    public boolean businessOwner() {
        return securityService.businessOwner(null, getAuthentication());
    }
    public boolean businessOwner(long portfolioId) {
        return securityService.businessOwner(portfolioId, getAuthentication());
    }
    public boolean businessOwner(Portfolio portfolio) {
        return securityService.businessOwner(portfolio, getAuthentication());
    }

    public boolean portfolioStakeHolder() {
        return securityService.portfolioStakeHolder(null, getAuthentication());
    }
    public boolean portfolioStakeHolder(long portfolioId) {
        return securityService.portfolioStakeHolder(portfolioId, getAuthentication());
    }
    public boolean portfolioStakeHolder(Portfolio portfolio) {
        return securityService.portfolioStakeHolder(portfolio, getAuthentication());
    }

    public boolean owner() {
        return securityService.owner(null, getAuthentication());
    }
    public boolean owner(Object o) {
        return securityService.owner(o, getAuthentication());
    }

    public boolean archivedProject(Project p) {
        return securityService.archivedProject(p);
    }
    public boolean archivedProject() {
        return securityService.archivedProject(null);
    }
    public boolean archivedProject(long p) {
        return securityService.archivedProject(p);
    }

    public boolean appEnabledProject(String appDefinitionId) {
        return securityService.appEnabledProject(appDefinitionId);
    }
}
