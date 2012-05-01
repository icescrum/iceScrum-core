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
 */

package org.icescrum.core.domain

import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.services.SecurityService
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumProductEvent
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.grails.plugins.springsecurity.service.acl.AclUtilService
import org.springframework.security.acls.domain.BasePermission

class Product extends TimeBox implements Serializable {

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
            'owner',
            'firstTeam'
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
        pkey(blank: false, maxSize: 10, matches: /[A-Z0-9]*/, unique: true)
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
            if (it.members)
                users.addAll(it.members)
        }
        if (this.productOwners)
            users.addAll(this.productOwners)
        return users.asList().unique()
    }

    static recentActivity(Product currentProductInstance) {
        executeQuery("SELECT DISTINCT a.activity " +
                "FROM grails.plugin.fluxiable.ActivityLink as a, org.icescrum.core.domain.Product as p " +
                "WHERE a.type='product' " +
                "and p.id=a.activityRef " +
                "and p.id=:p " +
                "ORDER BY a.activity.dateCreated DESC", [p: currentProductInstance.id], [max: 15])
    }

    static allProductsByUser(long userid, params) {
        executeQuery("SELECT DISTINCT p " +
                "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                "WHERE t.id in" +
                "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                "INNER JOIN t2.members as m " +
                "WHERE m.id = :uid)", [uid: userid], params ?: [:])
    }

    static findAllByRole(String user, List<BasePermission> permission, params) {
        executeQuery("SELECT DISTINCT p "+
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
                        "AND p.id = ai.objectId", [sid: user, p:permission*.mask ], params ?: [:])
    }

    static searchPublicAndMyProducts(String user, String term, params) {
        executeQuery("SELECT DISTINCT p "+
                        "From org.icescrum.core.domain.Product as p "+
                        "where "+
                        " ( p.name LIKE :term AND p.preferences.hidden = false ) " +
                        "or ( p.name LIKE :term AND p IN ( SELECT DISTINCT p "+
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
                        , [term:term, sid: user, p:[BasePermission.WRITE,BasePermission.READ]*.mask ], params ?: [:])
    }

    static countPublicAndMyProducts(String user, String term, params) {
        executeQuery("SELECT DISTINCT count(p) "+
                        "From org.icescrum.core.domain.Product as p "+
                        "where "+
                        " ( p.name LIKE :term AND p.preferences.hidden = false ) " +
                        "or ( p.name LIKE :term AND p IN ( SELECT DISTINCT p "+
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
                        , [term:term, sid: user, p:[BasePermission.WRITE,BasePermission.READ]*.mask ], params ?: [:])
    }

    def getProductOwners() {
        def aclUtilService = (AclUtilService) ApplicationHolder.application.mainContext.getBean('aclUtilService');
        //Only used when product is being imported
        if (this.productOwners) {
            this.productOwners
        }
        else if (this.id) {
            def acl = aclUtilService.readAcl(this.getClass(), this.id)
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
        def aclUtilService = (AclUtilService) ApplicationHolder.application.mainContext.getBean('aclUtilService');
        //Only used when product is being imported
        if (this.stakeHolders) {
            this.stakeHolders
        }
        else if (this.id) {
            def acl = aclUtilService.readAcl(this.getClass(), this.id)
            def users = acl.entries.findAll {it.permission in SecurityService.stakeHolderPermissions}*.sid*.principal;
            if (users)
                return User.findAll("from User as u where u.username in (:users)",[users:users], [cache: true])
            else
                return null
        } else {
            null
        }
    }

    def getOwner() {
        def aclUtilService = (AclUtilService) ApplicationHolder.application.mainContext.getBean('aclUtilService');
        if (this.id) {
            def acl = aclUtilService.readAcl(this.getClass(), this.id)
            return User.findByUsername(acl.owner.principal,[cache: true])
        } else {
            null
        }
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
}