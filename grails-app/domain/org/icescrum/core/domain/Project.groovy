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
 * Colin Bontemps (cbontemps@kagilum.com)
 */

package org.icescrum.core.domain

import grails.plugin.springsecurity.acl.AclUtilService
import grails.util.Holders
import org.hibernate.ObjectNotFoundException
import org.hibernate.criterion.CriteriaSpecification
import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.preferences.ProjectPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.services.SecurityService
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.springframework.security.acls.domain.BasePermission
import org.springframework.security.acls.model.Acl
import org.springframework.security.acls.model.NotFoundException

class Project extends TimeBox implements Serializable, Attachmentable {

    int planningPokerGameType = PlanningPokerGame.FIBO_SUITE
    String name = ""
    ProjectPreferences preferences
    String pkey
    SortedSet<Team> teams // DO NOT USE DIRECTLY, rather use transient "team" that resolves the first and only team
    SortedSet<Release> releases

    static belongsTo = [
            portfolio: Portfolio
    ]

    static hasMany = [
            actors               : Actor,
            features             : Feature,
            stories              : Story,
            releases             : Release,
            teams                : Team,
            backlogs             : Backlog,
            tasks                : Task,
            simpleProjectApps    : SimpleProjectApp,
            timeBoxNotesTemplates: TimeBoxNotesTemplate
    ]

    static mappedBy = [
            features: "backlog",
            stories : "backlog",
            releases: "parentProject",
            backlogs: "project",
            actors  : "parentProject",
            tasks   : "parentProject"
    ]

    static transients = [
            'allUsers',
            'allUsersAndOwnerAndStakeholders',
            'productOwners',
            'erasableByUser',
            'stakeHolders',
            'invitedStakeHolders',
            'invitedProductOwners',
            'owner',
            'team',
            'versions',
            'sprints',
            'currentOrNextRelease'
    ]

    Integer attachments_count = 0

    def erasableByUser = false
    def productOwners = null
    def stakeHolders = null

    static mapping = {
        cache true
        table 'is_project'
        actors cascade: 'all-delete-orphan', batchSize: 10, cache: true
        features cascade: 'all-delete-orphan', sort: 'rank', batchSize: 10, cache: true
        stories cascade: 'all-delete-orphan', sort: 'rank', 'label': 'asc', batchSize: 25, cache: true
        releases cascade: 'all-delete-orphan', batchSize: 10, cache: true
        tasks cascade: 'all-delete-orphan', batchSize: 10, cache: true
        pkey(index: 'p_key_index')
        name(index: 'p_name_index')
        attachments_count(nullable: true) // Must be nullable at creation for postgres because it doesn't set default value. The not nullable constraint is added in migration.
        preferences lazy: true
    }

    static constraints = {
        name(blank: false, shared: 'keyMaxSize')
        pkey(blank: false, maxSize: 10, matches: /^[A-Z0-9]*[A-Z][A-Z0-9]*$/, unique: true)
        planningPokerGameType(validator: { val, obj ->
            if (!(val in [PlanningPokerGame.INTEGER_SUITE, PlanningPokerGame.FIBO_SUITE, PlanningPokerGame.CUSTOM_SUITE])) {
                return ['no.game']
            }
            return true
        })
        portfolio(nullable: true)
    }

