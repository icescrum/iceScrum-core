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

import grails.transaction.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize

import java.text.SimpleDateFormat

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
            release.inProgressDate = new Date()
            release.state = Release.STATE_INPROGRESS
        }
        release.orderNumber = (product.releases?.size() ?: 0) + 1
        release.save(flush: true)
        product.addToReleases(release)
        product.endDate = release.endDate
        publishSynchronousEvent(IceScrumEventType.CREATE, release)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void update(Release release, Date startDate = null, Date endDate = null, boolean checkIntegrity = true) {
        if (checkIntegrity && release.state == Release.STATE_DONE) {
            def illegalDirtyProperties = release.dirtyPropertyNames - ['name', 'vision']
            if (illegalDirtyProperties) {
                throw new BusinessException(code: 'is.release.error.update.state.done')
            }
        }
        startDate = startDate ?: release.startDate
        endDate = endDate ?: release.endDate
        def nextRelease = release.nextRelease
        if (nextRelease && nextRelease.startDate <= endDate) {
            def nextStartDate = endDate + 1
            if (nextStartDate >= nextRelease.endDate) {
                throw new BusinessException(code: 'is.release.error.endDate.after.next.release')
            }
            update(nextRelease, nextStartDate) // cascade the update of next releases recursively
        }
        if (!release.sprints.isEmpty()) {
            def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
            def firstSprint = release.sprints.min { it.startDate }
            if (firstSprint.startDate.before(startDate)) {
                if (firstSprint.state >= Sprint.STATE_INPROGRESS) {
                    throw new BusinessException(code: 'is.release.error.startDate.after.inprogress.sprint')
                }
                sprintService.update(firstSprint, startDate, (startDate + firstSprint.duration - 1), false, false)
            }
            def outOfBoundsSprints = release.sprints.findAll { it.startDate >= endDate }
            if (outOfBoundsSprints) {
                Collection<Sprint> sprints = outOfBoundsSprints.findAll { Sprint sprint ->
                    return sprint.tasks || sprint.stories?.any { Story story -> story.tasks }
                }
                if (sprints) {
                    def sprintNames = sprints.collect { Sprint sprint -> g.message(code: 'is.sprint') + ' ' + sprint.index }.join(', ')
                    throw new BusinessException(code: 'is.release.error.sprint.tasks', args: [sprintNames])
                }
                sprintService.delete(outOfBoundsSprints.min { it.startDate })
            }
            def overlappingSprint = release.sprints.find { it.endDate.after(endDate) }
            if (overlappingSprint) {
                if (overlappingSprint.state > Sprint.STATE_INPROGRESS) {
                    throw new BusinessException(code: 'is.release.error.endDate.before.inprogress.sprint')
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
        release.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, release, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void activate(Release release) {
        if (release.state != Release.STATE_WAIT) {
            throw new BusinessException(code: 'is.release.error.not.state.wait')
        }
        def product = release.parentProduct
        if (product.releases.find { it.state == Release.STATE_INPROGRESS }) {
            throw new BusinessException(code: 'is.release.error.already.active')
        }
        def lastRelease = product.releases.findAll { it.state == Release.STATE_DONE }.max { it.orderNumber }
        if (lastRelease.orderNumber + 1 != release.orderNumber) {
            throw new BusinessException(code: 'is.release.error.not.next')
        }
        release.inProgressDate = new Date()
        release.state = Release.STATE_INPROGRESS
        update(release)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void close(Release release) {
        if (release.state != Release.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.release.error.not.state.wait')
        }
        release.doneDate = new Date()
        release.state = Release.STATE_DONE
        def lastSprintEndDate = release.sprints ? release.sprints.asList().last().endDate : new Date()
        update(release, null, lastSprintEndDate, false)
    }

    @PreAuthorize('(productOwner(#release.parentProduct) or scrumMaster(#release.parentProduct)) and !archivedProduct(#release.parentProduct)')
    void delete(Release release) {
        if (release.state >= Release.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.release.error.not.deleted')
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
        product.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.DELETE, release, dirtyProperties)
    }

    @PreAuthorize('stakeHolder(#release.parentProduct) or inProduct(#release.parentProduct)')
    def releaseBurndownValues(Release release) {
        def values = []
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
                def sprintEntry = [
                        userstories     : xmlRoot."${Cliche.FUNCTIONAL_STORY_PRODUCT_REMAINING_POINTS}".toBigDecimal(),
                        technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_PRODUCT_REMAINING_POINTS}".toBigDecimal(),
                        defectstories   : xmlRoot."${Cliche.DEFECT_STORY_PRODUCT_REMAINING_POINTS}".toBigDecimal(),
                        label: index == 0 ? "Start" : xmlRoot."${Cliche.SPRINT_ID}".toString()+"${cliche.type == Cliche.TYPE_ACTIVATION ? " (progress)" : ""}"
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

    def unMarshall(def releaseXml, def options) {
        Product product = options.product
        Release.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def inProgressDate = null
                if (releaseXml.inProgressDate?.text() && releaseXml.inProgressDate?.text() != "") {
                    inProgressDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.inProgressDate.text()) ?: null
                }
                def doneDate = null
                if (releaseXml.doneDate?.text() && releaseXml.doneDate?.text() != "") {
                    doneDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.doneDate.text()) ?: null
                }
                def todoDate = null
                if (releaseXml.todoDate?.text() && releaseXml.todoDate?.text() != "") {
                    todoDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.todoDate.text())
                } else if (releaseXml.dateCreated?.text() && releaseXml.dateCreated?.text() != "") {
                    todoDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.dateCreated.text())
                } else if (product) {
                    todoDate = product.todoDate
                }
                def release = new Release(
                        state: releaseXml.state.text().toInteger(),
                        name: releaseXml.name.text(),
                        lastUpdated: releaseXml.lastUpdated.text() ? new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.lastUpdated.text()) : new Date(),
                        todoDate: todoDate,
                        startDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.startDate.text()),
                        doneDate: doneDate,
                        inProgressDate: inProgressDate,
                        endDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(releaseXml.endDate.text()),
                        orderNumber: releaseXml.orderNumber.text().toInteger(),
                        firstSprintIndex: releaseXml.firstSprintIndex.text() ? releaseXml.firstSprintIndex.toInteger() : 1,
                        description: releaseXml.description.text(),
                        vision: releaseXml.vision.text(),
                        goal: releaseXml.goal?.text() ?: '')
                options.release = release
                if (product) {
                    product.addToReleases(release)
                    // Save before some hibernate stuff
                    if (options.save) {
                        release.save()
                    }
                    def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
                    releaseXml.sprints.sprint.eachWithIndex { it, index ->
                        sprintService.unMarshall(it, options)
                    }
                    releaseXml.features?.feature?.each { feature ->
                        def f = product.features.find { it.uid == feature.@uid.text().toInteger() } ?: null
                        if (f) {
                            release.addToFeatures(f)
                        }
                    }
                }
                // Child objects
                options.timebox = release
                releaseXml.cliches.cliche.each {
                    clicheService.unMarshall(it, options)
                }
                options.timebox = null

                if (options.save) {
                    release.save()
                }
                options.release = null
                return (Release) importDomainsPlugins(release, options)
            } catch (Exception e) {
                if (log.debugEnabled) e.printStackTrace()
                throw new RuntimeException(e)
            }
        }
    }
}
