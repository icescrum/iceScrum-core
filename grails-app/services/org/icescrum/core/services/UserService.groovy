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

import grails.plugin.springsecurity.SpringSecurityUtils
import grails.plugin.springsecurity.acl.AclEntry
import grails.plugin.springsecurity.acl.AclSid
import grails.transaction.Transactional
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.apache.commons.io.filefilter.WildcardFileFilter
import org.grails.comments.Comment
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.Activity
import org.icescrum.core.domain.Backlog
import org.icescrum.core.domain.Invitation
import org.icescrum.core.domain.Invitation.InvitationType
import org.icescrum.core.domain.Portfolio
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.Team
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Widget
import org.icescrum.core.domain.Window
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.springframework.security.acls.domain.BasePermission

@Transactional
class UserService extends IceScrumEventPublisher {

    def securityService
    def widgetService
    def projectService
    def portfolioService
    def teamService
    def hdImageService
    def grailsApplication
    def springSecurityService
    def notificationEmailService
    def aclCache
    def pushService

    void save(User user, String token = null) {
        if (user.password) {
            user.password = springSecurityService.encodePassword(user.password)
        } else if (user.accountExternal) {
            user.password = 'passwordDefinedExternally'
        }
        user.save()
        publishSynchronousEvent(IceScrumEventType.CREATE, user)
        if (token && grailsApplication.config.icescrum.invitation.enable) {
            def invitations = Invitation.findAllByToken(token)
            acceptInvitations(invitations, user)
        }
        widgetService.initUserWidgets(user)
        update(user, [avatar: 'initials'])
    }

