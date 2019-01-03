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

import org.icescrum.core.domain.Portfolio;
import org.icescrum.core.domain.Project;
import org.icescrum.core.domain.Team;
import org.icescrum.core.services.SecurityService;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.access.expression.SecurityExpressionRoot;
import org.springframework.security.core.Authentication;

import java.io.Serializable;
import java.util.Map;

// Used in services through @PreAuthorize annotations
// Don't provide methods without params since there is no request to look into
// Methods with ID as param are probably useless since services seem to always have an object as param
public class MethodScrumExpressionRoot extends SecurityExpressionRoot {

    private SecurityService securityService;

    public void setSecurityService(SecurityService securityService) {
        this.securityService = securityService;
    }

    public MethodScrumExpressionRoot(Authentication a) {
        super(a);
    }

    private PermissionEvaluator permissionEvaluator;
    private Object filterObject;
    private Object returnObject;
    public final String read = "read";
    public final String write = "write";
    public final String create = "create";
    public final String delete = "delete";
    public final String admin = "administration";

    public boolean hasPermission(Object target, Object permission) {
        return permissionEvaluator.hasPermission(authentication, target, permission);
    }

    public boolean hasPermission(Object targetId, String targetType, Object permission) {
        return permissionEvaluator.hasPermission(authentication, (Serializable) targetId, targetType, permission);
    }

    public void setFilterObject(Object filterObject) {
        this.filterObject = filterObject;
    }

    public Object getFilterObject() {
        return filterObject;
    }

    public void setReturnObject(Object returnObject) {
        this.returnObject = returnObject;
    }

    public Object getReturnObject() {
        return returnObject;
    }

    public void setPermissionEvaluator(PermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    public boolean inProject(Project p) {
        return securityService.inProject(p, super.authentication);
    }

    public boolean inProject(Map p) {
        return securityService.inProject(p != null ? p.get("id") : null, super.authentication);
    }

    public boolean inProject(long p) {
        return securityService.inProject(p, super.authentication);
    }

    public boolean inTeam(Team t) {
        return securityService.inTeam(t, super.authentication);
    }

    public boolean inTeam(long t) {
        return securityService.inTeam(t, super.authentication);
    }

    public boolean productOwner(Map p) {
        return securityService.productOwner(p != null ? p.get("id") : null, super.authentication);
    }

    public boolean productOwner(long p) {
        return securityService.productOwner(p, super.authentication);
    }

    public boolean productOwner(Project p) {
        return securityService.productOwner(p, super.authentication);
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

    public boolean stakeHolder(long p) {
        return securityService.stakeHolder(p, super.authentication, false);
    }

    public boolean stakeHolder(Map p) {
        return securityService.stakeHolder(p != null ? p.get("id") : null, super.authentication, false);
    }

    public boolean stakeHolder(Project p) {
        return securityService.stakeHolder(p, super.authentication, false);
    }

    public boolean stakeHolder(Project p, boolean onlyPrivate) {
        return securityService.stakeHolder(p, super.authentication, onlyPrivate);
    }

    public boolean stakeHolder(long p, boolean onlyPrivate) {
        return securityService.stakeHolder(p, super.authentication, onlyPrivate);
    }

    public boolean stakeHolder(Map p, boolean onlyPrivate) {
        return securityService.stakeHolder(p != null ? p.get("id") : null, super.authentication, onlyPrivate);
    }

    public boolean businessOwner(Portfolio portfolio) {
        return securityService.businessOwner(portfolio, super.authentication);
    }

    public boolean portfolioStakeHolder(Portfolio portfolio) {
        return securityService.portfolioStakeHolder(portfolio, super.authentication);
    }

    public boolean owner(Object o) {
        return securityService.owner(o, super.authentication);
    }

    public boolean archivedProject(long p) {
        return securityService.archivedProject(p);
    }

    public boolean archivedProject(Map p) {
        return securityService.archivedProject(p != null ? p.get("id") : null);
    }

    public boolean archivedProject(Project p) {
        return securityService.archivedProject(p);
    }
}
