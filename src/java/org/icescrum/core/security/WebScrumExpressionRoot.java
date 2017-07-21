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

import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes;
import org.icescrum.core.domain.Project;
import org.icescrum.core.domain.Team;
import org.icescrum.core.services.SecurityService;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.expression.WebSecurityExpressionRoot;

public class WebScrumExpressionRoot extends WebSecurityExpressionRoot implements ScrumExpressionRoot {

    private org.icescrum.core.services.SecurityService securityService;

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public WebScrumExpressionRoot(Authentication a, FilterInvocation fi) {
        super(a, fi);
    }

    public boolean inProject(Project p) {
        return securityService.inProject(p, super.authentication);
    }

    public boolean inProject(long p) {
        return securityService.inProject(p, super.authentication);
    }

    public boolean inProject() {
        return inProject(null);
    }

    public boolean inTeam(Team t) {
        return securityService.inTeam(t, super.authentication);
    }

    public boolean inTeam(long t) {
        return securityService.inTeam(t, super.authentication);
    }

    public boolean inTeam() {
        return inTeam(null);
    }


    public boolean productOwner() {
        return securityService.productOwner(null, super.authentication);
    }

    public boolean productOwner(long p) {
        return securityService.productOwner(p, super.authentication);
    }

    public boolean productOwner(Project p) {
        return securityService.productOwner(p, super.authentication);
    }

    public boolean teamMember() {
        return securityService.teamMember(null, super.authentication);
    }

    public boolean teamMember(long t) {
        return securityService.teamMember(t, super.authentication);
    }

    public boolean teamMember(Team t) {
        return securityService.teamMember(t, super.authentication);
    }

    public boolean teamMember(Project p) {
        Team team = p.getTeam();
        return team != null && securityService.teamMember(team, super.authentication);
    }

    public boolean scrumMaster() {
        return securityService.scrumMaster(null, super.authentication);
    }

    public boolean scrumMaster(long t) {
        return securityService.scrumMaster(t, super.authentication);
    }

    public boolean scrumMaster(Team t) {
        return securityService.scrumMaster(t, super.authentication);
    }

    public boolean scrumMaster(Project p) {
        Team team = p.getTeam();
        return team != null && securityService.scrumMaster(team, super.authentication);
    }

    public boolean stakeHolder() {
        return securityService.stakeHolder(null, super.authentication, false, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }

    public boolean stakeHolder(long p) {
        return securityService.stakeHolder(p, super.authentication, false, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }

    public boolean stakeHolder(Project p) {
        return securityService.stakeHolder(p, super.authentication, false, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }

    public boolean stakeHolder(Project p, boolean onlyPrivate) {
        return securityService.stakeHolder(p, super.authentication, onlyPrivate, this.request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE));
    }

    public boolean owner() {
        return securityService.owner(null, super.authentication);
    }

    public boolean owner(Object o) {
        return securityService.owner(o, super.authentication);
    }

    public boolean admin() {
        return securityService.admin(super.authentication);
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
}
