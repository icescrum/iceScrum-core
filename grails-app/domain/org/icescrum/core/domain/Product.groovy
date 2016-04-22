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
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.domain

import grails.plugin.springsecurity.acl.AclUtilService
import grails.util.Holders
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumProductEvent
import org.icescrum.core.services.SecurityService
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.NotFoundException
import org.springframework.security.core.context.SecurityContextHolder as SCH

class Product extends TimeBox implements Serializable, Attachmentable {

    int planningPokerGameType = PlanningPokerGame.FIBO_SUITE
    String name = ""
    ProductPreferences preferences
    String pkey
    SortedSet<Team> teams
    SortedSet<Release> releases

    static hasMany = [
            actors: Actor,
            features: Feature,
            stories: Story,
            releases: Release,
            impediments: Impediment,
            domains: Domain,
            teams: Team,
            backlogs: Backlog
    ]

    static mappedBy = [
            features: "backlog",
            actors: "backlog",
            stories: "backlog",
            releases: "parentProduct",
            impediments: "backlog",
            domains: "backlog",
            backlogs: "product"
    ]

    static transients = [
            'allUsers',
            'allUsersAndStakehokders',
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
        releases cascade: 'all-delete-orphan', batchSize: 10, cache: true
        impediments cascade: 'all-delete-orphan', batchSize: 10, cache: true
        pkey(index: 'p_key_index')
        name(index: 'p_name_index')
        preferences lazy: true
    }

