/*
 * Copyright (c) 2014 Kagilum SAS.
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */


package org.icescrum.core.services

import grails.transaction.Transactional
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class TeamService extends IceScrumEventPublisher {

    def springSecurityService
    def securityService
    def grailsApplication

    void save(Team team, List members, List scrumMasters) {
        if (!team) {
            throw new BusinessException(code: 'is.team.error.not.exist')
        }
        if (!team.save()) {
            throw new BusinessException(code: 'is.team.error.not.saved')
        } else {
            securityService.secureDomain(team)
            if (members) {
                for (member in User.getAll(members)) {
                    if (!scrumMasters?.contains(member.id) && member) {
                        addMember(team, member)
                    }
                }
            }
            if (scrumMasters) {
                for (scrumMaster in User.getAll(scrumMasters)) {
                    if (scrumMaster) {
                        addScrumMaster(team, scrumMaster)
                    }
                }
            }
            team.save(flush: true)
            team.projects = [] // Grails does not initialize the collection and it is serialized as null instead of empty collection
            publishSynchronousEvent(IceScrumEventType.CREATE, team)
        }
    }

    @PreAuthorize('owner(#team)')
    void delete(Team team) {
        if (team.projects) {
            throw new BusinessException(code: 'is.team.error.delete.has.projects')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, team)
        def teamMembersIds = team.members*.id
        teamMembersIds.each { Long id ->
            removeMemberOrScrumMaster(team, User.get(id))
        }
        team.invitedMembers*.delete()
        team.invitedScrumMasters*.delete()
        team.delete()
        securityService.unsecureDomain(team)
        publishSynchronousEvent(IceScrumEventType.DELETE, team, dirtyProperties)
    }

    @PreAuthorize('isAuthenticated()')
    void saveImport(Team team) {
        //save before go further
        team.members.each { user ->
            user.save()
        }
        team.scrumMasters?.each { user ->
            user.save()
        }
        if (!team.save()) {
            throw new BusinessException(code: 'is.team.error.not.saved')
        }
        securityService.secureDomain(team)
        def scrumMasters = team.scrumMasters
        for (member in team.members) {
            if (!member.isAttached()) {
                member = member.merge()
            }
            if (!(member in scrumMasters)) {
                addMember(team, member)
            }
        }
        scrumMasters?.each { User scrumMaster ->
            if (!scrumMaster.isAttached()) {
                scrumMaster = scrumMaster.merge()
            }
            addScrumMaster(team, scrumMaster)
        }
        securityService.changeOwner(team.owner, team) // Only after adding SM & Member permission to ensure that current user has permissions to manage permissions
    }

    void addMember(Team team, User member) {
        if (!team.members*.id?.contains(member.id)) {
            team.addToMembers(member).save()
        }
        securityService.createTeamMemberPermissions(member, team)
    }

    void addScrumMaster(Team team, User member) {
        if (!team.members*.id?.contains(member.id)) {
            team.addToMembers(member).save()
        }
        securityService.createScrumMasterPermissions(member, team)
    }

    void removeMemberOrScrumMaster(Team team, User member) {
        team.removeFromMembers(member).save()
        if (team.scrumMasters*.id?.contains(member.id)) {
            securityService.deleteScrumMasterPermissions(member, team)
        } else {
            securityService.deleteTeamMemberPermissions(member, team)
        }
    }

    def unMarshall(def teamXml, def options) {
        Project project = options.project
        Team.withTransaction(readOnly: !options.save) { transaction ->
            def teamAlreadyExists = true
            def userService = (UserService) grailsApplication.mainContext.getBean('userService')
            def ownerXml = teamXml.owner.user
            User owner = User.findByUid(ownerXml.@uid.text())
            if (!owner) {
                teamAlreadyExists = false
                owner = userService.unMarshall(ownerXml, options)
            }
            if (options.userUIDByImportedID != null) {
                options.userUIDByImportedID[ownerXml.id.text()] = owner.uid
            }
            def team = new Team(
                    name: teamXml."${'name'}".text(),
                    velocity: (teamXml.velocity.text().isNumber()) ? teamXml.velocity.text().toInteger() : 0,
                    description: teamXml.description.text() ?: null,
                    uid: teamXml.@uid.text() ?: (teamXml."${'name'}".text()).encodeAsMD5()
            )
            team.owner = owner
            teamXml.members.user.each { userXml ->
                User user = User.findByUid(userXml.@uid.text())
                if (!user) {
                    teamAlreadyExists = false
                    user = userService.unMarshall(userXml, options)
                }
                team.addToMembers(user)
                if (options.userUIDByImportedID != null) {
                    options.userUIDByImportedID[userXml.id.text()] = user.uid
                }
            }
            team.scrumMasters = teamXml.scrumMasters.user.collect { userXml ->
                return team.members.find { it.uid == userXml.@uid.text() }
            }
            if (teamAlreadyExists) {
                Team dbTeam = Team.findByName(team.name)
                teamAlreadyExists = (dbTeam &&
                                     dbTeam.owner.uid == team.owner.uid &&
                                     dbTeam.members.size() == team.members.size() &&
                                     dbTeam.members.every { dbMember -> return team.members.find { teamMember -> teamMember.uid == dbMember.uid } })
                if (teamAlreadyExists) {
                    team = dbTeam
                }
            }
            // Reference on other object
            if (project) {
                project.addToTeams(team)
            }
            if (!teamAlreadyExists && options.save) {
                saveImport(team)
            }
            return (Team) importDomainsPlugins(teamXml, team, options)
        }
    }
}