    void update(User user, Map props = [:]) {
        if (props.pwd) {
            user.password = springSecurityService.encodePassword(props.pwd)
        }
        if (props.emailsSettings) {
            user.preferences.emailsSettings = props.emailsSettings
        }
        try {
            if (props.avatar) {
                def ext
                def generated = false
                if (props.avatar == 'initials') {
                    ext = "png"
                    def path = "${grailsApplication.config.icescrum.images.users.dir}${user.id}.${ext}"
                    try {
                        generated = ApplicationSupport.generateInitialsAvatar(user.firstName, user.lastName, new FileOutputStream(new File(path)))
                    } catch (Exception e) {
                        if (log.debugEnabled) {
                            log.debug('Avatar generating failed: ' + e.message)
                        }
                    }
                }
                if (!generated) {
                    ext = FilenameUtils.getExtension(props.avatar)
                    def path = "${grailsApplication.config.icescrum.images.users.dir}${user.id}.${ext}"
                    def source = new File((String) props.avatar)
                    def dest = new File(path)
                    FileUtils.copyFile(source, dest)
                    if (props.scale) {
                        def avatar = new File(path)
                        avatar.setBytes(hdImageService.scale(new FileInputStream(dest), 120, 120))
                    }
                }
                def files = new File(grailsApplication.config.icescrum.images.users.dir.toString()).listFiles((FilenameFilter) new WildcardFileFilter("${user.id}.*"))
                files.each {
                    if (FilenameUtils.getExtension(it.path) != ext) {
                        it.delete()
                    }
                }
            } else if (props.containsKey('avatar') && props.avatar == null) {
                File[] oldAvatars = new File(grailsApplication.config.icescrum.images.users.dir.toString()).listFiles((FilenameFilter) new WildcardFileFilter("${user.id}.*"))
                oldAvatars.each {
                    it.delete()
                }
            }
        } catch (RuntimeException e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new BusinessException(code: 'is.convert.image.error')
        }
        user.lastUpdated = new Date()
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, user)
        user.save(flush: true)
        if (dirtyProperties.containsKey('username')) {
            def aclSid = AclSid.findBySidAndPrincipal(dirtyProperties.username, true)
            if (aclSid) {
                aclSid.sid = user.username
                aclSid.save(flush: true)
                aclCache.clearCache()
            }
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, user, dirtyProperties)
    }

    void delete(User user, User substitute, boolean deleteOwnedData = false) {
        pushService.disablePushForThisThread()
        publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, user, [substitutedBy: substitute, deleteOwnedData: deleteOwnedData])
        Team.findAllByOwner(user.username, [:]).each {
            if (deleteOwnedData) {
                it.projects.collect { it }.each { Project project -> // Collect to avoid ConcurrentModificationException
                    projectService.delete(project)
                }
                teamService.delete(it)
            } else {
                securityService.changeOwner(substitute, it)
                it.projects.each { Project project ->
                    securityService.changeOwner(substitute, project)
                }
            }
        }
        Portfolio.findAllByUser(user).each { Portfolio portfolio ->
            securityService.deleteBusinessOwnerPermissions(user, portfolio)
            securityService.deletePortfolioStakeHolderPermissions(user, portfolio)
        }
        Project.findAllByRole(user, [BasePermission.WRITE, BasePermission.READ], [:], true, false, false, "").each { Project project ->
            securityService.deleteProductOwnerPermissions(user, project)
            securityService.deleteTeamMemberPermissions(user, project.teams[0])
            securityService.deleteScrumMasterPermissions(user, project.teams[0])
        }
        Project.findAllByRole(user, [BasePermission.WRITE, BasePermission.READ], [:], true, true, false, "").each { Project project ->
            securityService.deleteProductOwnerPermissions(user, project)
            securityService.deleteTeamMemberPermissions(user, project.teams[0])
            securityService.deleteScrumMasterPermissions(user, project.teams[0])
        }
        Window.findAllByUser(user).collect { it }.each { it.delete() } // Collect to avoid ConcurrentModificationException
        user.preferences.widgets.collect { Widget widget -> widget }.each { Widget widget ->
            user.preferences.removeFromWidgets(widget)
            widget.delete()
        }
        Team.where {
            members { id == user.id }
        }.list().each {
            it.removeFromMembers(user)
        }
        Story.where {
            followers { id == user.id }
        }.list().each {
            it.removeFromFollowers(user)
        }
        Story.where {
            voters { id == user.id }
        }.list().each {
            it.removeFromVoters(user)
        }
        Story.findAllByCreator(user).each {
            it.creator = substitute
            it.save()
        }
        Task.findAllByCreatorOrResponsible(user, user)?.each {
            it.creator = it.creator == user ? substitute : it.creator
            it.responsible = it.responsible == user ? substitute : it.responsible
            it.save()
        }
        Comment.findAllByPosterId(user.id).each {
            it.posterId = substitute.id
            it.save()
        }
        Attachment.findAllByPosterId(user.id).each {
            it.posterId = substitute.id
            it.save()
        }
        Activity.findAllByPoster(user).each {
            it.poster = substitute
            it.save()
        }
        AcceptanceTest.findAllByCreator(user).each {
            it.creator = substitute
            it.save()
        }
        Backlog.findAllByOwner(user).each {
            it.owner = substitute
            it.save()
        }
        def aclSid = AclSid.findBySidAndPrincipal(user.username, true)
        if (aclSid) {
            AclEntry.findAllBySid(aclSid)*.delete()
            aclSid.delete()
            aclCache.clearCache()
        }
        File avatarFile = getAvatarFile(user)
        if (avatarFile?.exists()) {
            avatarFile.delete()
        }
        user.delete(flush: true)
        pushService.enablePushForThisThread()
        publishSynchronousEvent(IceScrumEventType.DELETE, user, [substitutedBy: substitute, deleteOwnedData: deleteOwnedData])
    }

    def resetPassword(User user) {
        def pool = ['a'..'z', 'A'..'Z', 0..9, '_'].flatten()
        Random rand = new Random(System.currentTimeMillis())
        def passChars = (0..10).collect { pool[rand.nextInt(pool.size())] }
        def password = passChars.join('')
        update(user, [pwd: password])
        notificationEmailService.sendNewPassword(user, password)
    }


    void menu(User user, String id, String position, boolean hidden) {
        def currentMenu
        if (hidden) {
            currentMenu = user.preferences.menuHidden
            if (!currentMenu.containsKey(id)) {
                currentMenu.put(id, (currentMenu.size() + 1).toString())
                if (user.preferences.menu.containsKey(id)) {
                    this.menu(user, id, user.preferences.menu.size().toString(), false)
                    user.preferences.menu.remove(id)
                }
            }
        } else {
            currentMenu = user.preferences.menu
            if (!currentMenu.containsKey(id)) {
                currentMenu.put(id, (currentMenu.size() + 1).toString())
                if (user.preferences.menuHidden.containsKey(id)) {
                    this.menu(user, id, user.preferences.menuHidden.size().toString(), true)
                    user.preferences.menuHidden.remove(id)
                }
            }
        }
        def from = currentMenu.get(id)?.toInteger()
        from = from ?: 1
        def to = position.toInteger()
        if (from != to) {
            if (from > to) {
                currentMenu.entrySet().each { it ->
                    if (it.value.toInteger() >= to && it.value.toInteger() <= from && it.key != id) {
                        it.value = (it.value.toInteger() + 1).toString()
                    } else if (it.key == id) {
                        it.value = position
                    }
                }
            } else {
                currentMenu.entrySet().each { it ->
                    if (it.value.toInteger() <= to && it.value.toInteger() >= from && it.key != id) {
                        it.value = (it.value.toInteger() - 1).toString()
                    } else if (it.key == id) {
                        it.value = position
                    }
                }
            }
        }
        user.lastUpdated = new Date()
        user.save()
    }

    File getAvatarFile(User user) {
        File[] files = new File(grailsApplication.config.icescrum.images.users.dir.toString()).listFiles((FilenameFilter) new WildcardFileFilter("${user.id}.*"))
        return files ? files[0] : null
    }

    void manageInvitations(List<Invitation> currentInvitations, List newInvitations, InvitationType type, Object domain) {
        newInvitations.each {
            def email = it.email
            int role = it.role
            Invitation currentInvitation = currentInvitations.find { it.email == email }
            if (currentInvitation) {
                if (currentInvitation.futureRole != role) {
                    currentInvitation.futureRole = role
                    currentInvitation.save()
                }
            } else {
                def invitation = Invitation.getNewInvitation(email: email, futureRole: role, type: type)
                if (type == InvitationType.PORTFOLIO) {
                    invitation.portfolio = domain
                } else if (type == InvitationType.TEAM) {
                    invitation.team = domain
                } else if (type == InvitationType.PROJECT) {
                    invitation.project = domain
                }
                invitation.save()
                try {
                    notificationEmailService.sendInvitation(invitation, springSecurityService.currentUser)
                } catch (MailException) {
                    throw new BusinessException(code: 'is.mail.error')
                }
            }
        }
        currentInvitations.findAll { currentInvitation ->
            !newInvitations*.email.contains(currentInvitation.email)
        }*.delete()
    }

    void acceptInvitations(List<Invitation> invitations, User user) {
        def allowedTeams = [], allowedProjects = [] // Reject invitations where previous membership was in place and accept invitations on new membership
        invitations.each { invitation ->
            def userAdmin = ApplicationSupport.getFirstAdministrator()
            SpringSecurityUtils.doWithAuth(userAdmin ? userAdmin.username : 'admin') {
                if (invitation.type == InvitationType.PROJECT) {
                    Project project = invitation.project
                    def oldMembers = projectService.getAllMembersProjectByRole(project)
                    if (project in allowedProjects || !(user.id in oldMembers.keySet()*.id)) {
                        allowedProjects << project
                        allowedTeams << project.team
                        projectService.addRole(project, user, invitation.futureRole)
                        projectService.manageProjectEvents(project, oldMembers)
                    }
                } else if (invitation.type == InvitationType.PORTFOLIO) {
                    Portfolio portfolio = invitation.portfolio
                    def oldMembers = portfolioService.getAllMembersPortfolioByRole(portfolio)
                    if (!(user.id in oldMembers.keySet()*.id)) {
                        portfolioService.addRole(portfolio, user, invitation.futureRole)
                        portfolioService.managePortfolioEvents(portfolio, oldMembers)
                    }
                } else {
                    Team team = invitation.team
                    def oldMembersByProject = [:]
                    team.projects.each { Project project ->
                        oldMembersByProject[project.id] = projectService.getAllMembersProjectByRole(project)
                    }
                    if (team in allowedTeams || !(user.id in oldMembersByProject.values().collect { it.keySet().collect { it.id } }.flatten())) {
                        allowedTeams << team
                        projectService.addRole(team, user, invitation.futureRole)
                        oldMembersByProject.each { Long projectId, Map oldMembers ->
                            Project project = Project.get(projectId)
                            allowedProjects << project
                            projectService.manageProjectEvents(project, oldMembers)
                        }
                    }
                }
                invitation.delete()
            }
        }
    }

    User unMarshall(def userXml, def options) {
        User.withTransaction(readOnly: !options.save) { transaction ->
            User user = new User(
                    lastName: userXml.lastName.text(),
                    firstName: userXml.firstName.text(),
                    username: userXml.username.text(),
                    email: userXml.email.text(),
                    password: userXml.password.text(),
                    enabled: userXml.enabled.text().toBoolean(),
                    accountExpired: userXml.accountExpired.text().toBoolean(),
                    accountLocked: userXml.accountLocked.text().toBoolean(),
                    passwordExpired: userXml.passwordExpired.text().toBoolean(),
                    accountExternal: userXml.accountExternal.text().toBoolean(),
                    uid: userXml.@uid.text() ?: (userXml.username.text() + userXml.email.text()).encodeAsMD5()
            )
            def preferencesXml = userXml.preferences
            user.preferences = new UserPreferences(
                    language: preferencesXml.language.text(),
                    activity: preferencesXml.activity.text(),
                    filterTask: preferencesXml.filterTask.text(),
                    enableDarkMode: preferencesXml.enableDarkMode.text().toBoolean(),
                    user: user,
                    menuHidden: preferencesXml.menuHidden && preferencesXml.menuHidden[0] ? preferencesXml.menuHidden[0].attributes() : [:]
            )
            if (preferencesXml.menu && preferencesXml.menu[0]) {
                user.preferences.menu = preferencesXml.menu[0].attributes()
            }
            if (options.save) {
                user.save()
            }
            // Child objects
            options.userPreferences = user.preferences
            preferencesXml.widgets.widget.each {
                widgetService.unMarshall(it, options)
            }
            options.userPreferences = null
            return (User) importDomainsPlugins(userXml, user, options)
        }
    }
}
