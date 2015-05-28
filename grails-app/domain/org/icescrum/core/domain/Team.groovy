/*
 * Copyright (c) 2010 iceScrum Technologies.
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

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.preferences.TeamPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumTeamEvent
import org.icescrum.core.services.SecurityService
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.core.context.SecurityContextHolder as SCH

class Team implements Serializable, Comparable {

    static final long serialVersionUID = 813639045272976126L

    String name
    int velocity = 0
    String description

    Date dateCreated
    Date lastUpdated
    String uid

    TeamPreferences preferences

    static hasMany = [
            products: Product,
            members: User
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
        preferences lazy: true
        table 'icescrum2_team'
    }

    static members(Team team, params) {
        executeQuery('select distinct t.members as m from org.icescrum.core.domain.Team as t where t.id=:id', [id: team.id], params ?: [:])
    }

    static products(Team team, params) {
        executeQuery('select distinct t.products as p from org.icescrum.core.domain.Team as t where t.id=:id', [id: team.id], params ?: [:])
    }

    static exceptMember(Long id, String term, params) {
        executeQuery(
                "SELECT DISTINCT t " +
                        "FROM org.icescrum.core.domain.Team as t " +
                        "WHERE t.id not in " +
                        "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                        "INNER JOIN t2.members as m " +
                        "WHERE m.id = :u) AND lower(t.name) like :term " +
                        "AND t.preferences.allowNewMembers = :allow ", [allow: true, term: "%$term%", u: id], params ?: [:])
    }

    static countExceptMember(Long id, params) {
        executeQuery(
                "SELECT DISTINCT count(t) " +
                        "FROM org.icescrum.core.domain.Team as t " +
                        "WHERE t.id not in " +
                        "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                        "INNER JOIN t2.members as m " +
                        "WHERE m.id = :u) AND t.preferences.allowNewMembers = :allow", [allow: true, u: id], params ?: [:])
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
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass as ac,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity as ai,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid as acl
                        WHERE ac.className = 'org.icescrum.core.domain.Team'
                        AND ai.aclClass = ac.id
                        AND ai.owner.sid = :sid
                        AND acl.id = ai.owner
                        AND t.id = ai.objectId
                        AND t.name LIKE :term""", [sid: user, term: term], params ?: [:])
    }

    static Integer countActiveProductsByTeamOwner(String username, params) {
        executeQuery("""SELECT COUNT(DISTINCT p.id)
                        FROM org.icescrum.core.domain.Product p,
                             org.icescrum.core.domain.Team t,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass ac,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity ai,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid acl
                        INNER JOIN t.products p
                        WHERE p.preferences.archived = false
                        AND t.id = ai.objectId
                        AND acl.id = ai.owner
                        AND ai.owner.sid = :sid
                        AND ai.aclClass = ac.id
                        AND ac.className = 'org.icescrum.core.domain.Team'""", [sid: username], params ?: [:])[0]
    }

    static countByOwner(String user, params, String term = '%%') {
        executeQuery("""SELECT DISTINCT COUNT(t.id)
                        FROM org.icescrum.core.domain.Team as t,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass as ac,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity as ai,
                             org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid as acl
                        WHERE ac.className = 'org.icescrum.core.domain.Team'
                        AND ai.aclClass = ac.id
                        AND ai.owner.sid = :sid
                        AND acl.id = ai.owner
                        AND t.id = ai.objectId
                        AND t.name LIKE :term""", [sid: user, term: term], params ?: [:])
    }

    static findAllByRole(String user, List<BasePermission> permission, params) {
        executeQuery("SELECT DISTINCT t "+
                        "From org.icescrum.core.domain.Team as t, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass as ac, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity as ai, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid as acl, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclEntry as ae "+
                        "where "+
                        "ac.className = 'org.icescrum.core.domain.Team' "+
                        "AND ai.aclClass = ac.id "+
                        "AND acl.sid = :sid "+
                        "AND acl.id = ae.sid.id "+
                        "AND ae.mask IN(:p) "+
                        "AND ai.id = ae.aclObjectIdentity.id "+
                        "AND t.id = ai.objectId", [sid: user, p:permission*.mask ], params ?: [:])
    }

    //Not working on ORACLE
    static recentTeamsActivity(def uid) {
        executeQuery("SELECT DISTINCT a.activity, p2 " +
                "FROM grails.plugin.fluxiable.ActivityLink as a, org.icescrum.core.domain.Product as p2 " +
                "WHERE a.type='product' " +
                "and p2.id=a.activityRef " +
                "and p2.id in (SELECT DISTINCT p.id " +
                                "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                                "WHERE t.id in" +
                                        "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                                        "INNER JOIN t2.members as m " +
                                        "WHERE m.id = :uid))" +
                "ORDER BY a.activity.dateCreated DESC", [uid:uid], [cache:true,max: 15])
    }

    static Team findTeamByProductAndUser(Long productId, Long userId) {
        executeQuery("""SELECT DISTINCT t
                        FROM org.icescrum.core.domain.Team t
                        INNER JOIN t.members m
                        INNER JOIN t.products p
                        WHERE p.id = :productId
                        AND m.id = :userId""", [userId: userId, productId: productId], [cache:true])[0]
    }

    static namedQueries = {

        teamLike {term ->
            ilike("name", "%$term%")
        }
    }

    def getScrumMasters() {
        def aclUtilService = (AclUtilService) ApplicationHolder.application.mainContext.getBean('aclUtilService');
        if (this.scrumMasters) {
            this.scrumMasters
        } else if (this.id) {
            def acl = aclUtilService.readAcl(this.getClass(), this.id)
            def users = acl.entries.findAll{it.permission in SecurityService.scrumMasterPermissions}*.sid*.principal;
            if (users)
                return User.findAll("from User as u where u.username in (:users)",[users:users], [cache: true])
            else
                return null
        } else {
            null
        }
    }

    def getOwner() {
        if (this.id) {
            def acl = retrieveAclTeam()
            return User.findByUsername(acl.owner.principal,[cache: true])
        } else {
            null
        }
    }

    List<Invitation> getInvitedScrumMasters() {
        return Invitation.findAllByTypeAndTeamAndFutureRole(InvitationType.TEAM, this, Authority.SCRUMMASTER).list()
    }

    List<Invitation> getInvitedMembers() {
        return Invitation.findAllByTypeAndTeamAndFutureRole(InvitationType.TEAM, this, Authority.MEMBER).list()
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

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumTeamEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE, true))
        }
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumTeamEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE, true))
        }
    }

    def beforeValidate(){
        //Create uid before first save object
        if (!this.id && !this.uid){
            this.uid = (this.name).encodeAsMD5()
        }
    }

    @Override
    int compareTo(Object t) {
        return this.name?.compareTo(t.name)
    }

    private Acl retrieveAclTeam() {
        def aclUtilService = (AclUtilService) ApplicationHolder.application.mainContext.getBean('aclUtilService')
        def acl
        try {
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        } catch (NotFoundException e) {
            if (log.debugEnabled) {
                log.debug(e.getMessage())
                log.debug("fixing unsecured team ... admin user will be the owner")
            }
            def securityService = (SecurityService) ApplicationHolder.application.mainContext.getBean('securityService')
            securityService.secureDomain(this)
            securityService.changeOwner(User.findById(1), this)
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        }
        return acl
    }
}
