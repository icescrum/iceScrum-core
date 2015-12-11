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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

import java.text.SimpleDateFormat
import org.icescrum.core.support.ProgressSupport
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*

@Transactional
class ReleaseService extends IceScrumEventPublisher {

    def storyService
    def clicheService
    def springSecurityService
    def grailsApplication

    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()

    @PreAuthorize('(productOwner(#product) or scrumMaster(#product)) and !archivedProduct(#product)')
    void save(Release release, Product product) {
        release.parentProduct = product
        release.state = Release.STATE_WAIT
        if (product.releases?.size() <= 0 || product.releases == null) {
            release.state = Release.STATE_INPROGRESS
        }
        release.orderNumber = (product.releases?.size() ?: 0) + 1
        if (!release.save(flush: true)) {
            throw new RuntimeException()
        }
        product.addToReleases(release)
        product.endDate = release.endDate
        publishSynchronousEvent(IceScrumEventType.CREATE, release)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void update(Release release, Date startDate = null, Date endDate = null, boolean checkIntegrity = true) {
        if (checkIntegrity && release.state == Release.STATE_DONE) {
            throw new IllegalStateException('is.release.error.update.state.done')
        }
        startDate = startDate ?: release.startDate
        endDate = endDate ?: release.endDate
        def nextRelease = release.nextRelease
        if (nextRelease && nextRelease.startDate <= endDate) {
            def nextStartDate = endDate + 1
            if (nextStartDate >= nextRelease.endDate) {
                throw new IllegalStateException('is.release.error.endDate.after.next.release')
            }
            update(nextRelease, nextStartDate) // cascade the update of next releases recursively
        }
        if (!release.sprints.isEmpty()) {
            def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
            def firstSprint = release.sprints.min { it.startDate }
            if (firstSprint.startDate.before(startDate)) {
                if (firstSprint.state >= Sprint.STATE_INPROGRESS) {
                    throw new IllegalStateException('is.release.error.startDate.after.inprogress.sprint')
                }
                sprintService.update(firstSprint, startDate, (startDate + firstSprint.duration - 1), false, false)
            }
            def outOfBoundsSprints = release.sprints.findAll { it.startDate >= endDate }
            if (outOfBoundsSprints) {
                Collection<Sprint> sprints = outOfBoundsSprints.findAll { Sprint sprint ->
                    return sprint.tasks || sprint.stories?.any { Story story -> story.tasks }
                }
                if (sprints) {
                    def sprintNames = sprints.collect { Sprint sprint -> g.message(code: 'is.sprint') + ' ' + sprint.orderNumber }.join(', ')
                    throw new IllegalStateException(g.message(code: 'is.release.error.sprint.tasks', args: [sprintNames]))
                }
                sprintService.delete(outOfBoundsSprints.min { it.startDate })
            }
            def overlappingSprint = release.sprints.find { it.endDate.after(endDate) }
            if (overlappingSprint) {
                if (overlappingSprint.state > Sprint.STATE_INPROGRESS) {
                    throw new IllegalStateException('is.release.error.endDate.before.inprogress.sprint')
                }
                sprintService.update(overlappingSprint, overlappingSprint.startDate, endDate, false, false)
            }
        }
        if (startDate != release.startDate) {
            release.startDate = startDate
        }
        if (endDate != release.endDate) {
            release.endDate = endDate
            if (release.orderNumber == release.parentProduct.releases.size()) {
                release.parentProduct.endDate = endDate
            }
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, release)
        if (!release.save(flush: true)) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, release, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void activate(Release release) {
        if (release.state != Release.STATE_WAIT) {
            throw new IllegalStateException('is.release.error.not.state.wait')
        }
        def product = release.parentProduct
        if (product.releases.find { it.state == Release.STATE_INPROGRESS }) {
            throw new IllegalStateException('is.release.error.already.active')
        }
        def lastRelease = product.releases.findAll { it.state == Release.STATE_DONE }.max { it.orderNumber }
        if (lastRelease.orderNumber + 1 != release.orderNumber) {
            throw new IllegalStateException('is.release.error.not.next')
        }
        release.state = Release.STATE_INPROGRESS
        update(release)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void close(Release release) {
        if (release.state != Release.STATE_INPROGRESS) {
            throw new IllegalStateException('is.release.error.not.state.wait')
        }
        release.state = Release.STATE_DONE
        def lastSprintEndDate = release.sprints ? release.sprints.asList().last().endDate : new Date()
        update(release, null, lastSprintEndDate, false)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void delete(Release release) {
        if (release.state >= Release.STATE_INPROGRESS) {
            throw new IllegalStateException("is.release.error.not.deleted")
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, release)
        if (release.sprints) {
            storyService.unPlanAll(release.sprints)
        }
        release.features?.each { release.removeFromFeatures(it) }
        def product = release.parentProduct
        product.removeFromReleases(release)
        if (product.releases) {
            product.releases.sort { it.startDate }.eachWithIndex { Release r, int i ->
                r.orderNumber = i + 1;
            }
            product.endDate = product.releases*.endDate.max()
        }
        if (!product.save(flush: true)) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.DELETE, release, dirtyProperties)
    }

    @PreAuthorize('stakeHolder(#release.parentProduct) or inProduct(#release.parentProduct)')
    def releaseBurndownValues(Release release) {
        def values = []
        Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])?.each { cliche ->
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
                def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
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