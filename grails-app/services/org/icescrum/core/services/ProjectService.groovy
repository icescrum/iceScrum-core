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
import groovy.xml.MarkupBuilder
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.utils.ServicesUtils
import org.icescrum.core.error.BusinessException
import java.text.SimpleDateFormat
import org.icescrum.core.domain.preferences.ProjectPreferences
import org.icescrum.core.domain.security.Authority
import org.springframework.security.access.prepost.PreAuthorize
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport
import grails.plugin.springsecurity.SpringSecurityUtils
import org.icescrum.core.domain.preferences.UserPreferences

@Transactional
class ProjectService extends IceScrumEventPublisher {

    def springSecurityService
    def securityService
    def teamService
    def actorService
    def grailsApplication
    def clicheService
    def notificationEmailService

    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('isAuthenticated()')
    void save(Project project, productOwners, stakeHolders) {
        project.orderNumber = (Project.count() ?: 0) + 1
        project.save(flush: true)
        createDefaultBacklogs(project)
        securityService.secureDomain(project)
        if (productOwners) {
            for (productOwner in User.getAll(productOwners*.toLong())) {
                if (productOwner) {
                    addRole(project, productOwner, Authority.PRODUCTOWNER)
                }
            }
        }
        if (stakeHolders && project.preferences.hidden) {
            for (stakeHolder in User.getAll(stakeHolders*.toLong())) {
                if (stakeHolder) {
                    addRole(project, stakeHolder, Authority.STAKEHOLDER)
                }
            }
        }
        manageProjectEvents(project, [:])
        publishSynchronousEvent(IceScrumEventType.CREATE, project)
    }

    @PreAuthorize('owner(#team) and !archivedProject(#project)')
    void addTeamToProject(Project project, Team team) {
        def oldMembers = getAllMembersProjectByRole(project)
        project.addToTeams(team)
        if (!project.save()) {
            throw new BusinessException(code: 'Project not saved')
        }
        manageProjectEvents(project, oldMembers)
    }

    @PreAuthorize('owner(#project.firstTeam) and owner(#newTeam) and !archivedProject(#project)')
    void changeTeam(Project project, Team newTeam) {
        def oldTeam = project.firstTeam
        def oldMembers = getAllMembersProjectByRole(project)
        // Switch team
        project.removeFromTeams(oldTeam)
        project.addToTeams(newTeam)
        // Remove conflicting POs and SHs
        removeConflictingPOandSH(newTeam, project)
        removeConflictingInvitedPOandSH(newTeam, project)
        // Broadcasts and events
        manageProjectEvents(project, oldMembers)
    }

