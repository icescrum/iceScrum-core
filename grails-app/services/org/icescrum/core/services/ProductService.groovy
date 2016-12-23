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
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.security.Authority
import org.springframework.security.access.prepost.PreAuthorize
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport
import grails.plugin.springsecurity.SpringSecurityUtils
import org.icescrum.core.domain.preferences.UserPreferences

@Transactional
class ProductService extends IceScrumEventPublisher {

    def springSecurityService
    def securityService
    def teamService
    def actorService
    def grailsApplication
    def clicheService
    def notificationEmailService
    def templateService

    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('isAuthenticated()')
    void save(Product product, productOwners, stakeHolders) {
        product.orderNumber = (Product.count() ?: 0) + 1
        product.save(flush: true)
        createDefaultBacklogs(product)
        securityService.secureDomain(product)
        if (productOwners) {
            for (productOwner in User.getAll(productOwners*.toLong())) {
                if (productOwner) {
                    addRole(product, productOwner, Authority.PRODUCTOWNER)
                }
            }
        }
        if (stakeHolders && product.preferences.hidden) {
            for (stakeHolder in User.getAll(stakeHolders*.toLong())) {
                if (stakeHolder) {
                    addRole(product, stakeHolder, Authority.STAKEHOLDER)
                }
            }
        }
        def bugStory = new Story(type: Story.TYPE_DEFECT, backlog: product)
        templateService.save(new Template(name: g.message(code: 'is.ui.sandbox.story.template.default.defect')), bugStory)
        bugStory.delete()
        manageProductEvents(product, [:])
        publishSynchronousEvent(IceScrumEventType.CREATE, product)
    }

    @PreAuthorize('owner(#team) and !archivedProduct(#product)')
    void addTeamToProduct(Product product, Team team) {
        def oldMembers = getAllMembersProductByRole(product)
        product.addToTeams(team)
        if (!product.save()) {
            throw new BusinessException(code: 'Product not saved')
        }
        manageProductEvents(product, oldMembers)
    }

    @PreAuthorize('owner(#product.firstTeam) and owner(#newTeam) and !archivedProduct(#product)')
    void changeTeam(Product product, Team newTeam) {
        def oldTeam = product.firstTeam
        def oldMembers = getAllMembersProductByRole(product)
        // Switch team
        product.removeFromTeams(oldTeam)
        product.addToTeams(newTeam)
        // Remove conflicting POs and SHs
        removeConflictingPOandSH(newTeam, product)
        removeConflictingInvitedPOandSH(newTeam, product)
        // Broadcasts and events
        manageProductEvents(product, oldMembers)
    }

