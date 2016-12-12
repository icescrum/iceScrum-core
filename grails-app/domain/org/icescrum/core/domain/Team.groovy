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


package org.icescrum.core.domain

import grails.plugin.springsecurity.acl.AclUtilService
import grails.util.Holders
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.services.SecurityService
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.NotFoundException

class Team implements Serializable, Comparable {

    static final long serialVersionUID = 813639045272976126L

    String name
    int velocity = 0
    String description

    Date dateCreated
    Date lastUpdated
    String uid

    static hasMany = [
            products: Product,
            members : User
    ]

    static transients = ['scrumMasters', 'owner', 'invitedScrumMasters', 'invitedMembers']

    def scrumMasters = null

    static belongsTo = [Product]

    static constraints = {
        description(nullable: true, maxSize: 1000)
        name(blank: false, unique: true)
    }

    static mapping = {
        cache true
        table 'is_team'
    }

    static findExceptProduct(Long id, term, params) {
        executeQuery(
                "SELECT DISTINCT t " +
                        "FROM org.icescrum.core.domain.Team as t " +
                        "WHERE lower(t.name) like lower(:term) and t.id not in " +
                        "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                        "INNER JOIN t2.products as p " +
                        "WHERE p.id = :p) ", [p: id, term: "%$term%"], params ?: [:])
    }

    static findAllByOwner(String user, params, String term = '%%') {
        executeQuery("""SELECT DISTINCT t
                        FROM org.icescrum.core.domain.Team as t,
                             grails.plugin.springsecurity.acl.AclClass as ac,
                             grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                             grails.plugin.springsecurity.acl.AclSid as acl
                        WHERE ac.className = 'org.icescrum.core.domain.Team'
                        AND ai.aclClass = ac.id
                        AND ai.owner.sid = :sid
                        AND acl.id = ai.owner
                        AND t.id = ai.objectId
                        AND lower(t.name) LIKE lower(:term)""", [sid: user, term: term], params ?: [:])
    }


    private static findAllBySM(String user, params, String term = '%%') {
        executeQuery("""SELECT t
                        FROM org.icescrum.core.domain.Team as t,
                             grails.plugin.springsecurity.acl.AclClass as ac,
                             grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                             grails.plugin.springsecurity.acl.AclSid as acl,
                             grails.plugin.springsecurity.acl.AclEntry as ae
                        WHERE ac.className = 'org.icescrum.core.domain.Team'
                        AND ai.aclClass = ac.id
                        AND acl.sid = :sid
                        AND acl.id = ae.sid.id
                        AND ae.mask = :smMask
                        AND ai.id = ae.aclObjectIdentity.id
                        AND t.id = ai.objectId
                        AND lower(t.name) LIKE lower(:term)""", [sid: user, term: term, smMask: BasePermission.WRITE.mask], params ?: [:])
    }

    static List<Team> findAllByOwnerOrSM(String user, params, String term = '%%') {
        // Union of queries is not allowed in HQL so we do it manually
        def ownerTeams = findAllByOwner(user, params, term)
        def smTeams = findAllBySM(user, params, term)
        def teams = smTeams + ownerTeams
        return teams.unique { it.id }
    }

    static Integer countByOwnerOrSM(String user, params, String term = '%%') {
        return findAllByOwnerOrSM(user, params, term).size()
    }

    static Integer countActiveProductsByTeamOwner(String username, params) {
        executeQuery("""SELECT COUNT(DISTINCT p.id)
                        FROM org.icescrum.core.domain.Product p,
                             org.icescrum.core.domain.Team t,
                             grails.plugin.springsecurity.acl.AclClass ac,
                             grails.plugin.springsecurity.acl.AclObjectIdentity ai,
                             grails.plugin.springsecurity.acl.AclSid acl
                        INNER JOIN t.products p
                        WHERE p.preferences.archived = false
                        AND t.id = ai.objectId
                        AND acl.id = ai.owner
                        AND ai.owner.sid = :sid
                        AND ai.aclClass = ac.id
                        AND ac.className = 'org.icescrum.core.domain.Team'""", [sid: username], params ?: [:])[0]
    }

