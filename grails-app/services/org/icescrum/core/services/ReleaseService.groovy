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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import java.text.SimpleDateFormat
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumReleaseEvent
import org.icescrum.core.support.ProgressSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*

class ReleaseService {

    final static long DAY = 1000 * 60 * 60 * 24

    static transactional = true

    def productService
    def storyService
    def clicheService
    def springSecurityService
    def grailsApplication

    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('(productOwner(#product) or scrumMaster(#product)) and !archivedProduct(#product)')
    void save(Release release, Product product) {
        release.parentProduct = product
        release.state = Release.STATE_WAIT
        // If this is the first release of the product, it is automatically activated
        if (product.releases?.size() <= 0 || product.releases == null) {
            release.state = Release.STATE_INPROGRESS
        }
        release.orderNumber = (product.releases?.size() ?: 0) + 1
        if (!release.save(flush: true))
            throw new RuntimeException()
        product.addToReleases(release)
        product.endDate = release.endDate

        broadcast(function: 'add', message: release, channel:'product-'+product.id)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    @Transactional
    void update(Release release, Date startDate = null, Date endDate = null) {

        if (release.state == Release.STATE_DONE){
            throw new IllegalStateException('is.release.error.update.state.done')
        }

        startDate = startDate ?: release.startDate
        endDate = endDate ?: release.endDate

        def nextRelease = release.parentProduct.releases.findAll { it.orderNumber > release.orderNumber } ?.min { it.orderNumber }
        if (nextRelease && nextRelease.startDate <= endDate) {
            def nextStartDate = endDate + 1
            if (nextStartDate >= nextRelease.endDate) {
                throw new IllegalStateException('is.release.error.endDate.after.next.release')
            }
            update(nextRelease, nextStartDate)  // updating the next release will update the next ones
        }

        if (!release.sprints.isEmpty()) {
            def sprintService = (SprintService) ApplicationHolder.application.mainContext.getBean('sprintService');
            def firstSprint = release.sprints.min { it.startDate }
            if (firstSprint.startDate.before(startDate)) {
                if (firstSprint.state >= Sprint.STATE_INPROGRESS) {
                    throw new IllegalStateException('is.release.error.startDate.after.inprogress.sprint')
                }
                sprintService.update(firstSprint, startDate, (startDate + firstSprint.duration - 1), false, false)
            }
            def outOfBoundsSprints = release.sprints.findAll {it.startDate >= endDate}
            if (outOfBoundsSprints) {
                Collection<Sprint> sprints = outOfBoundsSprints.findAll { Sprint sprint ->
                    return sprint.tasks || sprint.stories?.any { Story story -> story.tasks }
                }
                if (sprints) {
                    def sprintNames = sprints.collect { Sprint sprint -> g.message(code: 'is.sprint') + ' ' + sprint.orderNumber }.join(', ')
                    throw new IllegalStateException(g.message(code: 'is.release.error.sprint.tasks', args: [sprintNames]))
                }
                sprintService.delete(outOfBoundsSprints.min { it.startDate }) // deleting the first will delete the next ones
            }
            def overlappingSprint = release.sprints.find {it.endDate.after(endDate)}
            if (overlappingSprint) {
                if (overlappingSprint.state > Sprint.STATE_INPROGRESS) {
                    throw new IllegalStateException('is.release.error.endDate.before.inprogress.sprint')
                }
                sprintService.update(overlappingSprint, overlappingSprint.startDate, endDate, false, false)
            }
        }

        release.startDate = startDate
        release.endDate = endDate

        if (!release.save(flush: true))
            throw new RuntimeException()

        broadcast(function: 'update', message: release, channel:'product-'+release.parentProduct.id)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
    }

    void updateVision(Release release) {
        if (!release.save()) {
            throw new RuntimeException()
        }
        broadcast(function: 'vision', message: release, channel:'product-'+release.parentProduct.id)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumReleaseEvent.EVENT_UPDATED_VISION))
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void activate(Release release) {

        if (release.state != Release.STATE_WAIT)
            throw new IllegalStateException('is.release.error.not.state.wait')

        def product = release.parentProduct

        if (product.releases.find{it.state == Release.STATE_INPROGRESS})
            throw new IllegalStateException('is.release.error.already.active')

        def lastRelease = product.releases.findAll{it.state == Release.STATE_DONE}.max{ it.orderNumber }
        if ( lastRelease.orderNumber + 1 != release.orderNumber)
            throw new IllegalStateException('is.release.error.not.next')

        release.state = Release.STATE_INPROGRESS
        if (!release.save())
            throw new RuntimeException()

        broadcast(function: 'activate', message: release, channel:'product-'+release.parentProduct.id)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumReleaseEvent.EVENT_ACTIVATED))
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void close(Release release) {
        if (release.state != Release.STATE_INPROGRESS)
            throw new IllegalStateException('is.release.error.not.state.wait')

        def product = release.parentProduct

        release.state = Release.STATE_DONE

        def velocity = release.sprints.sum { it.velocity }
        velocity = release.sprints ? (velocity / release.sprints.size()) : 0
        release.releaseVelocity = velocity.toDouble()

        def lastDate = release.sprints ? release.sprints.asList().last().endDate : new Date()
        release.endDate = lastDate

        if (release.orderNumber == product.releases.size()) {
            product.endDate = lastDate
        }

        if (!release.save())
            throw new RuntimeException()

        broadcast(function: 'close', message: release, channel:'product-'+release.parentProduct.id)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumReleaseEvent.EVENT_CLOSED))
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void delete(Release release) {
        def product = release.parentProduct
        if (release.state == Release.STATE_INPROGRESS || release.state == Release.STATE_DONE)
            throw new IllegalStateException("is.release.error.not.deleted")

        def nextReleases = product.releases.findAll { it.orderNumber > release.orderNumber }
        release.removeAllAttachments()
        if (release.sprints) {
            storyService.unPlanAll(release.sprints)
        }
        product.removeFromReleases(release)
        release.features?.each{release.removeFromFeatures(it) }
        nextReleases.each {
            if (it.sprints) {
                storyService.unPlanAll(it.sprints)
            }
            product.removeFromReleases((Release) it)
            it.features?.each{ feature -> release.removeFromFeatures(feature) }
            broadcast(function: 'delete', message: [class: it.class, id: it.id], channel:'product-'+product.id)
        }
        def lastRelease = product.releases?.min {it.orderNumber}
        product.endDate = lastRelease?.endDate ?: null
        lastRelease?.lastUpdated = new Date()
        lastRelease?.save()
        broadcast(function: 'delete', message: [class: release.class, id: release.id], channel:'product-'+product.id)
    }

    def releaseBurndownValues(Release release) {
        def values = []
        Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])?.each { it ->
            def xmlRoot = new XmlSlurper().parseText(it.data)
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

    @Transactional(readOnly = true)
    def unMarshall(def release, Product p = null, ProgressSupport progress) {
        def g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
        try {
            def r = new Release(
                    state: release.state.text().toInteger(),
                    releaseVelocity: (release.releaseVelocity.text().isNumber()) ? release.releaseVelocity.text().toDouble() : 0,
                    name: release.name.text(),
                    dateCreated: release.dateCreated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(release.dateCreated.text()) : new Date(),
                    lastUpdated: release.lastUpdated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(release.lastUpdated.text()) : new Date(),
                    startDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(release.startDate.text()),
                    endDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(release.endDate.text()),
                    orderNumber: release.orderNumber.text().toInteger(),
                    description: release.description.text(),
                    vision: release.vision.text(),
                    goal: release.goal?.text() ?: '',
            )

            release.cliches.cliche.each {
                def c = clicheService.unMarshall(it)
                r.addToCliches(c)
            }

            if (p) {
                def sprintService = (SprintService) ApplicationHolder.application.mainContext.getBean('sprintService');
                release.sprints.sprint.eachWithIndex { it, index ->
                    def s = sprintService.unMarshall(it, p)
                    r.addToSprints(s)
                    progress?.updateProgress((release.sprints.sprint.size() * (index + 1) / 100).toInteger(), g.message(code: 'is.parse', args: [g.message(code: 'is.sprint')]))
                }
                p.addToReleases(r)
                release.features?.feature?.each { feature ->
                    def f = p.features.find { it.uid == feature.@uid.text().toInteger() } ?: null
                    if (f) {
                        r.addToFeatures(f)
                    }
                }
            }
            return r
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            progress?.progressError(g.message(code: 'is.parse.error', args: [g.message(code: 'is.sprint')]))
            throw new RuntimeException(e)
        }
    }
}