    @PreAuthorize('scrumMaster(#project) and !archivedProject(#project)')
    void update(Project project, boolean hasHiddenChanged, String pkeyChanged) {
        if (!project.name?.trim()) {
            throw new BusinessException(code: 'is.project.error.no.name')
        }
        if (hasHiddenChanged && project.preferences.hidden && !ApplicationSupport.booleanValue(grailsApplication.config.icescrum.project.private.enable)
                && !SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            project.preferences.hidden = false
        }
        if (hasHiddenChanged && !project.preferences.hidden) {
            project.stakeHolders?.each {
                removeStakeHolder(project, it)
            }
            project.invitedStakeHolders?.each {
                it.delete()
            }
        }
        if (pkeyChanged) {
            UserPreferences.findAllByLastProjectOpened(pkeyChanged)?.each {
                it.lastProjectOpened = project.pkey
                it.save()
            }
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, project)
        project.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, project, dirtyProperties)
    }

    @PreAuthorize('stakeHolder(#project) or inProject(#project)')
    def cumulativeFlowValues(Project project) {
        def values = []
        project.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each { Release release ->
            def cliches = []
            //begin of project
            def firstClicheActivation = Cliche.findByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])
            if(firstClicheActivation)
                cliches.add(firstClicheActivation)
            //others cliches
            cliches.addAll(Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"]))
            //transient cliche
            if(release.state == Release.STATE_INPROGRESS){
                def sprint = null
                sprint = release.sprints.find{it.state == Sprint.STATE_INPROGRESS}
                if(sprint){
                    cliches << [data:clicheService.generateSprintClicheData(sprint, Cliche.TYPE_CLOSE)]
                }
            }
            cliches?.eachWithIndex { cliche, index ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    values << [
                            suggested : xmlRoot."${Cliche.SUGGESTED_STORIES}".toInteger(),
                            accepted  : xmlRoot."${Cliche.ACCEPTED_STORIES}".toInteger(),
                            estimated : xmlRoot."${Cliche.ESTIMATED_STORIES}".toInteger(),
                            planned   : xmlRoot."${Cliche.PLANNED_STORIES}".toInteger(),
                            inprogress: xmlRoot."${Cliche.INPROGRESS_STORIES}".toInteger(),
                            done      : xmlRoot."${Cliche.FINISHED_STORIES}".toInteger(),
                            label: index == 0 ? "Start" : xmlRoot."${Cliche.SPRINT_ID}".toString()+"${cliche.id ?: " (progress)"}"
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#project) or inProject(#project)')
    def projectBurnupValues(Project project) {
        def values = []
        project.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each {Release release ->
            def cliches = []
            //begin of project
            def firstClicheActivation = Cliche.findByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])
            if(firstClicheActivation)
                cliches.add(firstClicheActivation)
            //others cliches
            cliches.addAll(Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"]))
            //transient cliche
            if(release.state == Release.STATE_INPROGRESS){
                def sprint = null
                sprint = release.sprints.find{it.state == Sprint.STATE_INPROGRESS}
                if(sprint){
                    cliches << [data:clicheService.generateSprintClicheData(sprint, Cliche.TYPE_CLOSE)]
                }
            }
            cliches?.eachWithIndex { cliche, index ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    def a = xmlRoot."${Cliche.PROJECT_POINTS}".toBigDecimal()
                    def b = xmlRoot."${Cliche.PROJECT_REMAINING_POINTS}".toBigDecimal()
                    def c = a - b
                    values << [
                            all  : xmlRoot."${Cliche.PROJECT_POINTS}".toBigDecimal(),
                            done : c,
                            label: index == 0 ? "Start" : xmlRoot."${Cliche.SPRINT_ID}".toString()+"${cliche.id ?: " (progress)"}"
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#project) or inProject(#project)')
    def projectBurndownValues(Project project) {
        def values = []
        def releaseService = (ReleaseService) grailsApplication.mainContext.getBean('releaseService')
        project.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each { Release release ->
            values.addAll(releaseService.releaseBurndownValues(release))
        }
        return values
    }

    @PreAuthorize('stakeHolder(#project) or inProject(#project)')
    def projectVelocityValues(Project project) {
        def values = []
        project.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each {
            Cliche.findAllByParentTimeBoxAndType(it, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"])?.each { cliche ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    def sprintEntry = [
                            userstories     : xmlRoot."${Cliche.FUNCTIONAL_STORY_VELOCITY}".toBigDecimal(),
                            defectstories   : xmlRoot."${Cliche.DEFECT_STORY_VELOCITY}".toBigDecimal(),
                            technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_VELOCITY}".toBigDecimal(),
                            label           : xmlRoot."${Cliche.SPRINT_ID}".toString()
                    ]
                    sprintEntry << computeLabelsForSprintEntry(sprintEntry)
                    values << sprintEntry
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#project) or inProject(#project)')
    def projectVelocityCapacityValues(Project project) {
        def values = []
        def capacity = 0, label = ""
        project.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each {
            Cliche.findAllByParentTimeBox(it, [sort: "datePrise", order: "asc"])?.each { cliche ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    if (cliche.type == Cliche.TYPE_ACTIVATION) {
                        capacity = xmlRoot."${Cliche.SPRINT_CAPACITY}".toBigDecimal()
                        label = xmlRoot."${Cliche.SPRINT_ID}".toString()
                    }
                    if (cliche.type == Cliche.TYPE_CLOSE) {
                        values << [
                                capacity: capacity,
                                velocity: xmlRoot."${Cliche.SPRINT_VELOCITY}".toBigDecimal(),
                                label   : label
                        ]
                    }
                }
            }
        }
        return values
    }

    private static Map computeLabelsForSprintEntry(sprintEntry) {
        def computePercents = { part ->
            def total = sprintEntry.userstories + sprintEntry.technicalstories + sprintEntry.defectstories
            total ? (Integer) Math.ceil(part / total * 100) : 0
        }
        def generateLabel = { part, percents ->
            percents > 0 ? part + ' (' + percents + '%)' : ''
        }
        def labels = [:]
        def percentsUS = computePercents(sprintEntry.userstories)
        def percentsTechnical = computePercents(sprintEntry.technicalstories)
        def percentsDefect = 100 - percentsUS - percentsTechnical
        labels['userstoriesLabel'] = generateLabel(sprintEntry.userstories, percentsUS)
        labels['technicalstoriesLabel'] = generateLabel(sprintEntry.userstories + sprintEntry.technicalstories, percentsTechnical)
        labels['defectstoriesLabel'] = generateLabel(sprintEntry.userstories + sprintEntry.technicalstories + sprintEntry.defectstories, percentsDefect)
        labels
    }

    @PreAuthorize('isAuthenticated()')
    Project unMarshall(def projectXml, def options) {
        Project.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def project = new Project(
                        name: projectXml."${'name'}".text(),
                        pkey: projectXml.pkey.text(),
                        description: projectXml.description.text(),
                        lastUpdated: projectXml.lastUpdated.text() ? ApplicationSupport.parseDate(projectXml.lastUpdated.text()) : new Date(),
                        todoDate: ApplicationSupport.parseDate(projectXml.todoDate?.text() ?: projectXml.dateCreated.text()),
                        startDate: ApplicationSupport.parseDate(projectXml.startDate.text()),
                        endDate: ApplicationSupport.parseDate(projectXml.endDate.text()),
                        planningPokerGameType: projectXml.planningPokerGameType.text().toInteger())

                project.preferences = new ProjectPreferences(
                        hidden: projectXml.preferences.hidden.text().toBoolean(),
                        assignOnBeginTask: projectXml.preferences.assignOnBeginTask.text().toBoolean(),
                        assignOnCreateTask: projectXml.preferences.assignOnCreateTask.text().toBoolean(),
                        autoCreateTaskOnEmptyStory: projectXml.preferences.autoCreateTaskOnEmptyStory.text().toBoolean(),
                        autoDoneStory: projectXml.preferences.autoDoneStory.text().toBoolean(),
                        noEstimation: projectXml.preferences.noEstimation.text().toBoolean(),
                        limitUrgentTasks: projectXml.preferences.limitUrgentTasks.text().toInteger(),
                        estimatedSprintsDuration: projectXml.preferences.estimatedSprintsDuration.text().toInteger(),
                        displayUrgentTasks: projectXml.preferences.displayUrgentTasks.text().toBoolean(),
                        displayRecurrentTasks: projectXml.preferences.displayRecurrentTasks.text().toBoolean(),
                        hideWeekend: projectXml.preferences.hideWeekend.text()?.toBoolean() ?: false,
                        releasePlanningHour: projectXml.preferences.releasePlanningHour.text() ?: "9:00",
                        sprintPlanningHour: projectXml.preferences.sprintPlanningHour.text() ?: "9:00",
                        dailyMeetingHour: projectXml.preferences.dailyMeetingHour.text() ?: "11:00",
                        sprintReviewHour: projectXml.preferences.sprintReviewHour.text() ?: "14:00",
                        sprintRetrospectiveHour: projectXml.preferences.sprintRetrospectiveHour.text() ?: "16:00",
                        timezone: projectXml.preferences?.timezone?.text() ?: grailsApplication.config.icescrum.timezone.default)

                options.project = project

                projectXml.teams.team.eachWithIndex { it, index ->
                    teamService.unMarshall(it, options)
                }

                Project pExist = (Project) Project.findByPkey(project.pkey)
                if (pExist && securityService.productOwner(pExist, springSecurityService.authentication)) {
                    project.erasableByUser = true
                }

                def productOwnersList = []
                projectXml.productOwners.user.eachWithIndex { productOwner, index ->
                    User u
                    if (!productOwner.@uid?.isEmpty())
                        u = ((User) project.getAllUsers().find { it.uid == productOwner.@uid.text() }) ?: null
                    else {
                        u = ApplicationSupport.findUserUIDOldXMl(productOwner, null, project.getAllUsers())
                    }
                    if (!u) {
                        u = User.findByUsernameAndEmail(productOwner.username.text(), productOwner.email.text())
                        if (!u) {
                            def userService = (UserService) grailsApplication.mainContext.getBean('userService')
                            userService.unMarshall(productOwner, options)
                        }
                    }
                    productOwnersList << u
                }
                project.productOwners = productOwnersList

                def erase = options.changes?.erase ? true : false
                if (options.changes) {
                    def team = project.teams[0]
                    if (options.changes?.team?.name) {
                        team.name = options.changes.team.name
                    }
                    if (options.changes?.users) {
                        team.members?.each {
                            if (options.changes.users."${it.uid}") {
                                it.username = options.changes.users."${it.uid}"
                            }
                        }
                        team.scrumMasters?.each {
                            if (options.changes.users."${it.uid}") {
                                it.username = options.changes.users."${it.uid}"
                            }
                        }
                        project.productOwners?.each {
                            if (options.changes.users."${it.uid}") {
                                it.username = options.changes.users."${it.uid}"
                            }
                        }
                    }
                    project.pkey = !erase && options.changes?.project?.pkey != null ? options.changes.project.pkey : project.pkey
                    project.name = !erase && options.changes?.project?.name != null ? options.changes.project.name : project.name
                }
                if (options.validate) {
                    options.changesNeeded = validate(project, erase)
                    if (options.changesNeeded) {
                        return null
                    }
                }
                if (options.save) {
                    if (erase && pExist) {
                        delete(pExist)
                    }
                    project.save()
                    project.teams.each { t ->
                        // Save users before team
                        t.members?.each {
                            if (it.id == null)
                                it.save()
                        }

                        if (t.id == null)
                            teamService.saveImport(t)
                        else {
                            def ts = Team.get(t.id)
                            ts.removeFromProjects(project)
                            ts.addToProjects(project)
                        }
                    }
                    securityService.secureDomain(project)
                    if (project.productOwners) {
                        project.productOwners?.eachWithIndex { user, index ->
                            user = User.get(user.id)
                            securityService.createProductOwnerPermissions(user, project)
                        }
                        def user = project.productOwners.first()
                        user = User.get(user.id)
                        securityService.changeOwner(user, project)
                    } else {
                        def user = User.get(springSecurityService.principal.id)
                        securityService.createProductOwnerPermissions(user, project)
                        securityService.changeOwner(user, project)
                    }
                }

                // Child objects
                def featureService = (FeatureService) grailsApplication.mainContext.getBean('featureService')
                projectXml.features.feature.eachWithIndex { it, index ->
                    featureService.unMarshall(it, options)
                }
                projectXml.actors.actor.eachWithIndex { it, index ->
                    actorService.unMarshall(it, options)
                }
                def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
                projectXml.stories.story.eachWithIndex { it, index ->
                    storyService.unMarshall(it, options)
                }
                // Ensure rank for stories in backlog
                def stories = project.stories.findAll {
                    it.state == Story.STATE_ACCEPTED || it.state == Story.STATE_ESTIMATED
                }.sort { a, b -> a.rank <=> b.rank }
                stories.eachWithIndex { it, index ->
                    it.rank = index + 1
                    if (options.save) {
                        it.save()
                    }
                }

                def releaseService = (ReleaseService) grailsApplication.mainContext.getBean('releaseService')
                projectXml.releases.release.eachWithIndex { it, index ->
                    releaseService.unMarshall(it, options)
                }
                // Ensure rank for stories in each sprint
                project.releases.each { Release release ->
                    release.sprints.each { Sprint sprint ->
                        // First ranks for planned and in progress stories
                        stories = sprint.stories.findAll { Story story -> story.state == Story.STATE_PLANNED || story.state == Story.STATE_INPROGRESS }.sort { a, b -> a.rank <=> b.rank }
                        stories.eachWithIndex { Story story, index ->
                            story.rank = index + 1
                            if (options.save) {
                                story.save()
                            }
                        }
                        // Lask ranks for done stories
                        def maxRank = stories.size()
                        stories = sprint.stories.findAll { Story story -> story.state == Story.STATE_DONE }.sort { a, b -> a.rank <=> b.rank }
                        stories.each { Story story ->
                            story.rank = ++maxRank
                            if (options.save) {
                                story.save()
                            }
                        }
                    }
                }
                if (options.save) {
                    project.save()
                }
                options.project = null
                return (Project) importDomainsPlugins(projectXml, project, options)
            } catch (Exception e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
                throw new RuntimeException(e)
            }
        }
    }

    @PreAuthorize('isAuthenticated()')
    def importXML(File file, def options) {
        Project.withTransaction(readOnly: !options.save) {
            String xmlText = file.getText()
            String cleanedXmlText = ServicesUtils.cleanXml(xmlText)
            def _projectXml = new XmlSlurper().parseText(cleanedXmlText)
            def Project project = null
            try {
                def projectXml = _projectXml
                // Be compatible with xml without export tag
                if (_projectXml.find { it.name == 'export' }) {
                    projectXml = _projectXml.project
                }
                project = this.unMarshall(projectXml, options)
            } catch (RuntimeException e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
            }
            if (project?.id && options.save) {
                project.save(flush: true)
            }
            return project
        }
    }

    @PreAuthorize('isAuthenticated()')
    def validate(Project project, boolean erase = false) {
        def changes = [:]
        try {
            Project.withNewSession {
                project.teams.eachWithIndex { team, index ->
                    team.validate()
                    if (team.errors.errorCount == 1) {
                        changes.team = [:]
                        if (team.errors.fieldErrors[0]?.field == 'name') {
                            changes.team.name = team.name
                        } else {
                            if (log.infoEnabled) {
                                log.info("Team validation error (${team.name}): " + team.errors)
                            }
                            throw new RuntimeException()
                        }
                    } else if (team.errors.errorCount > 1) {
                        if (log.infoEnabled) {
                            log.info("Team validation error (${team.name}): " + team.errors)
                        }
                        throw new RuntimeException()
                    }
                    team.members.eachWithIndex { member, index2 ->
                        member.validate()
                        if (member.errors.errorCount == 1) {
                            changes.users = changes.users ?: [:]
                            if (member.errors.fieldErrors[0]?.field == 'username') {
                                changes.users."$member.uid" = member.username
                            } else {
                                if (log.infoEnabled) {
                                    log.info("User validation error (${member.username}): " + member.errors)
                                }
                                throw new RuntimeException()
                            }
                        } else if (member.errors.errorCount > 1) {
                            if (log.infoEnabled) {
                                log.info("User validation error (${member.username}): " + member.errors)
                            }
                            throw new RuntimeException()
                        }
                    }
                }
                project.productOwners?.eachWithIndex { productOwner, index ->
                    productOwner.validate()
                    if (productOwner.errors.errorCount == 1) {
                        changes.users = changes.users ?: [:]
                        if (productOwner.errors.fieldErrors[0]?.field == 'username' && !(productOwner.username in changes.users)) {
                            changes.users."$productOwner.uid" = productOwner.username
                        } else {
                            if (log.infoEnabled) {
                                log.info("User validation error (${productOwner.username}): " + productOwner.errors)
                            }
                            throw new RuntimeException()
                        }
                    } else if (productOwner.errors.errorCount > 1) {
                        if (log.infoEnabled) {
                            log.info("User validation error (${productOwner.username}): " + productOwner.errors)
                        }
                        throw new RuntimeException()
                    }
                }
                project.validate()
                if (project.errors.errorCount && project.errors.errorCount <= 2 && !erase) {
                    changes.project = [:]
                    project.errors.fieldErrors*.field.each { field ->
                        if (!(field in ['pkey', 'name'])) {
                            if (log.infoEnabled) {
                                log.info("Project validation error (${project.name}): " + project.errors)
                            }
                            throw new RuntimeException()
                        } else {
                            changes.project[field] = project[field]
                        }
                    }
                    changes.erasable = project.erasableByUser
                } else if (project.errors.errorCount > 2) {
                    if (log.infoEnabled) {
                        log.info("Project validation error (${project.name}): " + project.errors)
                    }
                    throw new RuntimeException()
                }
                return changes
            }
        } catch (Exception e) {
            if (log.debugEnabled) {
                e.printStackTrace()
            }
        }
    }

    @PreAuthorize('owner(#p.firstTeam)')
    def delete(Project p) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, p)
        p.allUsers.each { it.preferences.removeEmailsSettings(p.pkey) } // must be before unsecure to have POs
        p.invitedStakeHolders*.delete()
        p.invitedProductOwners*.delete()
        securityService.unsecureDomain p
        UserPreferences.findAllByLastProjectOpened(p.pkey)?.each {
            it.lastProjectOpened = null
        }
        p.teams.each {
            it.removeFromProjects(p)
        }
        p.removeAllAttachments()
        p.delete(flush: true)
        publishSynchronousEvent(IceScrumEventType.DELETE, p, dirtyProperties)
    }

    @PreAuthorize('scrumMaster(#project)')
    def archive(Project project) {
        project.preferences.archived = true
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, project)
        project.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, project, dirtyProperties)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    def unArchive(Project project) {
        project.preferences.archived = false
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, project)
        project.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, project, dirtyProperties)
    }

    void removeAllRoles(domain, User user) {
        if (domain instanceof Team) {
            teamService.removeMemberOrScrumMaster(domain, user)
        } else if (domain instanceof Project) {
            removeProductOwner(domain, user)
            removeStakeHolder(domain, user)
        }
    }

    void addRole(domain, User user, int role) {
        if (domain instanceof Team) {
            if (role == Authority.SCRUMMASTER) {
                teamService.addScrumMaster(domain, user)
            } else if (role == Authority.MEMBER) {
                teamService.addMember(domain, user)
            }
        } else if (domain instanceof Project) {
            if (role == Authority.PRODUCTOWNER) {
                addProductOwner(domain, user)
            } else if (role == Authority.STAKEHOLDER) {
                addStakeHolder(domain, user)
            }
        }
    }

    void updateTeamMembers(Team team, List newMembers) {
        def oldMembersByProject = [:]
        team.projects.each { Project project ->
            oldMembersByProject[project.id] = getAllMembersProjectByRole(project)
        }
        def currentMembers = team.scrumMasters.collect { [id: it.id, role: Authority.SCRUMMASTER] }
        team.members.each { member ->
            if (!currentMembers.any { it.id == member.id }) {
                currentMembers << [id: member.id, role: Authority.MEMBER]
            }
        }
        updateMembers(team, currentMembers, newMembers)
        removeConflictingPOandSH(team)
        oldMembersByProject.each { Long projectId, Map oldMembers ->
            manageProjectEvents(Project.get(projectId), oldMembers)
        }
    }

    void updateProjectMembers(Project project, List newMembers) {
        def oldMembers = getAllMembersProjectByRole(project)
        def currentMembers = project.stakeHolders.collect { [id: it.id, role: Authority.STAKEHOLDER] } + project.productOwners.collect { [id: it.id, role: Authority.PRODUCTOWNER] }
        updateMembers(project, currentMembers, newMembers)
        manageProjectEvents(project, oldMembers)
    }

    private void updateMembers(domain, List currentMembers, List newMembers) {
        newMembers.each { newMember ->
            User user = User.get(newMember.id)
            int role = newMember.role
            def found = currentMembers.find { it.id == user.id }
            if (found) {
                if (found.role != role) {
                    removeAllRoles(domain, user)
                    addRole(domain, user, role)
                }
            } else {
                addRole(domain, user, role)
            }
        }
        currentMembers*.id.minus(newMembers*.id).each {
            removeAllRoles(domain, User.get(it))
        }
    }

    private void removeConflictingPOandSH(team, project = null) {
        def membersIds = team.members*.id
        def tmIds = team.members*.id - team.scrumMasters*.id
        def projects = project ? [project] : team.projects
        projects.each { Project p ->
            p.productOwners?.each { User po ->
                if (po.id in tmIds) {
                    removeAllRoles(p, po)
                }
            }
            p.stakeHolders?.each { User sh ->
                if (sh.id in membersIds) {
                    removeAllRoles(p, sh)
                }
            }
        }
    }

    private void addProductOwner(Project project, User productOwner) {
        securityService.createProductOwnerPermissions productOwner, project
    }

    private void addStakeHolder(Project project, User stakeHolder) {
        if (project.preferences.hidden) {
            securityService.createStakeHolderPermissions stakeHolder, project
        }
    }

    private void removeProductOwner(Project project, User productOwner) {
        securityService.deleteProductOwnerPermissions productOwner, project
    }

    private void removeStakeHolder(Project project, User stakeHolder) {
        securityService.deleteStakeHolderPermissions stakeHolder, project
    }

    // INVITATIONS

    void manageTeamInvitations(Team team, invitedMembers, invitedScrumMasters) {
        invitedMembers = invitedMembers*.toLowerCase()
        invitedScrumMasters = invitedScrumMasters*.toLowerCase()
        def type = Invitation.InvitationType.TEAM
        def currentInvitations = Invitation.findAllByTypeAndTeam(type, team)
        def newInvitations = []
        assert !invitedMembers.intersect(invitedScrumMasters)
        newInvitations.addAll(invitedMembers.collect { [role: Authority.MEMBER, email: it] })
        newInvitations.addAll(invitedScrumMasters.collect { [role: Authority.SCRUMMASTER, email: it] })
        manageInvitations(currentInvitations, newInvitations, type, null, team)
        removeConflictingInvitedPOandSH(team)
    }

    void manageProjectInvitations(Project project, invitedProductOwners, invitedStakeHolders) {
        invitedProductOwners = invitedProductOwners*.toLowerCase()
        invitedStakeHolders = invitedStakeHolders*.toLowerCase()
        def type = Invitation.InvitationType.PROJECT
        def currentInvitations = Invitation.findAllByTypeAndProject(type, project)
        def newInvitations = []
        assert !invitedProductOwners.intersect(invitedStakeHolders)
        newInvitations.addAll(invitedProductOwners.collect { [role: Authority.PRODUCTOWNER, email: it] })
        if (invitedStakeHolders && project.preferences.hidden) {
            newInvitations.addAll(invitedStakeHolders.collect { [role: Authority.STAKEHOLDER, email: it] })
        }
        manageInvitations(currentInvitations, newInvitations, type, project, null)
    }

    List<Project> getAllActiveProjectsByUser(User user, String searchTerm = '') {
        def projects = Project.findAllByUserAndActive(user, [sort: "name", order: "asc", cache: true], searchTerm)
        def projectsOwnerOf = Team.findAllActiveProjectsByTeamOwner(user.username, searchTerm, [sort: "name", order: "asc", cache: true]).findAll {
            !(it in projects)
        }
        projects.addAll(projectsOwnerOf)
        return projects
    }

    private void removeConflictingInvitedPOandSH(team, project = null) {
        def invitedMembersEmail = team.invitedMembers*.email
        def invitedMembersAndScrumMastersEmail = invitedMembersEmail + team.invitedScrumMasters*.email
        def projects = project ? [project] : team.projects
        projects.each { Project p ->
            p.invitedProductOwners?.each { Invitation invitedPo ->
                if (invitedPo.email in invitedMembersEmail) {
                    invitedPo.delete()
                }
            }
            p.invitedStakeHolders?.each { Invitation invitedSh ->
                if (invitedSh.email in invitedMembersAndScrumMastersEmail) {
                    invitedSh.delete()
                }
            }
        }
    }

    private void manageInvitations(List<Invitation> currentInvitations, List newInvitations, Invitation.InvitationType type, Project project, Team team) {
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
                def invitation = new Invitation(email: email, futureRole: role, type: type)
                if (type == Invitation.InvitationType.TEAM) {
                    invitation.team = team
                } else {
                    invitation.project = project
                }
                invitation.save()
                try {
                    notificationEmailService.sendInvitation(invitation, springSecurityService.currentUser)
                } catch (MailException) {
                    throw new BusinessException(code: 'is.mail.invitation.error')
                }
            }
        }
        currentInvitations.findAll { currentInvitation ->
            !newInvitations*.email.contains(currentInvitation.email)
        }*.delete()
    }

    // Quite experimental
    void manageProjectEvents(Project project, Map oldMembers) {
        Map newMembers = getAllMembersProjectByRole(project)
        if (project.hasProperty('membersByRole')) {
            project.membersByRole = newMembers
        } else {
            project.metaClass.membersByRole = newMembers
        }
        publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, project, [membersByRole: oldMembers])
        publishSynchronousEvent(IceScrumEventType.UPDATE, project, [membersByRole: oldMembers])
    }

    Map getAllMembersProjectByRole(Project project) {
        def usersByRole = [:]
        def productOwners = project.productOwners
        def team = project.firstTeam
        if (team) {
            def scrumMasters = team.scrumMasters
            team.members?.each { User member ->
                def role = Authority.MEMBER
                if (scrumMasters?.contains(member)) {
                    role = productOwners?.contains(member) ? Authority.PO_AND_SM : Authority.SCRUMMASTER
                }
                usersByRole[member] = role
            }
        }
        productOwners?.each { User productOwner ->
            if (!usersByRole.containsKey(productOwner)) {
                usersByRole[productOwner] = Authority.PRODUCTOWNER
            }
        }
        project.stakeHolders?.each { User stakeHolder ->
            usersByRole[stakeHolder] = Authority.STAKEHOLDER
        }
        return usersByRole
    }

    def export(StringWriter writer, Project project, String version) {
        def builder = new MarkupBuilder(writer)
        builder.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
        builder.export(version: version) {
            project.xml(builder)
        }
    }

    private void createDefaultBacklogs(Project project) {
        new Backlog(project: project, shared: true, filter: '{"story":{"state":1}}', name: 'is.ui.sandbox', code: 'sandbox').save()
        new Backlog(project: project, shared: true, filter: '{"story":{"state":[2,3]}}', name: 'is.ui.backlog', code: 'backlog').save()
        new Backlog(project: project, shared: true, filter: '{"story":{"state":7}}', name: 'todo.is.ui.backlog.done', code: 'done').save()
        new Backlog(project: project, shared: true, filter: '{"story":{}}', name: 'todo.is.ui.backlog.all', code: 'all').save()
    }
}
