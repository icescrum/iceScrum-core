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

package org.icescrum.core.services

import org.icescrum.core.utils.ServicesUtils
import java.text.SimpleDateFormat
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumProductEvent
import org.icescrum.core.support.ProgressSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils
import org.icescrum.core.domain.preferences.UserPreferences

import org.icescrum.core.event.IceScrumUserEvent

class ProductService {

    def springSecurityService
    def securityService
    def teamService
    def actorService
    def grailsApplication

    static transactional = true

    @PreAuthorize('isAuthenticated()')
    void save(Product product, productOwners, stakeHolders) {
        if (!product.endDate == null)
            throw new IllegalStateException("is.product.error.no.endDate")
        if (product.startDate > product.endDate)
            throw new IllegalStateException('is.product.error.startDate')
        if (product.startDate == product.endDate)
            throw new IllegalStateException('is.product.error.duration')
        if (!product.planningPokerGameType in [0, 1])
            throw new IllegalStateException("is.product.error.no.estimationSuite")

        product.orderNumber = (Product.count() ?: 0) + 1

        if (!product.save(flush: true))
            throw new RuntimeException()
        securityService.secureDomain(product)

        if (productOwners){
            for(productOwner in User.getAll(productOwners*.toLong())){
                if (productOwner)
                    addRole(product, null, productOwner, Authority.PRODUCTOWNER)
            }
        }
        if (stakeHolders && product.preferences.hidden){
            for(stakeHolder in User.getAll(stakeHolders*.toLong())){
                if (stakeHolder)
                    addRole(product, null, stakeHolder, Authority.STAKEHOLDER)
            }
        }
        publishEvent(new IceScrumProductEvent(product, this.class, (User)springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
    }

    @PreAuthorize('isAuthenticated()')
    void saveImport(Product product, String name, String importPath) {
        if (!product.endDate == null)
            throw new IllegalStateException("is.product.error.no.endDate")
        if (product.startDate > product.endDate)
            throw new IllegalStateException('is.product.error.startDate')
        if (product.startDate == product.endDate)
            throw new IllegalStateException('is.product.error.duration')
        if (!product.planningPokerGameType in [0, 1])
            throw new IllegalStateException("is.product.error.no.estimationSuite")
        product.orderNumber = (Product.count() ?: 0) + 1

        if (product.erasableByUser && product.name == name) {
            def p = Product.findByName(product.name)
            p.teams.each{ it.removeFromProducts(p) }
            securityService.unsecureDomain(p)
            p.delete(flush: true)
        }

        try {
            product.teams.each { t ->
                if (t.id == null)
                    teamService.saveImport(t)
                else {
                    def ts = Team.get(t.id)
                    ts.removeFromProducts(product)
                    ts.addToProducts(product)
                }
            }

            def productOwners = product.productOwners

            productOwners?.each {
                if (it.id == null)
                    it.save()
            }

            if (!product.save(flush: true)) {
                throw new RuntimeException()
            }
            securityService.secureDomain(product)

            product.teams.each{
               it.scrumMasters?.each{ u ->
                   u = User.get(u.id)
                   securityService.createAdministrationPermissionsForProduct(u,product)
               }
            }

            if (productOwners) {
                productOwners?.eachWithIndex {it, index ->
                    it = User.get(it.id)
                    securityService.createProductOwnerPermissions(it, product)
                }
                def u = productOwners.first()
                u = User.get(u.id)
                securityService.changeOwner(u, product)
            } else {
                def u = User.get(springSecurityService.principal.id)
                securityService.createProductOwnerPermissions(u, product)
                securityService.changeOwner(u, product)
            }

            publishEvent(new IceScrumProductEvent(product, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
            if (importPath){
                def event = new IceScrumProductEvent(product, new File(importPath), this.class, (User) springSecurityService.currentUser, IceScrumProductEvent.EVENT_IMPORTED)
                publishEvent(event)
            }
        } catch (Exception e) {
            throw new RuntimeException(e)
        }
    }

    @PreAuthorize('owner(#product) and !archivedProduct(#product)')
    void addTeamsToProduct(Product product, teamIds) {
        if (!product)
            throw new IllegalStateException('Product must not be null')

        if (!teamIds)
            throw new IllegalStateException('Product must have at least one team')

        for (team in Team.getAll(teamIds*.toLong())) {
            if (team){
                product.addToTeams(team)
                team.scrumMasters?.each{
                    securityService.createAdministrationPermissionsForProduct(it, product)
                }
                team.members?.each{
                    broadcastToSingleUser(user:it.username, function:'addRoleProduct', message:[class:'User',product:product])
                }
            }
            publishEvent(new IceScrumProductEvent(product, team, this.class, (User) springSecurityService.currentUser, IceScrumProductEvent.EVENT_TEAM_ADDED))
        }

        if (!product.save())
            throw new IllegalStateException('Product not saved')
    }

    @PreAuthorize('(scrumMaster(#product) or owner(#product)) and !archivedProduct(#product)')
    void update(Product product, boolean hasHiddenChanged, String pkeyChanged) {
        if (!product.name?.trim()) {
            throw new IllegalStateException("is.product.error.no.name")
        }
        if (!product.planningPokerGameType in [0, 1]) {
            throw new IllegalStateException("is.product.error.no.estimationSuite")
        }

        if (hasHiddenChanged && product.preferences.hidden && !ApplicationSupport.booleanValue(grailsApplication.config.icescrum.project.private.enable)
              && !SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            product.preferences.hidden = false
        }

        if (hasHiddenChanged && !product.preferences.hidden) {
            product.stakeHolders?.each {
                removeStakeHolder(product,it)
            }
        }

        if (pkeyChanged){
            UserPreferences.findAllByLastProductOpened(pkeyChanged)?.each {
                it.lastProductOpened = product.pkey
                it.save()
            }
        }

        product.lastUpdated = new Date()
        if (!product.save(flush: true)) {
            throw new RuntimeException()
        }

        broadcast(function: 'update', message: product, channel:'product-'+product.id)
        publishEvent(new IceScrumProductEvent(product, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
    }

    Release getLastRelease(Product p) {
        return p.releases?.max {s1, s2 -> s1.orderNumber <=> s2.orderNumber}
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def cumulativeFlowValues(Product product) {
        def values = []
        product.releases?.sort {a, b -> a.orderNumber <=> b.orderNumber}?.each {
            Cliche.findAllByParentTimeBoxAndType(it, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])?.each { cliche ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    values << [
                            suggested: xmlRoot."${Cliche.SUGGESTED_STORIES}".toInteger(),
                            accepted: xmlRoot."${Cliche.ACCEPTED_STORIES}".toInteger(),
                            estimated: xmlRoot."${Cliche.ESTIMATED_STORIES}".toInteger(),
                            planned: xmlRoot."${Cliche.PLANNED_STORIES}".toInteger(),
                            inprogress: xmlRoot."${Cliche.INPROGRESS_STORIES}".toInteger(),
                            done: xmlRoot."${Cliche.FINISHED_STORIES}".toInteger(),
                            label: xmlRoot."${Cliche.SPRINT_ID}".toString()
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productBurnupValues(Product product) {
        def values = []
        product.releases?.sort {a, b -> a.orderNumber <=> b.orderNumber}?.each {
            Cliche.findAllByParentTimeBoxAndType(it, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])?.each { cliche ->

                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {

                    def a = xmlRoot."${Cliche.PRODUCT_BACKLOG_POINTS}".toBigDecimal()
                    def b = xmlRoot."${Cliche.PRODUCT_REMAINING_POINTS}".toBigDecimal()
                    def c = a - b

                    values << [
                            all: xmlRoot."${Cliche.PRODUCT_BACKLOG_POINTS}".toBigDecimal(),
                            done: c,
                            label: xmlRoot."${Cliche.SPRINT_ID}".toString()
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productBurndownValues(Product product) {
        def values = []
        product.releases?.sort {a, b -> a.orderNumber <=> b.orderNumber}?.each {
            Cliche.findAllByParentTimeBoxAndType(it, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])?.each { cliche ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    def sprintEntry = [
                            label: xmlRoot."${Cliche.SPRINT_ID}".toString(),
                            userstories: xmlRoot."${Cliche.FUNCTIONAL_STORY_PRODUCT_REMAINING_POINTS}".toBigDecimal(),
                            technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_PRODUCT_REMAINING_POINTS}".toBigDecimal(),
                            defectstories: xmlRoot."${Cliche.DEFECT_STORY_PRODUCT_REMAINING_POINTS}".toBigDecimal()
                    ]
                    sprintEntry << computeLabelsForSprintEntry(sprintEntry)
                    values << sprintEntry
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder(#product) or inProduct(#product)')
    def productVelocityValues(Product product) {
        def values = []
        product.releases?.sort {a, b -> a.orderNumber <=> b.orderNumber}?.each {
            Cliche.findAllByParentTimeBoxAndType(it, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"])?.each { cliche ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    def sprintEntry = [
                            userstories: xmlRoot."${Cliche.FUNCTIONAL_STORY_VELOCITY}".toBigDecimal(),
                            defectstories: xmlRoot."${Cliche.DEFECT_STORY_VELOCITY}".toBigDecimal(),
                            technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_VELOCITY}".toBigDecimal(),
                            label: xmlRoot."${Cliche.SPRINT_ID}".toString()
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
        product.releases?.sort {a, b -> a.orderNumber <=> b.orderNumber}?.each {
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
                                label: label
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
    @Transactional(readOnly = true)
    Product unMarshall(def product, ProgressSupport progress = null) {
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        try {
            def p = new Product(
                    name: product."${'name'}".text(),
                    pkey: product.pkey.text(),
                    description: product.description.text(),
                    dateCreated: product.dateCreated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(product.dateCreated.text()) : new Date(),
                    lastUpdated: product.lastUpdated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(product.lastUpdated.text()) : new Date(),
                    startDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(product.startDate.text()),
                    endDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(product.endDate.text())
            )
            p.preferences = new ProductPreferences(
                    hidden: product.preferences.hidden.text().toBoolean(),
                    assignOnBeginTask: product.preferences.assignOnBeginTask.text().toBoolean(),
                    assignOnCreateTask: product.preferences.assignOnCreateTask.text().toBoolean(),
                    autoCreateTaskOnEmptyStory: product.preferences.autoCreateTaskOnEmptyStory.text().toBoolean(),
                    autoDoneStory: product.preferences.autoDoneStory.text().toBoolean(),
                    url: product.preferences.url?.text() ?: null,
                    noEstimation: product.preferences.noEstimation.text().toBoolean(),
                    limitUrgentTasks: product.preferences.limitUrgentTasks.text().toInteger(),
                    estimatedSprintsDuration: product.preferences.estimatedSprintsDuration.text().toInteger(),
                    displayUrgentTasks: product.preferences.displayUrgentTasks.text().toBoolean(),
                    displayRecurrentTasks: product.preferences.displayRecurrentTasks.text().toBoolean(),
                    hideWeekend: product.preferences.hideWeekend.text()?.toBoolean() ?: false,
                    releasePlanningHour: product.preferences.releasePlanningHour.text() ?: "9:00",
                    sprintPlanningHour: product.preferences.sprintPlanningHour.text() ?: "9:00",
                    dailyMeetingHour: product.preferences.dailyMeetingHour.text() ?: "11:00",
                    sprintReviewHour: product.preferences.sprintReviewHour.text() ?: "14:00",
                    sprintRetrospectiveHour: product.preferences.sprintRetrospectiveHour.text() ?: "16:00",
                    timezone: product.preferences?.timezone?.text() ?: grailsApplication.config.icescrum.timezone.default
            )

            Product pExist = (Product) Product.findByPkey(p.pkey)
            if (pExist && securityService.productOwner(pExist, springSecurityService.authentication)) {
                p.erasableByUser = true
            }

            product.teams.team.eachWithIndex { it, index ->
                def t = teamService.unMarshall(it, p, progress)
                p.addToTeams(t)
                progress?.updateProgress((product.teams.team.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.team')]))
            }

            def productOwnersList = []
            product.productOwners.user.eachWithIndex {productOwner, index ->
                def u
                if (!productOwner.@uid?.isEmpty())
                    u = ((User) p.getAllUsers().find { it.uid == productOwner.@uid.text() } ) ?: null
                else{
                    u = ApplicationSupport.findUserUIDOldXMl(productOwner,null,p.getAllUsers())
                }
                if (!u) {
                    u = User.findByUsernameAndEmail(productOwner.username.text(), productOwner.email.text())
                    if (!u) {
                        def userService = (UserService) ApplicationHolder.application.mainContext.getBean('userService');
                        u = userService.unMarshall(productOwner)
                    }
                }
                productOwnersList << u
            }
            p.productOwners = productOwnersList

            def featureService = (FeatureService) ApplicationHolder.application.mainContext.getBean('featureService');
            product.features.feature.eachWithIndex { it, index ->
                def f = featureService.unMarshall(it)
                p.addToFeatures(f)
                progress?.updateProgress((product.features.feature.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.feature')]))
            }

            product.actors.actor.eachWithIndex { it, index ->
                def a = actorService.unMarshall(it)
                p.addToActors(a)
                progress?.updateProgress((product.actors.actor.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.actor')]))
            }

            def storyService = (StoryService) ApplicationHolder.application.mainContext.getBean('storyService');
            product.stories.story.eachWithIndex { it, index ->
                storyService.unMarshall(it, p)
                progress?.updateProgress((product.stories.story.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.story')]))
            }
            // ensure rank for stories in backlog
            def stories = p.stories.findAll {it.state == Story.STATE_ACCEPTED || it.state == Story.STATE_ESTIMATED}.sort({ a, b -> a.rank <=> b.rank } as Comparator)
            stories.eachWithIndex {it, index ->
                it.rank = index + 1
            }

            def releaseService = (ReleaseService) ApplicationHolder.application.mainContext.getBean('releaseService');
            product.releases.release.eachWithIndex { it, index ->
                releaseService.unMarshall(it, p, progress)
                progress?.updateProgress((product.releases.release.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.release')]))
            }
            // ensure rank for stories in each sprint
            p.releases.each{Release release ->
                release.sprints.each{Sprint sprint ->
                    // first ranks for planned and in progress stories
                    stories = sprint.stories.findAll { Story story -> story.state == Story.STATE_PLANNED || story.state == Story.STATE_INPROGRESS}.sort({ a, b -> a.rank <=> b.rank } as Comparator)
                    stories.eachWithIndex {Story story, index ->
                        story.rank = index + 1
                    }
                    // lask ranks for done stories
                    def maxRank = stories.size()
                    stories = sprint.stories.findAll { Story story -> story.state == Story.STATE_DONE}.sort({ a, b -> a.rank <=> b.rank } as Comparator)
                    stories.each {Story story ->
                        story.rank = ++maxRank
                    }
                }
            }

            return p
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            progress?.progressError(g.message(code: 'is.parse.error', args: [g.message(code: 'is.product')]))
            throw new RuntimeException(e)
        }
    }

    @PreAuthorize('isAuthenticated()')
    @Transactional(readOnly = true)
    def parseXML(File file, ProgressSupport progress = null) {
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        String xmlText = file.getText();
        String cleanedXmlText = ServicesUtils.cleanXml(xmlText)
        def prod = new XmlSlurper().parseText(cleanedXmlText)

        progress?.updateProgress(0, g.message(code: 'is.parse', args: [g.message(code: 'is.product')]))
        def Product p
        try {
            def product = prod

            //be compatible with xml without export tag
            if (prod.find{it.name == 'export'}){ product = prod.product }

            p = this.unMarshall(product, progress)
        } catch (RuntimeException e) {
            if (log.debugEnabled) e.printStackTrace()
            progress?.progressError(g.message(code: 'is.parse.error', args: [g.message(code: 'is.product')]))
            return
        }
        progress.completeProgress(g.message(code: 'is.validate.complete'))
        return p
    }

    @PreAuthorize('isAuthenticated()')
    @Transactional(readOnly = true)
    def validate(Product p, ProgressSupport progress = null) {
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        try {
            Product.withNewSession {
                p.teams.eachWithIndex { team, index ->
                    team.validate()
                    progress?.updateProgress((p.teams.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.validate', args: [g.message(code: 'is.team')]))
                    team.members.eachWithIndex { member, index2 ->
                        member.validate()
                        progress?.updateProgress((team.members.size() * (index2 + 1) / 100).toInteger(), g.message(code: 'is.validate', args: [g.message(code: 'is.user')]))
                    }
                }
                p.productOwners?.eachWithIndex{ productOwner, index ->
                    productOwner.validate()
                    progress?.updateProgress((p.productOwners.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.validate', args: [g.message(code: 'is.user')]))
                }
                p.validate()
                progress?.updateProgress(100, g.message(code: 'is.validate', args: [g.message(code: 'is.product')]))
            }
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            progress?.progressError(g.message(code: 'is.validate.error', args: [g.message(code: 'is.product')]))
        }
    }

    @PreAuthorize('owner(#p)')
    def delete(Product p) {
        def id = p.id
        p.allUsers.each{ it.preferences.removeEmailsSettings(p.pkey) } // must be before unsecure to have POs
        securityService.unsecureDomain p
        UserPreferences.findAllByLastProductOpened(p.pkey)?.each {
            it.lastProductOpened = null
        }
        p.teams.each{
            it.removeFromProducts(p)
        }
        p.removeAllAttachments()
        p.delete(flush:true)
        broadcast(function: 'delete', message: [class: p.class, id: id], channel:'product-'+id)
    }

    @PreAuthorize('owner(#p) or scrumMaster(#p)')
    def archive(Product p) {
        p.preferences.archived = true
        p.lastUpdated = new Date()
        if (!p.save(flush:true)){
            throw new RuntimeException()
        }
        broadcast(function: 'archive', message: p, channel:'product-'+p.id)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    def unArchive(Product p) {
        p.preferences.archived = false
        p.lastUpdated = new Date()
        if (!p.save(flush:true)){
            throw new RuntimeException()
        }
        broadcast(function: 'unarchive', message: p, channel:'product-'+p.id)
    }

    void removeAllRoles(Product product, Team team, User user, boolean broadcast = true, boolean raiseEvent = true) {
        if (team){
            teamService.removeMemberOrScrumMaster(team,user)
        }
        if (product){
            removeProductOwner(product,user)
            removeStakeHolder(product,user)
        }else{
            team.products*.each{
                removeProductOwner(it,user)
                removeStakeHolder(it,user)
            }
        }
        // Remove email settings only if it's not a role update (remove -> add)
        if (broadcast || raiseEvent) {
            if (product) {
                user.preferences.removeEmailsSettings(product.pkey)
            } else {
                team?.products?.each {
                    user.preferences.removeEmailsSettings(it.pkey)
                }
            }
        }
        if (broadcast){
            if (product){
                broadcastToSingleUser(user:user.username, function:'removeRoleProduct', message:[class:'User',product:product])
            }else{
                team.products?.each{
                    broadcastToSingleUser(user:user.username, function:'removeRoleProduct', message:[class:'User',product:it])
                }
            }
        }
        if(raiseEvent) {
            if (product){
                publishEvent(new IceScrumUserEvent(user, product, this.class, (User) springSecurityService.currentUser, IceScrumUserEvent.EVENT_REMOVED_FROM_PRODUCT))
            } else {
                team?.products?.each{
                    publishEvent(new IceScrumUserEvent(user, it, this.class, (User) springSecurityService.currentUser, IceScrumUserEvent.EVENT_REMOVED_FROM_PRODUCT))
                }
            }
        }
    }

    void addRole(Product product, Team team, User user, int role, boolean broadcast = true, boolean raiseEvent = true) {
        switch (role){
            case Authority.SCRUMMASTER:
                teamService.addScrumMaster(team,user)
                break
            case Authority.MEMBER:
                teamService.addMember(team,user)
                break
            case Authority.PRODUCTOWNER:
                if (product)
                    addProductOwner(product,user)
                else{
                    team.products?.each{
                        addProductOwner(it,user)
                    }
                }
                break
            case Authority.STAKEHOLDER:
                if (product) {
                    addStakeHolder(product, user)
                } else {
                    team?.products?.each {
                        addStakeHolder(it, user)
                    }
                }
                break
            case Authority.PO_AND_SM:
                teamService.addScrumMaster(team,user)
                if (product)
                    addProductOwner(product,user)
                else{
                    team.products?.each{
                        addProductOwner(it,user)
                    }
                }
                break
        }
        if(broadcast){
            if (product){
                broadcastToSingleUser(user:user.username, function:'addRoleProduct', message:[class:'User',product:product])
            }else{
                team?.products?.each{
                    broadcastToSingleUser(user:user.username, function:'addRoleProduct', message:[class:'User',product:it])
                }
            }
        }
        if(raiseEvent) {
            if (product){
                publishEvent(new IceScrumUserEvent(user, product, role, this.class, (User) springSecurityService.currentUser, IceScrumUserEvent.EVENT_ADDED_TO_PRODUCT))
            } else {
                team?.products?.each{
                    publishEvent(new IceScrumUserEvent(user, it, role, this.class, (User) springSecurityService.currentUser, IceScrumUserEvent.EVENT_ADDED_TO_PRODUCT))
                }
            }
        }
    }

    void changeRole(Product product, Team team, User user, int role, boolean broadcast = true){
        removeAllRoles(product, team, user, false, false)
        addRole(product, team, user, role, false, false)
        publishEvent(new IceScrumUserEvent(user, product, role, this.class, (User) springSecurityService.currentUser, IceScrumUserEvent.EVENT_CHANGED_ROLE_IN_PRODUCT))
        if(broadcast){
            if (product){
                broadcastToSingleUser(user:user.username, function:'updateRoleProduct', message:[class:'User',product:product])
            }else{
                team?.products?.each{
                    broadcastToSingleUser(user:user.username, function:'updateRoleProduct', message:[class:'User',product:it])
                }
            }
        }
    }

    List getAllMembersProduct(def product) {
        def team = product.firstTeam
        def productOwners = product.productOwners
        def members = []
        def is = grailsApplication.mainContext.getBean('org.icescrum.core.taglib.ScrumTagLib')

        if (team) {
            def scrumMasters = team.scrumMasters
            team.members?.each {
                def role = Authority.MEMBER
                if (scrumMasters*.id?.contains(it.id) && productOwners*.id?.contains(it.id)) {
                    role = Authority.PO_AND_SM
                } else if (scrumMasters*.id?.contains(it.id)) {
                    role = Authority.SCRUMMASTER
                } else if (productOwners*.id?.contains(it.id)) {
                    role = Authority.PRODUCTOWNER
                }
                members.add([name: it.firstName + ' ' + it.lastName,
                        activity: it.preferences.activity ?: '&nbsp;',
                        id: it.id,
                        avatar: is.avatar(user: it, link: true),
                        role: role])
            }
        }

        productOwners?.each{
            if(!members*.id?.contains(it.id)){
                members.add([name: it.firstName+' '+it.lastName,
                activity:it.preferences.activity?:'&nbsp;',
                         id: it.id,
                         avatar:is.avatar(user:it,link:true),
                         role: Authority.PRODUCTOWNER])
            }
        }

        product.stakeHolders?.each{
            members.add([name: it.firstName+' '+it.lastName,
                         activity:it.preferences.activity?:'&nbsp;',
                         id: it.id,
                         avatar:is.avatar(user:it,link:true),
                         role: Authority.STAKEHOLDER])
        }
        members.sort{ a,b -> b.role <=> a.role ?: a.name <=> b.name }
    }

    private void addProductOwner(Product product, User productOwner) {
        securityService.createProductOwnerPermissions productOwner, product
    }

    private void addStakeHolder(Product product, User stakeHolder) {
        if (product.preferences.hidden){
            securityService.createStakeHolderPermissions stakeHolder, product
        }
    }

    private void removeProductOwner(Product product, User productOwner) {
        securityService.deleteProductOwnerPermissions productOwner, product
    }

    private void removeStakeHolder(Product product, User stakeHolder) {
        securityService.deleteStakeHolderPermissions stakeHolder, product
    }
}