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
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */

package org.icescrum.core.services

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.acl.AclClass
import grails.plugin.springsecurity.acl.AclObjectIdentity
import grails.plugin.springsecurity.acl.AclSid
import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsHibernateUtil
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.domain.security.Authority
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.domain.PrincipalSid
import org.springframework.security.acls.model.*
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.springframework.util.Assert
import org.springframework.web.context.request.RequestContextHolder as RCH

import static org.springframework.security.acls.domain.BasePermission.*

class SecurityService {

    def aclUtilService
    def objectIdentityRetrievalStrategy
    def springSecurityService
    def grailsUrlMappingsHolder
    def grailsApplication
    def aclService
    def appService

    static final productOwnerPermissions = [BasePermission.WRITE]
    static final stakeHolderPermissions = [BasePermission.READ]
    static final teamMemberPermissions = [BasePermission.READ]
    static final scrumMasterPermissions = [BasePermission.WRITE]

    Acl secureDomain(o) {
        createAcl(objectIdentityRetrievalStrategy.getObjectIdentity(o))
    }

    void unsecureDomain(o) {
        aclUtilService.deleteAcl(GrailsHibernateUtil.unwrapIfProxy(o))
    }

    void changeOwner(User user, o) {
        aclUtilService.changeOwner(GrailsHibernateUtil.unwrapIfProxy(o), user.username)
        user.lastUpdated = new Date()
        user.save()
    }

    // Project
    void createProductOwnerPermissions(User user, Project project) {
        createPermissions(project, user, productOwnerPermissions)
    }
    void deleteProductOwnerPermissions(User user, Project project) {
        deletePermissions(project, user, productOwnerPermissions)
    }
    void createStakeHolderPermissions(User user, Project project) {
        createPermissions(project, user, stakeHolderPermissions)
    }
    void deleteStakeHolderPermissions(User user, Project project) {
        deletePermissions(project, user, stakeHolderPermissions)
    }

    // Team
    void createTeamMemberPermissions(User user, Team team) {
        createPermissions(team, user, teamMemberPermissions)
    }
    void deleteTeamMemberPermissions(User user, Team team) {
        deletePermissions(team, user, teamMemberPermissions)
    }
    void createScrumMasterPermissions(User user, Team team) {
        createPermissions(team, user, [WRITE, ADMINISTRATION])
    }
    void deleteScrumMasterPermissions(User user, Team team) {
        deletePermissions(team, user, [WRITE, ADMINISTRATION])
    }

    // Utility
    private void createPermissions(Object o, User user, List<Permission> permissions) {
        changePermissions(o, user, permissions, aclUtilService.&addPermission)
    }
    private void deletePermissions(Object o, User user, List<Permission> permissions) {
        changePermissions(o, user, permissions, aclUtilService.&deletePermission)
    }
    private void changePermissions(Object o, User user, List<Permission> permissions, Closure change) {
        def unwrappedObject = GrailsHibernateUtil.unwrapIfProxy(o)
        def username = user.username
        permissions.each { permission ->
            change(unwrappedObject, username, permission)
        }
        user.lastUpdated = new Date()
        user.save()
    }

    @SuppressWarnings("GroovyMissingReturnStatement")
    boolean inProject(project, auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        boolean authorized = productOwner(project, auth)
        if (!authorized) {
            def p
            if (!project) {
                def request = RCH.requestAttributes.currentRequest
                if (request.filtered) {
                    return request.inProject
                } else {
                    project = getProjectIdFromRequest(request)
                }
            } else if (project in Project) {
                p = project
                project = project.id
            }
            if (project) {
                def computeResult = {
                    if (!p) {
                        p = Project.get(project)
                    }
                    if (!p || !auth) {
                        return false
                    }
                    for (team in p.teams) {
                        if (inTeam(team, auth)) {
                            return true
                        }
                    }
                }
                authorized = computeResult()
            }
        }
        return authorized
    }

