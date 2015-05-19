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
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.domain

import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.services.SecurityService
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumProductEvent
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl

class Product extends TimeBox implements Serializable, Attachmentable {

    static final long serialVersionUID = -8854429090297032383L

    int planningPokerGameType = PlanningPokerGame.FIBO_SUITE
    String name = ""
    ProductPreferences preferences
    String pkey
    SortedSet<Team> teams

    static hasMany = [
            actors: Actor,
            features: Feature,
            stories: Story,
            releases: Release,
            impediments: Impediment,
            domains: Domain,
            teams: Team
    ]

    static mappedBy = [
            features: "backlog",
            actors: "backlog",
            stories: "backlog",
            releases: "parentProduct",
            impediments: "backlog",
            domains: "backlog"
    ]

    static transients = [
            'allUsers',
            'productOwners',
            'erasableByUser',
            'stakeHolders',
            'invitedStakeHolders',
            'invitedProductOwners',
            'owner',
            'firstTeam',
            'versions',
            'sprints'
    ]

    def erasableByUser = false
    def productOwners = null
    def stakeHolders = null

    static mapping = {
        cache true
        table 'icescrum2_product'
        actors cascade: 'all-delete-orphan', batchSize: 10, cache: true
        features cascade: 'all-delete-orphan', sort: 'rank', batchSize: 10, cache: true
        stories cascade: 'all-delete-orphan', sort: 'rank', 'label': 'asc', batchSize: 25, cache: true
        domains cascade: 'all-delete-orphan', batchSize: 10, cache: true
        releases cascade: 'all-delete-orphan', batchSize: 10, sort: 'id', cache: true
        impediments cascade: 'all-delete-orphan', batchSize: 10, cache: true
        pkey(index: 'p_key_index')
        name(index: 'p_name_index')
        preferences lazy: true
    }

    static constraints = {
        name(blank: false, maxSize: 200, unique: true)
        pkey(blank: false, maxSize: 10, matches: /\d*[A-Z][A-Z0-9]*/, unique: true)
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        return result
    }

    @Override
    boolean equals(obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        final Product other = (Product) obj
        if (name == null) {
            if (other.name != null)
                return false
        } else if (!name.equals(other.name))
            return false
        return true
    }

    int compareTo(Product obj) {
        return name.compareTo(obj.name);
    }

    def getAllUsers() {
        def users = []
        this.teams?.each {
            if (it.members) {
                users.addAll(it.members)
            }
        }
        def pos = this.getProductOwners() // Do not use this.productOwners because it refers to the private attribute rather than the getter
        if (pos) {
            users.addAll(pos)
        }
        return users.asList().unique()
    }

    static recentActivity(Product currentProductInstance) {
        executeQuery("""SELECT act FROM grails.plugin.fluxiable.Activity as act
                        WHERE act.id IN (SELECT DISTINCT a.activity.id """ +
                                                "FROM grails.plugin.fluxiable.ActivityLink as a, org.icescrum.core.domain.Product as p " +
                                                "WHERE a.type='product' " +
                                                "and p.id=a.activityRef " +
                                                "and p.id=:p )" +
                            "ORDER BY act.dateCreated DESC", [p: currentProductInstance.id], [max: 15])
    }

    static allProductsByUser(long userid, params) {
        executeQuery("SELECT p FROM org.icescrum.core.domain.Product as p WHERE p.id IN (SELECT DISTINCT p.id " +
                "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                "WHERE t.id in" +
                "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                "INNER JOIN t2.members as m " +
                "WHERE m.id = :uid))", [uid: userid], params ?: [:])
    }

    static findAllByRole(User user, List<BasePermission> permission, params, members = true, archived = true) {
        executeQuery("SELECT p FROM org.icescrum.core.domain.Product as p WHERE p.id IN (SELECT DISTINCT p.id "+
                "From org.icescrum.core.domain.Product as p "+
                "where "

                + ( members ?
            "( p.id IN "+
                    "(SELECT DISTINCT p.id " +
                    "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                    "WHERE t.id in " +
                    "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                    "INNER JOIN t2.members as m " +
                    "WHERE m.id = :uid) "+
                    (archived ? '' : " AND p.preferences.archived = false ") +
                    ") )" +
                    "or" : "")

                + "( p IN ( SELECT DISTINCT p "+
                "From org.icescrum.core.domain.Product as p, "+
                "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass as ac, "+
                "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity as ai, "+
                "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid as acl, "+
                "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclEntry as ae "+
                "where "+
                "ac.className = 'org.icescrum.core.domain.Product' "+
                "AND ai.aclClass = ac.id "+
                "AND acl.sid = :sid "+
                "AND acl.id = ae.sid.id "+
                "AND ae.mask IN(:p) "+
                "AND ai.id = ae.aclObjectIdentity.id "+
                (archived ? '' : "AND p.preferences.archived = false ") +
                "AND p.id = ai.objectId ) ) )", members ? [sid: user?.username?:'', uid: user?.id?:0L, p:permission*.mask ] : [sid: user?.username?:'', p:permission*.mask ], params ?: [:])
    }