    @PreAuthorize('scrumMaster(#product) and !archivedProduct(#product)')
    void update(Product product, boolean hasHiddenChanged, String pkeyChanged) {
        if (!product.name?.trim()) {
            throw new BusinessException(code: 'is.product.error.no.name')
        }
        if (hasHiddenChanged && product.preferences.hidden && !ApplicationSupport.booleanValue(grailsApplication.config.icescrum.project.private.enable)
                && !SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            product.preferences.hidden = false
        }
        if (hasHiddenChanged && !product.preferences.hidden) {
            product.stakeHolders?.each {
                removeStakeHolder(product, it)
            }
            product.invitedStakeHolders?.each {
                it.delete()
            }
        }
        if (pkeyChanged) {
            UserPreferences.findAllByLastProductOpened(pkeyChanged)?.each {
                it.lastProductOpened = product.pkey
                it.save()
            }
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, product)
        product.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, product, dirtyProperties)
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def cumulativeFlowValues(Product product) {
        def values = []
        product.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each { Release release ->
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
                            label: index == 0 ? "Start" : xmlRoot."${Cliche.SPRINT_ID}".toString()+"${cliche.type == Cliche.TYPE_ACTIVATION ? " (progress)" : ""}"
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productBurnupValues(Product product) {
        def values = []
        product.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each {Release release ->
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
                    def a = xmlRoot."${Cliche.PRODUCT_BACKLOG_POINTS}".toBigDecimal()
                    def b = xmlRoot."${Cliche.PRODUCT_REMAINING_POINTS}".toBigDecimal()
                    def c = a - b
                    values << [
                            all  : xmlRoot."${Cliche.PRODUCT_BACKLOG_POINTS}".toBigDecimal(),
                            done : c,
                            label: index == 0 ? "Start" : xmlRoot."${Cliche.SPRINT_ID}".toString()+"${cliche.type == Cliche.TYPE_ACTIVATION ? " (progress)" : ""}"
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productBurndownValues(Product product) {
        def values = []
        def releaseService = (ReleaseService) grailsApplication.mainContext.getBean('releaseService')
        product.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each { Release release ->
            values.addAll(releaseService.releaseBurndownValues(release))
        }
        return values
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productVelocityValues(Product product) {
        def values = []
        product.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each {
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

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productVelocityCapacityValues(Product product) {
        def values = []
        def capacity = 0, label = ""
        product.releases?.sort { a, b -> a.orderNumber <=> b.orderNumber }?.each {
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
    Product unMarshall(def productXml, def options) {
        Product.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def product = new Product(
                        name: productXml."${'name'}".text(),
                        pkey: productXml.pkey.text(),
                        description: productXml.description.text(),
                        lastUpdated: productXml.lastUpdated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(productXml.lastUpdated.text()) : new Date(),
                        todoDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(productXml.todoDate?.text() ?: productXml.dateCreated.text()),
                        startDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(productXml.startDate.text()),
                        endDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(productXml.endDate.text()),
                        planningPokerGameType: productXml.planningPokerGameType.text().toInteger())

                product.preferences = new ProductPreferences(
                        hidden: productXml.preferences.hidden.text().toBoolean(),
                        assignOnBeginTask: productXml.preferences.assignOnBeginTask.text().toBoolean(),
                        assignOnCreateTask: productXml.preferences.assignOnCreateTask.text().toBoolean(),
                        autoCreateTaskOnEmptyStory: productXml.preferences.autoCreateTaskOnEmptyStory.text().toBoolean(),
                        autoDoneStory: productXml.preferences.autoDoneStory.text().toBoolean(),
                        noEstimation: productXml.preferences.noEstimation.text().toBoolean(),
                        limitUrgentTasks: productXml.preferences.limitUrgentTasks.text().toInteger(),
                        estimatedSprintsDuration: productXml.preferences.estimatedSprintsDuration.text().toInteger(),
                        displayUrgentTasks: productXml.preferences.displayUrgentTasks.text().toBoolean(),
                        displayRecurrentTasks: productXml.preferences.displayRecurrentTasks.text().toBoolean(),
                        hideWeekend: productXml.preferences.hideWeekend.text()?.toBoolean() ?: false,
                        releasePlanningHour: productXml.preferences.releasePlanningHour.text() ?: "9:00",
                        sprintPlanningHour: productXml.preferences.sprintPlanningHour.text() ?: "9:00",
                        dailyMeetingHour: productXml.preferences.dailyMeetingHour.text() ?: "11:00",
                        sprintReviewHour: productXml.preferences.sprintReviewHour.text() ?: "14:00",
                        sprintRetrospectiveHour: productXml.preferences.sprintRetrospectiveHour.text() ?: "16:00",
                        timezone: productXml.preferences?.timezone?.text() ?: grailsApplication.config.icescrum.timezone.default)

                options.product = product

                productXml.teams.team.eachWithIndex { it, index ->
                    teamService.unMarshall(it, options)
                }

                Product pExist = (Product) Product.findByPkey(product.pkey)
                if (pExist && securityService.productOwner(pExist, springSecurityService.authentication)) {
                    product.erasableByUser = true
                }

                def productOwnersList = []
                productXml.productOwners.user.eachWithIndex { productOwner, index ->
                    User u
                    if (!productOwner.@uid?.isEmpty())
                        u = ((User) product.getAllUsers().find { it.uid == productOwner.@uid.text() }) ?: null
                    else {
                        u = ApplicationSupport.findUserUIDOldXMl(productOwner, null, product.getAllUsers())
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
                product.productOwners = productOwnersList

                def erase = options.changes?.erase ? true : false
                if (options.changes) {
                    def team = product.teams[0]
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
                        product.productOwners?.each {
                            if (options.changes.users."${it.uid}") {
                                it.username = options.changes.users."${it.uid}"
                            }
                        }
                    }
                    product.pkey = !erase && options.changes?.product?.pkey != null ? options.changes.product.pkey : product.pkey
                    product.name = !erase && options.changes?.product?.name != null ? options.changes.product.name : product.name
                }
                if (options.validate) {
                    options.changesNeeded = validate(product, erase)
                    if (options.changesNeeded) {
                        return null
                    }
                }
                if (options.save) {
                    if (erase && pExist) {
                        delete(pExist)
                    }
                    product.save()
                    product.teams.each { t ->
                        // Save users before team
                        t.members?.each {
                            if (it.id == null)
                                it.save()
                        }

                        if (t.id == null)
                            teamService.saveImport(t)
                        else {
                            def ts = Team.get(t.id)
                            ts.removeFromProducts(product)
                            ts.addToProducts(product)
                        }
                    }
                    securityService.secureDomain(product)
                    if (product.productOwners) {
                        product.productOwners?.eachWithIndex { user, index ->
                            user = User.get(user.id)
                            securityService.createProductOwnerPermissions(user, product)
                        }
                        def user = product.productOwners.first()
                        user = User.get(user.id)
                        securityService.changeOwner(user, product)
                    } else {
                        def user = User.get(springSecurityService.principal.id)
                        securityService.createProductOwnerPermissions(user, product)
                        securityService.changeOwner(user, product)
                    }
                }

                // Child objects
                def featureService = (FeatureService) grailsApplication.mainContext.getBean('featureService')
                productXml.features.feature.eachWithIndex { it, index ->
                    featureService.unMarshall(it, options)
                }
                productXml.actors.actor.eachWithIndex { it, index ->
                    actorService.unMarshall(it, options)
                }
                def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
                productXml.stories.story.eachWithIndex { it, index ->
                    storyService.unMarshall(it, options)
                }
                // Ensure rank for stories in backlog
                def stories = product.stories.findAll {
                    it.state == Story.STATE_ACCEPTED || it.state == Story.STATE_ESTIMATED
                }.sort { a, b -> a.rank <=> b.rank }
                stories.eachWithIndex { it, index ->
                    it.rank = index + 1
                    if (options.save) {
                        it.save()
                    }
                }

                def releaseService = (ReleaseService) grailsApplication.mainContext.getBean('releaseService')
                productXml.releases.release.eachWithIndex { it, index ->
                    releaseService.unMarshall(it, options)
                }
                // Ensure rank for stories in each sprint
                product.releases.each { Release release ->
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
                    product.save()
                }
                options.product = null
                return (Product) importDomainsPlugins(product, options)
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
        Product.withTransaction(readOnly: !options.save) {
            String xmlText = file.getText()
            String cleanedXmlText = ServicesUtils.cleanXml(xmlText)
            def _productXml = new XmlSlurper().parseText(cleanedXmlText)
            def Product product = null
            try {
                def productXml = _productXml
                // Be compatible with xml without export tag
                if (_productXml.find { it.name == 'export' }) {
                    productXml = _productXml.product
                }
                product = this.unMarshall(productXml, options)
            } catch (RuntimeException e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
            }
            if (product?.id && options.save) {
                product.save(flush: true)
            }
            return product
        }
    }

    @PreAuthorize('isAuthenticated()')
    def validate(Product product, boolean erase = false) {
        def changes = [:]
        try {
            Product.withNewSession {
                product.teams.eachWithIndex { team, index ->
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
                product.productOwners?.eachWithIndex { productOwner, index ->
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
                product.validate()
                if (product.errors.errorCount && product.errors.errorCount <= 2 && !erase) {
                    changes.product = [:]
                    product.errors.fieldErrors*.field.each { field ->
                        if (!(field in ['pkey', 'name'])) {
                            if (log.infoEnabled) {
                                log.info("Product validation error (${product.name}): " + product.errors)
                            }
                            throw new RuntimeException()
                        } else {
                            changes.product[field] = product[field]
                        }
                    }
                    changes.erasable = product.erasableByUser
                } else if (product.errors.errorCount > 2) {
                    if (log.infoEnabled) {
                        log.info("Product validation error (${product.name}): " + product.errors)
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
    def delete(Product p) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, p)
        p.allUsers.each { it.preferences.removeEmailsSettings(p.pkey) } // must be before unsecure to have POs
        p.invitedStakeHolders*.delete()
        p.invitedProductOwners*.delete()
        securityService.unsecureDomain p
        UserPreferences.findAllByLastProductOpened(p.pkey)?.each {
            it.lastProductOpened = null
        }
        p.teams.each {
            it.removeFromProducts(p)
        }
        p.removeAllAttachments()
        Template.findAllByParentProduct(p)*.delete()
        p.delete(flush: true)
        publishSynchronousEvent(IceScrumEventType.DELETE, p, dirtyProperties)
    }

    @PreAuthorize('scrumMaster(#product)')
    def archive(Product product) {
        product.preferences.archived = true
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, product)
        product.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, product, dirtyProperties)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    def unArchive(Product product) {
        product.preferences.archived = false
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, product)
        product.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, product, dirtyProperties)
    }

    void removeAllRoles(domain, User user) {
        if (domain instanceof Team) {
            teamService.removeMemberOrScrumMaster(domain, user)
        } else if (domain instanceof Product) {
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
        } else if (domain instanceof Product) {
            if (role == Authority.PRODUCTOWNER) {
                addProductOwner(domain, user)
            } else if (role == Authority.STAKEHOLDER) {
                addStakeHolder(domain, user)
            }
        }
    }

    void updateTeamMembers(Team team, List newMembers) {
        def oldMembersByProduct = [:]
        team.products.each { Product product ->
            oldMembersByProduct[product.id] = getAllMembersProductByRole(product)
        }
        def currentMembers = team.scrumMasters.collect { [id: it.id, role: Authority.SCRUMMASTER] }
        team.members.each { member ->
            if (!currentMembers.any { it.id == member.id }) {
                currentMembers << [id: member.id, role: Authority.MEMBER]
            }
        }
        updateMembers(team, currentMembers, newMembers)
        removeConflictingPOandSH(team)
        oldMembersByProduct.each { Long productId, Map oldMembers ->
            manageProductEvents(Product.get(productId), oldMembers)
        }
    }

    void updateProductMembers(Product product, List newMembers) {
        def oldMembers = getAllMembersProductByRole(product)
        def currentMembers = product.stakeHolders.collect { [id: it.id, role: Authority.STAKEHOLDER] } + product.productOwners.collect { [id: it.id, role: Authority.PRODUCTOWNER] }
        updateMembers(product, currentMembers, newMembers)
        manageProductEvents(product, oldMembers)
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

    private void removeConflictingPOandSH(team, product = null) {
        def membersIds = team.members*.id
        def tmIds = team.members*.id - team.scrumMasters*.id
        def products = product ? [product] : team.products
        products.each { Product p ->
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

    private void addProductOwner(Product product, User productOwner) {
        securityService.createProductOwnerPermissions productOwner, product
    }

    private void addStakeHolder(Product product, User stakeHolder) {
        if (product.preferences.hidden) {
            securityService.createStakeHolderPermissions stakeHolder, product
        }
    }

    private void removeProductOwner(Product product, User productOwner) {
        securityService.deleteProductOwnerPermissions productOwner, product
    }

    private void removeStakeHolder(Product product, User stakeHolder) {
        securityService.deleteStakeHolderPermissions stakeHolder, product
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

    void manageProductInvitations(Product product, invitedProductOwners, invitedStakeHolders) {
        invitedProductOwners = invitedProductOwners*.toLowerCase()
        invitedStakeHolders = invitedStakeHolders*.toLowerCase()
        def type = Invitation.InvitationType.PRODUCT
        def currentInvitations = Invitation.findAllByTypeAndProduct(type, product)
        def newInvitations = []
        assert !invitedProductOwners.intersect(invitedStakeHolders)
        newInvitations.addAll(invitedProductOwners.collect { [role: Authority.PRODUCTOWNER, email: it] })
        if (invitedStakeHolders && product.preferences.hidden) {
            newInvitations.addAll(invitedStakeHolders.collect { [role: Authority.STAKEHOLDER, email: it] })
        }
        manageInvitations(currentInvitations, newInvitations, type, product, null)
    }

    List<Product> getAllActiveProductsByUser(User user, String searchTerm = '') {
        def projects = Product.findAllByUserAndActive(user, [sort: "name", order: "asc", cache: true], searchTerm)
        def projectsOwnerOf = Team.findAllActiveProductsByTeamOwner(user.username, searchTerm, [sort: "name", order: "asc", cache: true]).findAll {
            !(it in projects)
        }
        projects.addAll(projectsOwnerOf)
        return projects
    }

    private void removeConflictingInvitedPOandSH(team, product = null) {
        def invitedMembersEmail = team.invitedMembers*.email
        def invitedMembersAndScrumMastersEmail = invitedMembersEmail + team.invitedScrumMasters*.email
        def products = product ? [product] : team.products
        products.each { Product p ->
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

    private void manageInvitations(List<Invitation> currentInvitations, List newInvitations, Invitation.InvitationType type, Product product, Team team) {
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
                    invitation.product = product
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
    void manageProductEvents(Product product, Map oldMembers) {
        Map newMembers = getAllMembersProductByRole(product)
        if (product.hasProperty('membersByRole')) {
            product.membersByRole = newMembers
        } else {
            product.metaClass.membersByRole = newMembers
        }
        publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, product, [membersByRole: oldMembers])
        publishSynchronousEvent(IceScrumEventType.UPDATE, product, [membersByRole: oldMembers])
    }

    Map getAllMembersProductByRole(Product product) {
        def usersByRole = [:]
        def productOwners = product.productOwners
        def team = product.firstTeam
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
        product.stakeHolders?.each { User stakeHolder ->
            usersByRole[stakeHolder] = Authority.STAKEHOLDER
        }
        return usersByRole
    }

    def export(StringWriter writer, Product product, String version) {
        def builder = new MarkupBuilder(writer)
        builder.mkp.xmlDeclaration(version: "1.0", encoding: "UTF-8")
        builder.export(version: version) {
            product.xml(builder)
        }
    }

    private void createDefaultBacklogs(Product product) {
        new Backlog(product: product, shared: true, filter: '{"story":{"state":1}}', name: 'is.ui.sandbox', code: 'sandbox').save()
        new Backlog(product: product, shared: true, filter: '{"story":{"state":[2,3]}}', name: 'is.ui.backlog', code: 'backlog').save()
        new Backlog(product: product, shared: true, filter: '{"story":{"state":7}}', name: 'todo.is.ui.backlog.done', code: 'done').save()
        new Backlog(product: product, shared: true, filter: '{"story":{}}', name: 'todo.is.ui.backlog.all', code: 'all').save()
    }
}
