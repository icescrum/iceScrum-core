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


package org.icescrum.core.services

import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.TeamPreferences
import org.icescrum.core.support.ProgressSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumTeamEvent
import org.icescrum.core.event.IceScrumProductEvent
import org.icescrum.core.support.ApplicationSupport

@Transactional
class TeamService {

    def springSecurityService
    def securityService
    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    void save(Team team, List members, List scrumMasters) {
        if (!team)
            throw new RuntimeException('is.team.error.not.exist')

        if (!team.save()) {
            throw new RuntimeException('is.team.error.not.saved')
        }

        else {
            securityService.secureDomain(team)
            if (members) {
                for (member in User.getAll(members*.toLong())) {
                    if (!scrumMasters?.contains(member.id)) {
                        if (member){
                            addMember(team,member)
                        }
                    }
                }
            }
            if (scrumMasters){
                for(scrumMaster in User.getAll(scrumMasters*.toLong())){
                    if (scrumMaster){
                        addScrumMaster(team,scrumMaster)
                    }
                }
            }
            if (!team.save(flush:true)) {
                throw new RuntimeException()
            }
            publishEvent(new IceScrumTeamEvent(team, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
        }

    }

    @PreAuthorize('owner(#team)')
    void delete(Team team) {
        if (!team)
            throw new IllegalStateException('Team must not be null')

        Team.withSession {session ->

            def toDelete = team.products.collect {it}

            for (Product product in toDelete)
                removeTeamsFromProduct(product, [team.id])
            session.flush()

            toDelete = team.members.collect {it}

            for (User member in toDelete)
                removeTeamsFromUser(member, [team.id])

            team.delete()

            securityService.unsecureDomain team
        }

    }

    @PreAuthorize('owner(#team)')
    void update(Team team) {
        if (!team)
            throw new IllegalStateException('Team must not be null')
        if (!team.save()) {
            throw new RuntimeException('is.team.error.not.saved')
        }
    }

    void removeTeamsFromUser(User _user, teamIds) {
        if (!_user)
            throw new IllegalStateException('_user must not be null')

        if (!teamIds)
            throw new IllegalStateException('_user must have at least one team')


        for (team in Team.getAll(teamIds)) {
            removeMemberOrScrumMaster(team,_user)
        }

        if (!_user.save())
            throw new IllegalStateException('_user not saved')
    }

    @PreAuthorize('owner(#_product)')
    void removeTeamsFromProduct(Product _product, teamIds) {
        if (!_product)
            throw new IllegalStateException('Product must not be null')

        if (!teamIds)
            throw new IllegalStateException('Product must have at least one team')


        for (team in Team.getAll(teamIds)) {
            if (team)
                _product.removeFromTeams(team)
            publishEvent(new IceScrumProductEvent(_product, team, this.class, (User) springSecurityService.currentUser, IceScrumProductEvent.EVENT_TEAM_REMOVED))
        }

        if (!_product.save())
            throw new IllegalStateException('Product not saved')
    }

    @PreAuthorize('isAuthenticated()')
    void saveImport(Team team) {
        if (!team)
            throw new IllegalStateException('is.team.error.not.exist')

        if (!team.save()) {
            throw new RuntimeException('is.team.error.not.saved')
        }

        def scrumMasters = team.scrumMasters
        securityService.secureDomain(team)

        def u = (User) springSecurityService.currentUser
        for (member in team.members) {
            if (!member.isAttached()) member = member.merge()
            if (!(member in scrumMasters))
                addMember(team,member)
        }
        if (scrumMasters) {
            scrumMasters.eachWithIndex {it, index ->
                if (!it.isAttached()) it = it.merge()
                addScrumMaster(team,it)
            }
            securityService.changeOwner(team.scrumMasters.first(), team)
        } else {
            if (!u.isAttached()) u = u.merge()
            addScrumMaster(team,u)
            securityService.changeOwner(u, team)
        }
        publishEvent(new IceScrumTeamEvent(team, this.class, u, IceScrumEvent.EVENT_CREATED))
    }

    void addMember(Team team, User member) {
        if (!team.members*.id?.contains(member.id))
            team.addToMembers(member).save()
        securityService.createTeamMemberPermissions member, team
        publishEvent(new IceScrumTeamEvent(team, member, this.class, (User) springSecurityService.currentUser, IceScrumTeamEvent.EVENT_MEMBER_ADDED))
    }

    void addScrumMaster(Team team, User member) {
        if (!team.members*.id?.contains(member.id))
            team.addToMembers(member).save()
        securityService.createScrumMasterPermissions member, team
        publishEvent(new IceScrumTeamEvent(team, member, this.class, (User) springSecurityService.currentUser, IceScrumTeamEvent.EVENT_MEMBER_ADDED))
    }

    void removeMemberOrScrumMaster(Team team, User member) {
        team.removeFromMembers(member).save()
        if (team.scrumMasters*.id?.contains(member.id))
            securityService.deleteScrumMasterPermissions member, team
        else
            securityService.deleteTeamMemberPermissions member, team
        publishEvent(new IceScrumTeamEvent(team, member, this.class, (User) springSecurityService.currentUser, IceScrumTeamEvent.EVENT_MEMBER_REMOVED))
    }


    @Transactional(readOnly = true)
    def unMarshall(def team, Product p = null, ProgressSupport progress = null) {
        try {
            def existingTeam = true
            def t = new Team(
                    name: team."${'name'}".text(),
                    velocity: (team.velocity.text().isNumber()) ? team.velocity.text().toInteger() : 0,
                    description: team.description.text(),
                    uid: team.@uid.text() ?: (team."${'name'}".text()).encodeAsMD5()
            )

            t.preferences = new TeamPreferences(
                    allowNewMembers: team.preferences.allowNewMembers.text()?.toBoolean() ?: true
            )

            def userService = (UserService) ApplicationHolder.application.mainContext.getBean('userService');
            team.members.user.eachWithIndex {user, index ->
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
            team.scrumMasters.scrumMaster.eachWithIndex {user, index ->
                def u
                if (!user.@uid?.isEmpty())
                    u = ((User) t.members.find { it.uid == user.@uid.text() } ) ?: null
                else{
                    u = ApplicationSupport.findUserUIDOldXMl(user,null,t.members)
                }
                if (u)
                    scrumMastersList << u
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
                    dbTeam.members?.each{ member ->
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