    static searchPublicAndMyProducts(User user, String term, params) {
        executeQuery("SELECT p FROM org.icescrum.core.domain.Product as p WHERE p.id IN (SELECT DISTINCT p.id "+
                        "From org.icescrum.core.domain.Product as p "+
                        "where "+
                        " ( p.name LIKE :term AND p.preferences.hidden = false ) " +
                        "OR ( p.name LIKE :term AND p.id IN "+
                        "(SELECT DISTINCT p.id " +
                        "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                        "WHERE t.id in " +
                        "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                        "INNER JOIN t2.members as m " +
                        "WHERE m.id = :uid) ) )" +
                        "or ( p.name LIKE :term AND p.id IN ( SELECT DISTINCT p.id "+
                        "From org.icescrum.core.domain.Product as p, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass as ac, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity as ai, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid as acl, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclEntry as ae "+
                        "where "+
                        "ac.className = 'org.icescrum.core.domain.Product' "+
                        "AND ai.aclClass = ac.id "+
                        "AND acl.sid = :sid "+
                        "AND acl.id = ae.sid.id "+
                        "AND ae.mask IN(:p) "+
                        "AND ai.id = ae.aclObjectIdentity.id "+
                        "AND p.id = ai.objectId ) ) )"
                        , [term:term, sid: user?.username?:'', uid: user?.id?:0L, p:[BasePermission.WRITE,BasePermission.READ]*.mask ], params ?: [:])
    }

    static countPublicAndMyProducts(User user, String term, params) {
        executeQuery("SELECT DISTINCT count(p) "+
                        "From org.icescrum.core.domain.Product as p "+
                        "where "+
                        " ( p.name LIKE :term AND p.preferences.hidden = false ) " +
                        "OR ( p.name LIKE :term AND p.id IN "+
                        "(SELECT DISTINCT p.id " +
                        "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                        "WHERE t.id in " +
                        "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                        "INNER JOIN t2.members as m " +
                        "WHERE m.id = :uid) ) )" +
                        "or ( p.name LIKE :term AND p.id IN ( SELECT DISTINCT p.id "+
                        "From org.icescrum.core.domain.Product as p, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclClass as ac, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclObjectIdentity as ai, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclSid as acl, "+
                        "org.codehaus.groovy.grails.plugins.springsecurity.acl.AclEntry as ae "+
                        "where "+
                        "ac.className = 'org.icescrum.core.domain.Product' "+
                        "AND ai.aclClass = ac.id "+
                        "AND acl.sid = :sid "+
                        "AND acl.id = ae.sid.id "+
                        "AND ae.mask IN(:p) "+
                        "AND ai.id = ae.aclObjectIdentity.id "+
                        "AND p.id = ai.objectId ) )"
                        , [term:term, sid: user?.username?:'', uid: user?.id?:0L, p:[BasePermission.WRITE,BasePermission.READ]*.mask ], params ?: [:])
    }

    def getProductOwners() {
        //Only used when product is being imported
        if (this.productOwners) {
            this.productOwners
        }
        else if (this.id) {
            def acl = retrieveAclProduct()
            def users = acl.entries.findAll {it.permission in SecurityService.productOwnerPermissions}*.sid*.principal;
            if (users)
                return User.findAll("from User as u where u.username in (:users)",[users:users], [cache: true])
            else
                return null
        } else {
            null
        }
    }

    def getStakeHolders() {
        //Only used when product is being imported
        if (this.stakeHolders) {
            this.stakeHolders
        }
        else if (this.id) {
            def acl = retrieveAclProduct()
            def users = acl.entries.findAll {it.permission in SecurityService.stakeHolderPermissions}*.sid*.principal
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
            def acl = retrieveAclProduct()
            return User.findByUsername(acl.owner.principal,[cache: true])
        } else {
            null
        }
    }

    List getInvitedStakeHolders() {
        return Invitation.findAllByTypeAndProductAndRole(InvitationType.PRODUCT, this, Authority.STAKEHOLDER).list().collect { it.userMock }
    }

    List getInvitedProductOwners() {
        return Invitation.findAllByTypeAndProductAndRole(InvitationType.PRODUCT, this, Authority.PRODUCTOWNER).list().collect { it.userMock }
    }

    Team getFirstTeam(){
        return this.teams? this.teams.first() : null
    }

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumProductEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE, true))
        }
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumProductEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE, true))
        }
    }

    def getVersions(def onlyFromSprints = false, def onlyDelivered = false) {
        def versions = onlyFromSprints ? [] : this.stories.findAll{it.affectVersion}*.affectVersion
        def sprints = this.releases*.sprints?.flatten()
        versions.addAll (onlyDelivered ? sprints?.findAll{ it.state == Sprint.STATE_DONE && it.deliveredVersion }*.deliveredVersion : sprints?.findAll{ it.deliveredVersion }*.deliveredVersion)
        return versions.unique()
    }

    def getSprints () {
        return this.releases*.sprints.flatten()
    }

    private Acl retrieveAclProduct(){
        def aclUtilService = (AclUtilService) ApplicationHolder.application.mainContext.getBean('aclUtilService')
        def acl
        try{
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        }catch(NotFoundException e){
            if (log.debugEnabled){
                log.debug(e.getMessage())
                log.debug("fixing unsecured project ... admin user will be the owner")
            }
            def securityService = (SecurityService) ApplicationHolder.application.mainContext.getBean('securityService')
            securityService.secureDomain(this)
            securityService.changeOwner(User.findById(1),this)
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        }
        return acl
    }
}