    static List<Product> findAllActiveProductsByTeamOwner(String username, String term = '%%', params) {
        executeQuery("""SELECT DISTINCT p
                        FROM org.icescrum.core.domain.Product p,
                             org.icescrum.core.domain.Team t,
                             grails.plugin.springsecurity.acl.AclClass ac,
                             grails.plugin.springsecurity.acl.AclObjectIdentity ai,
                             grails.plugin.springsecurity.acl.AclSid acl
                        INNER JOIN t.products p
                        WHERE p.preferences.archived = false
                        AND lower(p.name) LIKE lower(:term)
                        AND t.id = ai.objectId
                        AND acl.id = ai.owner
                        AND ai.owner.sid = :sid
                        AND ai.aclClass = ac.id
                        AND ac.className = 'org.icescrum.core.domain.Team'""", [sid: username, term: term], params ?: [:])
    }

    static countByOwner(String user, params, String term = '%%') {
        executeQuery("""SELECT DISTINCT COUNT(t.id)
                        FROM org.icescrum.core.domain.Team as t,
                             grails.plugin.springsecurity.acl.AclClass as ac,
                             grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                             grails.plugin.springsecurity.acl.AclSid as acl
                        WHERE ac.className = 'org.icescrum.core.domain.Team'
                        AND ai.aclClass = ac.id
                        AND ai.owner.sid = :sid
                        AND acl.id = ai.owner
                        AND t.id = ai.objectId
                        AND lower(t.name) LIKE lower(:term)""", [sid: user, term: term], params ?: [:])
    }

    static namedQueries = {

        productTeam { p, u ->
            products {
                idEq(p)
            }
            members {
                idEq(u)
            }
        }


        teamLike { term ->
            ilike("name", "%$term%")
        }

    }


    def getScrumMasters() {
        def aclUtilService = (AclUtilService) Holders.grailsApplication.mainContext.getBean('aclUtilService');
        if (this.scrumMasters) {
            this.scrumMasters
        } else if (this.id) {
            def acl = aclUtilService.readAcl(this.getClass(), this.id)
            def users = acl.entries.findAll { it.permission in SecurityService.scrumMasterPermissions }*.sid*.principal;
            if (users) {
                return User.findAll("from User as u where u.username in (:users)", [users: users], [cache: true])
            } else {
                return []
            }
        } else {
            []
        }
    }

    def getOwner() {
        if (this.id) {
            def acl = retrieveAclTeam()
            return User.findByUsername(acl.owner.principal, [cache: true])
        } else {
            null
        }
    }

    List<Invitation> getInvitedScrumMasters() {
        return Invitation.findAllByTypeAndTeamAndFutureRole(InvitationType.TEAM, this, Authority.SCRUMMASTER)
    }

    List<Invitation> getInvitedMembers() {
        return Invitation.findAllByTypeAndTeamAndFutureRole(InvitationType.TEAM, this, Authority.MEMBER)
    }

    boolean equals(o) {
        if (this.is(o)) return true;
        if (getClass() != o.class) return false;

        Team team = (Team) o;

        if (name != team.name) return false;

        return true;
    }

    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        return result
    }

    def beforeValidate() {
        // Create uid before first save object
        if (!this.id && !this.uid) {
            this.uid = (this.name).encodeAsMD5()
        }
    }

    @Override
    int compareTo(Object t) {
        return this.name?.compareTo(t.name)
    }

    private Acl retrieveAclTeam() {
        def aclUtilService = (AclUtilService) Holders.grailsApplication.mainContext.getBean('aclUtilService')
        def acl
        try {
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        } catch (NotFoundException e) {
            if (log.debugEnabled) {
                log.debug(e.getMessage())
                log.debug("fixing unsecured team ... admin user will be the owner")
            }
            def securityService = (SecurityService) Holders.grailsApplication.mainContext.getBean('securityService')
            securityService.secureDomain(this)
            securityService.changeOwner(User.findById(1), this)
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        }
        return acl
    }

    static Team withTeam(long id) {
        Team team = get(id)
        if (!team) {
            throw new ObjectNotFoundException(id, 'Team')
        }
        return team
    }

    def xml(builder) {
        builder.team(uid: this.uid) {
            builder.velocity(this.velocity)
            builder.dateCreated(this.dateCreated)
            builder.lastUpdated(this.lastUpdated)
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            builder.scrumMasters() {
                this.scrumMasters?.each { _user ->
                    builder.user(uid: _user.uid)
                }
            }

            builder.members() {
                this.members?.each { _user ->
                    _user.xml(builder)
                }
            }
            builder.owner() {
                this.owner.xml(builder)
            }
            exportDomainsPlugins(builder)
        }
    }
}
