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

package org.icescrum.core.services

import grails.plugins.springsecurity.Secured
import groovy.util.slurpersupport.NodeChild
import java.text.SimpleDateFormat
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.domain.preferences.ProductPreferences
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumProductEvent
import org.icescrum.core.support.ProgressSupport
import org.icescrum.core.support.XMLConverterSupport
import org.springframework.security.access.prepost.PostFilter
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport
import org.codehaus.groovy.grails.plugins.springsecurity.SpringSecurityUtils

/**
 * ProductService is a transactional class, that manage operations about
 * ProducBacklog, requested by web pages (Product.jspx & Productform.jspx)
 */
class ProductService {

    def springcacheService
    def springSecurityService
    def securityService
    def teamService
    def actorService
    def grailsApplication
    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    static transactional = true

    @PostFilter("stakeHolder(filterObject) or inProduct(filterObject)")
    List getByTermProductList(term, params) {
        return Product.findAllByNameIlike('%' + term + '%', params)
    }

    @PostFilter("(stakeHolder(filterObject,true) or inProduct(filterObject) or owner(filterObject)) and !hasRole('ROLE_ADMIN')")
    List getByMemberProductList() {
        return Product.list(cache: true)
    }

    @PreAuthorize('isAuthenticated()')
    void save(Product _product, productOwners, stakeHolders) {
        if (!_product.endDate == null)
            throw new IllegalStateException("is.product.error.no.endDate")
        if (_product.startDate > _product.endDate)
            throw new IllegalStateException('is.product.error.startDate')
        if (_product.startDate == _product.endDate)
            throw new IllegalStateException('is.product.error.duration')
        if (!_product.planningPokerGameType in [0, 1])
            throw new IllegalStateException("is.product.error.no.estimationSuite")

        _product.orderNumber = (Product.count() ?: 0) + 1

        if (!_product.save(flush: true))
            throw new RuntimeException()
        securityService.secureDomain(_product)

        if (productOwners){
            for(productOwner in User.getAll(productOwners*.toLong())){
                if (productOwner)
                    addRole(_product, null, productOwner, Authority.PRODUCTOWNER)
            }
        }
        if (stakeHolders){
            for(stakeHolder in User.getAll(stakeHolders*.toLong())){
                if (stakeHolder)
                    addRole(_product, null, stakeHolder, Authority.STAKEHOLDER)
            }
        }
        publishEvent(new IceScrumProductEvent(_product, this.class, (User)springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
    }

    @PreAuthorize('isAuthenticated()')
    void saveImport(Product _product, String name) {
        if (!_product.endDate == null)
            throw new IllegalStateException("is.product.error.no.endDate")
        if (_product.startDate > _product.endDate)
            throw new IllegalStateException('is.product.error.startDate')
        if (_product.startDate == _product.endDate)
            throw new IllegalStateException('is.product.error.duration')
        if (!_product.planningPokerGameType in [0, 1])
            throw new IllegalStateException("is.product.error.no.estimationSuite")
        _product.orderNumber = (Product.count() ?: 0) + 1

        if (_product.erasableByUser && _product.name == name) {
            def p = Product.findByName(_product.name)
            securityService.unsecureDomain(p)
            p.delete(flush: true)
        }

        try {
            _product.teams.each { t ->
                if (t.id == null)
                    teamService.saveImport(t)
            }

            def productOwners = _product.productOwners

            productOwners?.each {
                if (it.id == null)
                    it.save()
            }

            if (!_product.save()) {
                throw new RuntimeException()
            }
            securityService.secureDomain(_product)

            _product.teams.each{
               it.scrumMasters?.each{ u ->
                   securityService.createAdministrationPermissionsForProduct(u,_product)
               }
            }

            if (productOwners) {
                productOwners?.eachWithIndex {it, index ->
                    securityService.createProductOwnerPermissions(it, _product)
                }
                securityService.changeOwner(productOwners.first(), _product)
            } else {
                def u = User.get(springSecurityService.principal.id)
                securityService.createProductOwnerPermissions(u, _product)
                securityService.changeOwner(u, _product)
            }

            publishEvent(new IceScrumProductEvent(_product, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
        } catch (Exception e) {
            throw new RuntimeException(e)
        }
    }

    @PreAuthorize('owner(#_product) and !archivedProduct(#_product)')
    void addTeamsToProduct(Product _product, teamIds) {
        if (!_product)
            throw new IllegalStateException('Product must not be null')

        if (!teamIds)
            throw new IllegalStateException('Product must have at least one team')


        log.debug teamIds
        for (team in Team.getAll(teamIds*.toLong())) {
            if (team){
                _product.addToTeams(team)
                team.scrumMasters?.each{
                    securityService.createAdministrationPermissionsForProduct(it, _product)
                }
                team.members?.each{
                    broadcastToSingleUser(user:it.username, function:'addRoleProduct', message:[class:'User',product:_product])
                }
            }
            publishEvent(new IceScrumProductEvent(_product, team, this.class, (User) springSecurityService.currentUser, IceScrumProductEvent.EVENT_TEAM_ADDED))
        }

        if (!_product.save())
            throw new IllegalStateException('Product not saved')

        springcacheService.flush(SecurityService.CACHE_OPENPRODUCTTEAM)
        springcacheService.flush(SecurityService.CACHE_PRODUCTTEAM)

    }

    @PreAuthorize('(scrumMaster() or owner(#_product)) and !archivedProduct(#_product)')
    void update(Product _product, boolean hasHiddenChanged) {
        if (!_product.name?.trim()) {
            throw new IllegalStateException("is.product.error.no.name")
        }
        if (!_product.planningPokerGameType in [0, 1]) {
            throw new IllegalStateException("is.product.error.no.estimationSuite")
        }

        if (hasHiddenChanged && _product.preferences.hidden && !ApplicationSupport.booleanValue(grailsApplication.config.icescrum.project.private.enable)
              && !SpringSecurityUtils.ifAnyGranted(Authority.ROLE_ADMIN)) {
            _product.preferences.hidden = false
        }

        if (hasHiddenChanged && !_product.preferences.hidden) {
            _product.stakeHolders?.each {
                removeStakeHolder(_product,it)
            }
        }

        if(hasHiddenChanged)
            flushCache(cache:'project_'+_product.id+'_'+SecurityService.CACHE_STAKEHOLDER)

        if (!_product.save(flush: true)) {
            throw new RuntimeException()
        }

        broadcast(function: 'update', message: _product)

        publishEvent(new IceScrumProductEvent(_product, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
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

                    def a = xmlRoot."${Cliche.PRODUCT_BACKLOG_POINTS}".toInteger()
                    def b = xmlRoot."${Cliche.PRODUCT_REMAINING_POINTS}".toInteger()
                    def c = a - b

                    values << [
                            all: xmlRoot."${Cliche.PRODUCT_BACKLOG_POINTS}".toInteger(),
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
                    values << [
                            label: xmlRoot."${Cliche.SPRINT_ID}".toString(),
                            userstories: xmlRoot."${Cliche.FUNCTIONAL_STORY_PRODUCT_REMAINING_POINTS}".toInteger(),
                            technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_PRODUCT_REMAINING_POINTS}".toInteger(),
                            defectstories: xmlRoot."${Cliche.DEFECT_STORY_PRODUCT_REMAINING_POINTS}".toInteger()
                    ]
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
                    values << [
                            userstories: xmlRoot."${Cliche.FUNCTIONAL_STORY_VELOCITY}".toInteger(),
                            defectstories: xmlRoot."${Cliche.DEFECT_STORY_VELOCITY}".toInteger(),
                            technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_VELOCITY}".toInteger(),
                            label: xmlRoot."${Cliche.SPRINT_ID}".toString()
                    ]
                }
            }
        }
        return values
    }

    @PreAuthorize('stakeHolder() or inProduct()')
    def productVelocityCapacityValues(Product product) {
        def values = []
        def capacity = 0, label = ""
        product.releases?.sort {a, b -> a.orderNumber <=> b.orderNumber}?.each {
            Cliche.findAllByParentTimeBox(it, [sort: "datePrise", order: "asc"])?.each { cliche ->
                def xmlRoot = new XmlSlurper().parseText(cliche.data)
                if (xmlRoot) {
                    if (cliche.type == Cliche.TYPE_ACTIVATION) {
                        capacity = xmlRoot."${Cliche.SPRINT_CAPACITY}".toInteger()
                        label = xmlRoot."${Cliche.SPRINT_ID}".toString()
                    }
                    if (cliche.type == Cliche.TYPE_CLOSE) {
                        values << [
                                capacity: capacity,
                                velocity: xmlRoot."${Cliche.SPRINT_VELOCITY}".toInteger(),
                                label: label
                        ]

                    }
                }
            }
        }
        return values
    }

    @PreAuthorize('isAuthenticated()')
    @Transactional(readOnly = true)
    Product unMarshall(NodeChild product, ProgressSupport progress = null) {
        try {
            def p = new Product(
                    name: product."${'name'}".text(),
                    pkey: product.pkey.text(),
                    description: product.description.text(),
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
                User u = (User) p?.getAllUsers()?.find {it.idFromImport == productOwner.@id.text().toInteger()} ?: null
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

            def stories = p.stories.findAll {it.state == Story.STATE_ACCEPTED || it.state == Story.STATE_ESTIMATED}.sort({ a, b -> a.rank <=> b.rank } as Comparator)
            stories.eachWithIndex {it, index ->
                it.rank = index + 1
            }

            def releaseService = (ReleaseService) ApplicationHolder.application.mainContext.getBean('releaseService');
            product.releases.release.eachWithIndex { it, index ->
                releaseService.unMarshall(it, p, progress)
                progress?.updateProgress((product.releases.release.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.release')]))
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
    def parseXML(File x, ProgressSupport progress = null) {
        def prod = new XmlSlurper().parse(x)

        progress?.updateProgress(0, g.message(code: 'is.parse', args: [g.message(code: 'is.product')]))

        XMLConverterSupport converter = new XMLConverterSupport(prod)
        if (converter.needConversion) {
            prod = converter.convert()
        }

        progress?.updateProgress(5, g.message(code: 'is.parse', args: [g.message(code: 'is.product')]))
        def Product p
        try {
            p = this.unMarshall((NodeChild) prod, progress)
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
        springcacheService.flush(~/project_${id}\w+/)
        securityService.unsecureDomain p
        p.teams.each{ it.removeFromProducts(p) }
        p.delete(flush:true)
        broadcast(function: 'delete', message: [class: p.class, id: id])
    }

    @PreAuthorize('owner(#p) or scrumMaster()')
    def archive(Product p) {
        p.preferences.archived = true
        if (!p.save(flush:true)){
            throw new RuntimeException()
        }
        springcacheService.flush(~/project_${p.id}\w+/)
        def cacheResolver = grailsApplication.mainContext.getBean('applicationCacheResolver')
        springcacheService.flush(cacheResolver.resolveCacheName(SecurityService.CACHE_ARCHIVEDPRODUCT))
        broadcast(function: 'archive', message: p)
    }

    @PreAuthorize("hasRole('ROLE_ADMIN')")
    def unArchive(Product p) {
        p.preferences.archived = false
        if (!p.save(flush:true)){
            throw new RuntimeException()
        }
        springcacheService.flush(~/project_${p.id}\w+/)
        def cacheResolver = grailsApplication.mainContext.getBean('applicationCacheResolver')
        springcacheService.flush(cacheResolver.resolveCacheName(SecurityService.CACHE_ARCHIVEDPRODUCT))
        broadcast(function: 'unarchive', message: p)
    }

    @PreAuthorize('owner(#product) or scrumMaster()')
    void removeAllRoles(Product product, Team team, User user, boolean broadcast = true){
        teamService.removeMemberOrScrumMaster(team,user)
        removeProductOwner(product,user)
        removeStakeHolder(product,user)
        if (broadcast)
            broadcastToSingleUser(user:user.username, function:'removeRoleProduct', message:[class:'User',product:product])
    }

    @Secured(['owner(#product) or scrumMaster()', 'RUN_AS_PERMISSIONS_MANAGER'])
    void addRole(Product product, Team team, User user, int role, boolean broadcast = true){
        switch (role){
            case Authority.SCRUMMASTER:
                teamService.addScrumMaster(team,user)
                break
            case Authority.MEMBER:
                teamService.addMember(team,user)
                break
            case Authority.PRODUCTOWNER:
                addProductOwner(product,user)
                break
            case Authority.STAKEHOLDER:
                addStakeHolder(product,user)
                break
            case Authority.PO_AND_SM:
                teamService.addScrumMaster(team,user)
                addProductOwner(product,user)
                break
        }
        if(broadcast)
            broadcastToSingleUser(user:user.username, function:'addRoleProduct', message:[class:'User',product:product])
    }

    @Secured(['owner(#product) or scrumMaster()', 'RUN_AS_PERMISSIONS_MANAGER'])
    void changeRole(Product product, Team team, User user, int role, boolean broadcast = true){
        removeAllRoles(product,team,user,false)
        addRole(product,team,user,role,false)
        if(broadcast)
            broadcastToSingleUser(user:user.username, function:'updateRoleProduct', message:[class:'User',product:product])
    }

    private void addProductOwner(Product product, User productOwner) {
        securityService.createProductOwnerPermissions productOwner, product
    }

    private void addStakeHolder(Product product, User stakeHolder) {
        if (product.preferences.hidden)
            securityService.createStakeHolderPermissions stakeHolder, product
    }

    private void removeProductOwner(Product product, User productOwner) {
        securityService.deleteProductOwnerPermissions productOwner, product
    }

    private void removeStakeHolder(Product product, User stakeHolder) {
        securityService.deleteStakeHolderPermissions stakeHolder, product
    }
}