    boolean archivedProject(project) {
        def p
        if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            return false
        }
        if (!project) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered) {
                return request.archivedProject ?: false
            } else {
                project = getProjectIdFromRequest(request)
            }
        } else if (project in Project) {
            p = project
            project = project.id
        }
        if (project) {
            def computeResult = {
                if (!p) {
                    p = Project.get(project)
                }
                return p ? p.preferences.archived : false
            }
            return computeResult()
        } else {
            return false
        }
    }

    boolean inTeam(team, auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        teamMember(team, auth) || scrumMaster(team, auth)
    }

    Team openProjectTeam(Long projectId, Long principalId) {
        return Team.findTeamByProjectAndUser(projectId, principalId)
    }

    boolean scrumMaster(team, auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        def t = null
        if (!team) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered) {
                return request.scrumMaster
            } else {
                def parsedProject = getProjectIdFromRequest(request)
                if (parsedProject) {
                    def p = Project.get(parsedProject)
                    //case project doesn't exist
                    if (!p) {
                        return false
                    }
                    t = GrailsHibernateUtil.unwrapIfProxy(p.team)
                    team = t.id
                }
            }
        } else if (team in Team) {
            t = GrailsHibernateUtil.unwrapIfProxy(team)
            team = t.id
        }
        return isScrumMaster(team, auth, t) || isOwner(team, auth, grailsApplication.getDomainClass(Team.class.name).newInstance(), t)
    }

    boolean isScrumMaster(team, auth, t = null) {
        if (team) {
            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
                return true
            }
            def computeResult = {
                if (!t) {
                    t = Team.get(team)
                }
                if (!t || !auth) {
                    return false
                }
                return aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(t), SecurityService.scrumMasterPermissions)
            }
            return computeResult()
        } else {
            return false
        }
    }

    boolean stakeHolder(project, auth, onlyPrivate, controllerName = null) {
        if (!springSecurityService.isLoggedIn() && onlyPrivate) {
            return false
        }
        def p = null
        def stakeHolder = false
        if (!project) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered && !controllerName) {
                return request.stakeHolder
            } else {
                project = getProjectIdFromRequest(request)
                if (request.stakeHolder) {
                    stakeHolder = request.stakeHolder
                }
            }
        } else if (project in Project) {
            p = GrailsHibernateUtil.unwrapIfProxy(project)
            project = project.id
        }
        if (project) {
            if (!p) {
                p = Project.get(project)
            }
            if (!p || !auth) {
                return false
            }
            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
                return true
            }
            def computeResult = {
                def access = stakeHolder ?: p.preferences.hidden ? aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(p), SecurityService.stakeHolderPermissions) : !onlyPrivate
                if (access && controllerName) {
                    return !(controllerName in p.preferences.stakeHolderRestrictedViews?.split(','))
                } else {
                    return access
                }
            }
            return computeResult()
        } else {
            return false
        }
    }

    boolean productOwner(project, auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        def p = null
        if (!project) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered) {
                return request.productOwner
            } else {
                project = getProjectIdFromRequest(request)
            }
        } else if (project in Project) {
            p = GrailsHibernateUtil.unwrapIfProxy(project)
            project = project.id
        }
        def isPo = isProductOwner(project, auth, p)
        if (isPo) {
            return true
        } else if (project) {
            if (!p) {
                p = Project.get(project)
            }
            //case project doesn't exist
            if (!p) {
                return false
            }
            Team t = GrailsHibernateUtil.unwrapIfProxy(p.team)
            long team = t.id
            return isOwner(team, auth, grailsApplication.getDomainClass(Team.class.name).newInstance(), t)
        } else {
            return false
        }
    }

    boolean admin(auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        return SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)
    }

    boolean isProductOwner(project, auth, p = null) {
        if (project) {
            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
                return true
            }
            def computeResult = {
                if (!p) {
                    p = Project.get(project)
                }
                if (!p || !auth) {
                    return false
                }
                return aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(p), SecurityService.productOwnerPermissions)
            }
            return computeResult()
        } else {
            return false
        }
    }

    boolean teamMember(team, auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        def t
        if (!team) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered) {
                return request.inTeam
            } else {
                def parsedProject = getProjectIdFromRequest(request)
                if (parsedProject) {
                    if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
                        return true
                    }
                    t = openProjectTeam(parsedProject, springSecurityService.principal.id)
                    team = t?.id
                }
            }
        } else if (team in Team) {
            t = GrailsHibernateUtil.unwrapIfProxy(team)
            team = team.id
        }
        if (team) {
            if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
                return true
            }
            def computeResult = {
                if (!t) {
                    t = Team.get(team)
                }
                if (!t || !auth) {
                    return false
                }
                return aclUtilService.hasPermission(auth, GrailsHibernateUtil.unwrapIfProxy(t), SecurityService.teamMemberPermissions)
            }
            return computeResult()
        } else {
            return false
        }
    }

    Long getProjectIdFromRequest(request) {
        if (!request['project_id']) {
            def projectId = request.getParameter('project')
            if (!projectId) {
                def mappingInfo = grailsUrlMappingsHolder.match(request.forwardURI.replaceFirst(request.contextPath, ''))
                projectId = mappingInfo?.parameters?.getAt('project')
            }
            request['project_id'] = projectId?.decodeProjectKey()?.toLong()
        }
        return request['project_id']
    }

    MutableAcl createAcl(ObjectIdentity objectIdentity, parent = null) throws AlreadyExistsException {
        Assert.notNull objectIdentity, 'Object Identity required'
        // Check this object identity hasn't already been persisted
        if (aclService.retrieveObjectIdentity(objectIdentity)) {
            throw new AlreadyExistsException("Object identity '$objectIdentity' already exists")
        }
        // Need to retrieve the current principal, in order to know who "owns" this ACL (can be changed later on)
        PrincipalSid sid = new PrincipalSid(SCH.context.authentication)
        // Create the acl_object_identity row
        createObjectIdentity objectIdentity, sid, parent
        return aclService.readAclById(objectIdentity)
    }

    protected void createObjectIdentity(ObjectIdentity object, Sid owner, parent = null) {
        AclSid ownerSid = aclService.createOrRetrieveSid(owner, true)
        AclClass aclClass = aclService.createOrRetrieveClass(object.type, true)
        aclService.save new AclObjectIdentity(
                aclClass: aclClass,
                objectId: object.identifier,
                owner: ownerSid,
                parent: parent,
                entriesInheriting: true)
    }

    public boolean owner(domain, Authentication auth) {
        if (!springSecurityService.isLoggedIn()) {
            return false
        }
        if (SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            return true
        }
        def d = null
        def domainClass
        if (!domain) {
            def request = RCH.requestAttributes.currentRequest
            if (request.filtered) {
                return request.owner
            } else {
                def parsedProject = getProjectIdFromRequest(request)
                if (parsedProject) {
                    def p = Project.get(parsedProject)
                    //case project doesn't exist
                    if (!p) {
                        return false
                    }
                    d = GrailsHibernateUtil.unwrapIfProxy(p.team)
                    domain = d.id
                    domainClass = grailsApplication.getDomainClass(Team.class.name).newInstance()
                }
            }
        } else {
            d = GrailsHibernateUtil.unwrapIfProxy(domain)
            domainClass = d
            if (!d) {
                return false
            }
            domain = d.id
        }
        return isOwner(domain, auth, domainClass, d)
    }

    boolean isOwner(domain, auth, domainClass, d = null) {
        if (domain && domainClass) {
            def computeResult = {
                if (!d) {
                    d = domainClass.get(domain)
                }
                if (!d || !auth) {
                    return false
                }
                def acl = aclService.readAclById(objectIdentityRetrievalStrategy.getObjectIdentity(d))
                return acl.owner == new PrincipalSid((Authentication) auth)
            }
            return computeResult()
        } else {
            return false
        }
    }

    boolean appEnabledProject(String appDefinitionId) {
        Long project = getProjectIdFromRequest(RCH.requestAttributes.currentRequest)
        def authorized = false
        if (project) {
            Project _project = Project.load(project)
            if (_project) {
                authorized = appService.isEnabledAppForProject(_project, appDefinitionId)
            }
        }
        return authorized
    }

    def filterRequest(force = false) {
        def request = RCH.requestAttributes.currentRequest
        if (force) {
            request.filtered = false
        } else if (!request || request.filtered) {
            return
        }
        if (!request.filtered) {
            request.scrumMaster = scrumMaster(null, springSecurityService.authentication)
            request.productOwner = productOwner(null, springSecurityService.authentication)
            request.teamMember = teamMember(null, springSecurityService.authentication)
            request.stakeHolder = stakeHolder(null, springSecurityService.authentication, false)
            request.owner = owner(null, springSecurityService.authentication)
            request.inProject = request.scrumMaster || request.productOwner || request.teamMember
            request.inTeam = request.scrumMaster || request.teamMember
            request.admin = admin(springSecurityService.authentication)
        }
        if (request.owner && !request.inProject && !request.admin) {
            request.stakeholder = true
        }
        if ((request.inProject || request.stakeHolder) && archivedProject(null)) {
            request.scrumMaster = false
            request.productOwner = false
            request.teamMember = false
            request.inTeam = false
            request.inProject = false
            request.owner = false
            request.archivedProject = true
        }
        request.filtered = true
    }

    def getRolesRequest(force) {
        filterRequest(force)
        def request = RCH.requestAttributes.currentRequest
        return [productOwner: request.productOwner,
                scrumMaster : request.scrumMaster,
                teamMember  : request.teamMember,
                stakeHolder : request.stakeHolder,
                admin       : request.admin]
    }
}
