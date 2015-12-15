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

import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.support.ProgressSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.support.ApplicationSupport

@Transactional
class TeamService {

    def springSecurityService
    def securityService
    def grailsApplication

    void save(Team team, List members, List scrumMasters) {
        if (!team) {
            throw new RuntimeException('is.team.error.not.exist')
        }
        if (!team.save()) {
            throw new RuntimeException('is.team.error.not.saved')
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
            if (!team.save(flush: true)) {
                throw new RuntimeException()
            }
            team.products = [] // Grails does not initialize the collection and it is serialized as null instead of empty collection
        }
    }

    @PreAuthorize('owner(#team)')
    void delete(Team team) {
        if (team.products) {
            throw new RuntimeException('is.team.error.delete.has.products')
        }
        def teamMembersIds = team.members*.id
        teamMembersIds.each { Long id ->
            removeMemberOrScrumMaster(team, User.get(id))
        }
        team.invitedMembers*.delete()
        team.invitedScrumMasters*.delete()
        team.delete()
        securityService.unsecureDomain(team)
    }

    @PreAuthorize('isAuthenticated()')
    void saveImport(Team team) {
        if (!team) {
            throw new IllegalStateException('is.team.error.not.exist')
        }
        if (!team.save()) {
            throw new RuntimeException('is.team.error.not.saved')
        }
        securityService.secureDomain(team)
        def scrumMasters = team.scrumMasters
        def user = (User) springSecurityService.currentUser
        for (member in team.members) {
            if (!member.isAttached()) {
                member = member.merge()
            }
            if (!(member in scrumMasters)) {
                addMember(team, member)
            }
        }
        if (scrumMasters) {
            scrumMasters.eachWithIndex { it, index ->
                if (!it.isAttached()) {
                    it = it.merge()
                }
                addScrumMaster(team, it)
            }
            securityService.changeOwner(team.scrumMasters.first(), team)
        } else {
            if (!user.isAttached()) {
                user = user.merge()
            }
            addScrumMaster(team, user)
            securityService.changeOwner(user, team)
        }
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

    @Transactional(readOnly = true)
    def unMarshall(def team, Product p = null, ProgressSupport progress = null) {
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        try {
            def existingTeam = true
            def t = new Team(
                    name: team."${'name'}".text(),
                    velocity: (team.velocity.text().isNumber()) ? team.velocity.text().toInteger() : 0,
                    description: team.description.text(),
                    uid: team.@uid.text() ?: (team."${'name'}".text()).encodeAsMD5()
            )

            def userService = (UserService) grailsApplication.mainContext.getBean('userService')
            team.members.user.eachWithIndex { user, index ->
                User u = userService.unMarshall(user)
                if (!u.id) {
                    existingTeam = false
                }
                if (p) {
                    def uu = (User) t.members.find { it.uid == u.uid } ?: null
                    uu ? t.addToMembers(uu) : t.addToMembers(u)
                } else {
                    t.addToMembers(u)
                }
                progress?.updateProgress((team.members.user.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.user')]))
            }
            def scrumMastersList = []

            //fix between R6#x and R7
            def sm = team.scrumMasters.scrumMaster ?: team.scrumMasters.user
            sm.eachWithIndex { user, index ->
                def u
                if (!user.@uid?.isEmpty()) {
                    u = ((User) t.members.find { it.uid == user.@uid.text() }) ?: null
                } else {
                    u = ApplicationSupport.findUserUIDOldXMl(user, null, t.members)
                }
                if (u) {
                    scrumMastersList << u
                }
                progress?.updateProgress((team.members.user.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.user')]))
            }
            t.scrumMasters = scrumMastersList

            if (existingTeam) {
                Team dbTeam = Team.findByName(t.name)
                if (dbTeam) {
                    if (dbTeam.members.size() != t.members.size()) existingTeam = false
                    if (existingTeam) {
                        for (member in dbTeam.members) {
                            def u = t.members.find { member.uid == it.uid }
                            if (!u) {
                                existingTeam = false
                                break
                            }
                        }
                    }
                } else {
                    existingTeam = false
                }
                if (existingTeam) {
                    //Remove "tmp" team because team already exist
                    dbTeam.members?.each { member ->
                        member.removeFromTeams(t)
                    }
                    t.scrumMasters = null
                    t.delete()
                    return dbTeam
                } else {
                    return t
                }
            } else {
                return t
            }
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            progress?.progressError(g.message(code: 'is.parse.error', args: [g.message(code: 'is.team')]))
            throw new RuntimeException(e)
        }
    }
}