    @Override
    int hashCode() {
        final int prime = 34
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!id) ? 0 : id.hashCode())
        return result
    }

    @Override
    boolean equals(obj) {
        if (this.is(obj)) {
            return true
        }
        if (obj == null) {
            return false
        }
        if (getClass() != obj.getClass()) {
            return false
        }
        final Project other = (Project) obj
        if (name == null) {
            if (other.name != null) {
                return false
            }
        } else if (!name.equals(other.name)) {
            return false
        }
        return true
    }

    int compareTo(Project obj) {
        return name.compareTo(obj.name);
    }

    List<User> getAllUsers() {
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

    List<User> getAllUsersAndOwnerAndStakeholders() {
        def users = getAllUsers()
        this.teams?.each {
            users << it.owner
        }
        def shs = this.getStakeHolders()
        if (shs) {
            users.addAll(shs)
        }
        return users.unique()
    }

    static List<Project> findAllByTermAndFilter(params = [:], String term = '', String filter = '') {
        return createCriteria().list(params) {
            if (filter) {
                preferences {
                    if (filter == 'archived' || filter == 'active') {
                        eq 'archived', filter == 'archived'
                    } else if (filter == 'hidden' || filter == 'public') {
                        eq 'hidden', filter == 'hidden'
                    }
                }
            }
            if (term) {
                or {
                    ilike 'name', "%${term}%"
                    ilike 'pkey', "%${term}%"
                }
            }
        }
    }

    static findAllByRole(User user, List<BasePermission> permission, params, members = true, archived = true, owner = true, String term = '%%') {
        def vars = [sid: user?.username ?: '', p: permission*.mask, term: term]
        if (members) {
            vars.uid = user?.id ?: 0L
        }
        executeQuery("""SELECT p
                        FROM org.icescrum.core.domain.Project as p
                        WHERE p.id IN (
                            SELECT DISTINCT p.id
                            FROM org.icescrum.core.domain.Project as p
                            WHERE """
                + (owner ? """
                                p.id IN (
                                    SELECT DISTINCT p.id
                                        FROM org.icescrum.core.domain.Project as p,
                                             grails.plugin.springsecurity.acl.AclClass as ac,
                                             grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                                             grails.plugin.springsecurity.acl.AclSid as acl
                                        WHERE ac.className = 'org.icescrum.core.domain.Team'
                                        AND ai.aclClass = ac.id
                                        AND ai.owner.sid = :sid
                                        AND acl.id = ai.owner
                                        AND p.id = ai.objectId
                                )
                                OR"""
                         : "")
                + (members ? """
                                p.id IN (
                                    SELECT DISTINCT p.id
                                    FROM org.icescrum.core.domain.Project as p
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
                                p.id IN (
                                    SELECT DISTINCT p.id
                                    FROM org.icescrum.core.domain.Project as p,
                                         grails.plugin.springsecurity.acl.AclClass as ac,
                                         grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                                         grails.plugin.springsecurity.acl.AclSid as acl,
                                         grails.plugin.springsecurity.acl.AclEntry as ae
                                    WHERE ac.className = 'org.icescrum.core.domain.Project'
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
        return findAllByRole(user, [BasePermission.WRITE, BasePermission.READ], params, true, false, false, term)
    }

    static findAllByMember(long userid, params) {
        executeQuery("""SELECT p
                        FROM org.icescrum.core.domain.Project as p
                        WHERE p.id IN (
                            SELECT DISTINCT p.id
                            FROM org.icescrum.core.domain.Project as p
                            INNER JOIN p.teams as t
                            WHERE t.id IN (
                                SELECT DISTINCT t2.id
                                FROM org.icescrum.core.domain.Team as t2
                                INNER JOIN t2.members as m
                                WHERE m.id = :uid
                            )
                        )""", [uid: userid], params ?: [:])
    }

    static Project withProject(long id) {
        Project project = get(id)
        if (!project) {
            throw new ObjectNotFoundException(id, 'Project')
        }
        return project
    }

    static List<Project> withProjects(def params, String id = 'id', User productOwnerOrOwner = null) {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Project> projects = ids ? getAll(ids) : null
        if (productOwnerOrOwner) {
            projects = projects.findAll { Project project ->
                project.productOwners.contains(productOwnerOrOwner) || project.owner == productOwnerOrOwner
            }
        }
        if (!projects) {
            throw new ObjectNotFoundException(ids, 'Project')
        }
        return projects
    }

    List<User> getProductOwners() {
        //Only used when project is being imported
        if (this.productOwners != null) {
            this.productOwners
        } else if (this.id) {
            def acl = retrieveAclProject()
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

    List<User> getStakeHolders() {
        if (this.stakeHolders != null) {
            this.stakeHolders // Used only when the project is being imported
        } else if (this.id) {
            def acl = retrieveAclProject()
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

    User getOwner() {
        return (id && team) ? team.owner : null
    }

    User getUserByUidOrOwner(String uid) {
        return getAllUsersAndOwnerAndStakeholders().find { it.uid == uid } ?: (User.findByUid(uid) ?: owner)
    }

    User getUserByUid(String uid) {
        return getAllUsersAndOwnerAndStakeholders().find { it.uid == uid } ?: User.findByUid(uid)
    }

    List<Invitation> getInvitedStakeHolders() {
        return Invitation.findAllByTypeAndProjectAndFutureRole(InvitationType.PROJECT, this, Authority.STAKEHOLDER)
    }

    List<Invitation> getInvitedProductOwners() {
        return Invitation.findAllByTypeAndProjectAndFutureRole(InvitationType.PROJECT, this, Authority.PRODUCTOWNER)
    }

    Team getTeam() {
        return this.teams ? this.teams.first() : null
    }

    List<String> getVersions(def onlyFromSprints = false, def onlyDelivered = false) {
        def versions = onlyFromSprints ? [] : this.stories.findAll { it.affectVersion }*.affectVersion
        def sprints = this.releases*.sprints?.flatten()
        versions.addAll(onlyDelivered ? sprints?.findAll { it.state == Sprint.STATE_DONE && it.deliveredVersion }*.deliveredVersion : sprints?.findAll { it.deliveredVersion }*.deliveredVersion)
        return versions.unique()
    }

    Map<Integer, String> getStoryStateNames() {
        return Holders.grailsApplication.config.icescrum.resourceBundles.storyStates
    }

    def getSprints() {
        return this.releases*.sprints.flatten()
    }

    def getCurrentOrNextRelease() {
        def release = Release.withCriteria(uniqueResult: true) {
            resultTransformer CriteriaSpecification.ALIAS_TO_ENTITY_MAP
            eq('parentProject', this)
            ne('state', Release.STATE_DONE)
            projections {
                property("id", "id")
                property("name", "name")
                property("state", "state")
                property("firstSprintIndex", "firstSprintIndex")
            }
            order("state", "desc")
            maxResults(1)
        }
        if (release?.id) {
            def sprintAndCount = Sprint.executeQuery("""
                SELECT s.id, s.goal, s.state, s.orderNumber, s.velocity, s.capacity, s.endDate, s.startDate
                FROM Sprint s
                WHERE s.parentRelease.id = :parentReleaseId
                AND s.state in (:states)
                ORDER BY s.state DESC """, [parentReleaseId: release.id, states: [Sprint.STATE_INPROGRESS, Sprint.STATE_WAIT]], [max: 1])[0]
            if (sprintAndCount) {
                def sprint = [id: sprintAndCount[0], goal: sprintAndCount[1], state: sprintAndCount[2], orderNumber: sprintAndCount[3], velocity: sprintAndCount[4], capacity: sprintAndCount[5], endDate: sprintAndCount[6], startDate: sprintAndCount[7]]
                sprint.index = sprint.orderNumber + release.remove('firstSprintIndex') - 1
                release.currentOrNextSprint = sprint
            }
        }
        return release
    }

    private Acl retrieveAclProject() {
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
            securityService.changeOwner(team?.owner ?: User.findById(1), this)
            acl = aclUtilService.readAcl(this.getClass(), this.id)
        }
        return acl
    }

    def xml(builder) {
        builder.project(id: this.id) {
            builder.pkey(this.pkey)
            builder.endDate(this.endDate)
            builder.todoDate(this.todoDate)
            builder.startDate(this.startDate)
            builder.lastUpdated(this.lastUpdated)
            builder.dateCreated(this.dateCreated)
            builder.planningPokerGameType(this.planningPokerGameType)
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            preferences.xml(builder)
            builder.teams() {
                this.teams.each { _team ->
                    _team.xml(builder)
                }
            }
            builder.productOwners() {
                this.productOwners.each { _user ->
                    _user.xml(builder)
                }
            }
            if (preferences.hidden) {
                builder.stakeHolders() {
                    this.stakeHolders.each { _user ->
                        _user.xml(builder)
                    }
                }
            }
            builder.features() {
                this.features.each { _feature ->
                    _feature.xml(builder)
                }
            }
            builder.actors() {
                this.actors.sort { it.uid }.each { _actor ->
                    _actor.xml(builder)
                }
            }
            builder.stories() {
                // To preserve groupby & sort order and be able to insert dependsOn on the import flow..
                this.stories.findAll {
                    it.parentSprint == null
                }.sort { a, b ->
                    def stateA = a.state == Story.STATE_ESTIMATED ? Story.STATE_ACCEPTED : a.state
                    def stateB = b.state == Story.STATE_ESTIMATED ? Story.STATE_ACCEPTED : b.state
                    return stateB <=> stateA ?: a.rank <=> b.rank
                }.each { _story ->
                    _story.xml(builder)
                }
            }
            builder.releases() {
                this.releases.each { _release ->
                    _release.xml(builder)
                }
            }
            builder.attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
            builder.cliches() {
                this.cliches.sort { a, b ->
                    a.type <=> b.type ?: a.datePrise <=> b.datePrise
                }.each { _cliche ->
                    _cliche.xml(builder)
                }
            }
            builder.activities() {
                this.activities.each { _activity ->
                    _activity.xml(builder)
                }
            }
            builder.simpleProjectApps() {
                this.simpleProjectApps.each { _simpleProjectApp ->
                    _simpleProjectApp.xml(builder)
                }
            }
            builder.timeBoxNotesTemplates() {
                this.timeBoxNotesTemplates.each { _timeBoxNotesTemplate ->
                    _timeBoxNotesTemplate.xml(builder)
                }
            }
            exportDomainsPlugins(builder)
        }
    }
}