    static constraints = {
        name(blank: false, maxSize: 200, unique: true)
        pkey(blank: false, maxSize: 10, matches: /^[A-Z0-9]*$/, unique: true)   //TODO custom message
        planningPokerGameType(validator: { val, obj ->
            if (!(val in [PlanningPokerGame.INTEGER_SUITE, PlanningPokerGame.FIBO_SUITE, PlanningPokerGame.CUSTOM_SUITE])) {
                return ['no.game']
            }
            return true
        }) //TODO custom message
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

    Collection<User> getAllUsersAndStakehokders() {
        def users = getAllUsers()
        users.addAll(this.getStakeHolders())
        return users.unique()
    }

    static findAllByRole(User user, List<BasePermission> permission, params, members = true, archived = true, String term = '%%') {
        def vars = [sid: user?.username ?: '', p: permission*.mask, term: term]
        if (members) {
            vars.uid = user?.id ?: 0L
        }
        executeQuery("""SELECT p
                        FROM org.icescrum.core.domain.Product as p
                        WHERE p.id IN (
                            SELECT DISTINCT p.id
                            FROM org.icescrum.core.domain.Product as p
                            WHERE """
                                + ( members ? """
                                p.id IN (
                                    SELECT DISTINCT p.id
                                    FROM org.icescrum.core.domain.Product as p
                                    INNER JOIN p.teams as t
                                    WHERE t.id IN (
                                        SELECT DISTINCT t2.id
                                        FROM org.icescrum.core.domain.Team as t2
                                        INNER JOIN t2.members as m
                                        WHERE m.id = :uid
                                    )
                                )
                                OR"""
                                : "") + """
                                p IN (
                                    SELECT DISTINCT p
                                    FROM org.icescrum.core.domain.Product as p,
                                         grails.plugin.springsecurity.acl.AclClass as ac,
                                         grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                                         grails.plugin.springsecurity.acl.AclSid as acl,
                                         grails.plugin.springsecurity.acl.AclEntry as ae
                                    WHERE ac.className = 'org.icescrum.core.domain.Product'
                                    AND ai.aclClass = ac.id
                                    AND acl.sid = :sid
                                    AND acl.id = ae.sid.id
                                    AND ae.mask IN(:p)
                                    AND ai.id = ae.aclObjectIdentity.id
                                    AND p.id = ai.objectId
                                )
                        )
                        AND lower(p.name) LIKE lower(:term)""" +
                        (archived ? '' : "AND p.preferences.archived = false "), vars, params ?: [:])
    }

    static findAllByUserAndActive(User user, params, String term) {
        if (!term) {
            term = '%%'
        }
        return findAllByRole(user, [BasePermission.WRITE, BasePermission.READ], params, true, false, term)
    }

    static findAllByMember(long userid, params) {
        executeQuery("""SELECT p
                        FROM org.icescrum.core.domain.Product as p
                        WHERE p.id IN (
                            SELECT DISTINCT p.id
                            FROM org.icescrum.core.domain.Product as p
                            INNER JOIN p.teams as t
                            WHERE t.id IN (
                                SELECT DISTINCT t2.id
                                FROM org.icescrum.core.domain.Team as t2
                                INNER JOIN t2.members as m
                                WHERE m.id = :uid
                            )
                        )""", [uid: userid], params ?: [:])
    }

    static Product withProduct(long id) {
        Product product = get(id)
        if (!product) {
            throw new ObjectNotFoundException(id, 'Product')
        }
        return product
    }

    def getProductOwners() {
        //Only used when product is being imported
        if (this.productOwners) {
            this.productOwners
        } else if (this.id) {
            def acl = retrieveAclProduct()
            def users = acl.entries.findAll { it.permission in SecurityService.productOwnerPermissions }*.sid*.principal;
            if (users) {
                return User.findAll("from User as u where u.username in (:users)", [users: users], [cache: true])
            } else {
                return []
            }
        } else {
            return []
        }
    }

    def getStakeHolders() {
        if (this.stakeHolders) {
            this.stakeHolders // Used only when the project is being imported
        } else if (this.id) {
            def acl = retrieveAclProduct()
            def users = acl.entries.findAll { it.permission in SecurityService.stakeHolderPermissions }*.sid*.principal
            if (users) {
                return User.findAll("from User as u where u.username in (:users)", [users: users], [cache: true])
            } else {
                return []
            }
        } else {
            return []
        }
    }

    def getOwner() {
        return (id && firstTeam) ? firstTeam.owner : null
    }

    List<Invitation> getInvitedStakeHolders() {
        return Invitation.findAllByTypeAndProductAndFutureRole(InvitationType.PRODUCT, this, Authority.STAKEHOLDER)
    }

    List<Invitation> getInvitedProductOwners() {
        return Invitation.findAllByTypeAndProductAndFutureRole(InvitationType.PRODUCT, this, Authority.PRODUCTOWNER)
    }

    Team getFirstTeam() {
        return this.teams ? this.teams.first() : null
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
        def versions = onlyFromSprints ? [] : this.stories.findAll { it.affectVersion }*.affectVersion
        def sprints = this.releases*.sprints?.flatten()
        versions.addAll(onlyDelivered ? sprints?.findAll { it.state == Sprint.STATE_DONE && it.deliveredVersion }*.deliveredVersion : sprints?.findAll { it.deliveredVersion }*.deliveredVersion)
        return versions.unique()
    }

    def getSprints() {
        return this.releases*.sprints.flatten()
    }

    private Acl retrieveAclProduct() {
        def aclUtilService = (AclUtilService) Holders.grailsApplication.mainContext.getBean('aclUtilService')
        def acl
        try {
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        } catch (NotFoundException e) {
            if (log.debugEnabled) {
                log.debug(e.getMessage())
                log.debug("fixing unsecured project ... admin user will be the owner")
            }
            def securityService = (SecurityService) Holders.grailsApplication.mainContext.getBean('securityService')
            securityService.secureDomain(this)
            securityService.changeOwner(User.findById(1), this)
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        }
        return acl
    }

    def xml(builder) {
        builder.product(id: this.id) {
            pkey(this.pkey)
            endDate(this.endDate)
            todoDate(this.todoDate)
            startDate(this.startDate)
            lastUpdated(this.lastUpdated)
            dateCreated(this.dateCreated)
            planningPokerGameType(this.planningPokerGameType)
            name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            this.preferences.xml(builder)
            teams() {
                this.teams.each { _team ->
                    _team.xml(builder)
                }
            }
            productOwners() {
                this.productOwners.each { _user ->
                    _user.xml(builder)
                }
            }
            features() {
                this.features.each { _feature ->
                    _feature.xml(builder)
                }
            }
            stories() {
                this.stories.findAll { it.parentSprint == null }.each { _story ->
                    _story.xml(builder)
                }
            }
            releases() {
                this.releases.each { _release ->
                    _release.xml(builder)
                }
            }
            attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
            cliches() {
                this.cliches.each { _cliche ->
                    _cliche.xml(builder)
                }
            }
        }
    }
}