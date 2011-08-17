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
 */

package org.icescrum.core.services

import groovy.util.slurpersupport.NodeChild
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
    def g = new org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib()


    @PreAuthorize('(productOwner(#product) or scrumMaster()) and !archivedProduct(#product)')
    void save(Release release, Product product) {
        release.parentProduct = product

        // Check data integrity
        if (release.endDate == null) {
            throw new IllegalStateException('is.release.error.no.endDate')
        } else if (release.startDate.after(release.endDate)) {
            throw new IllegalStateException('is.release.error.startDate.before.endDate')
        } else if (release.startDate == null) {
            throw new IllegalStateException('is.release.error.no.startDate')
        } else if (release.startDate == release.endDate) {
            throw new IllegalStateException('is.release.error.startDate.equals.endDate')
        } else if (release.startDate.before(product.startDate)) {
            throw new IllegalStateException('is.release.error.startDate.before.productStartDate')
        } else {
            Release _r = productService.getLastRelease(product)
            if (_r != null && _r.endDate.after(release.startDate)) {
                throw new IllegalStateException('is.release.error.startDate.before.previous')
            }
        }
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

    @PreAuthorize('(productOwner() or scrumMaster()) and !archivedProduct()')
    void update(Release release, Date startDate = null, Date endDate = null) {
        def product = release.parentProduct
        if (!startDate) {
            startDate = release.startDate
        }

        if (!endDate) {
            endDate = release.endDate
        }

        if (release.state == Release.STATE_DONE)
            throw new IllegalStateException('is.release.error.state.done')

        // Check sprint date integrity
        if (startDate > endDate)
            throw new IllegalStateException('is.release.error.startDate.before.endDate')
        if (startDate == endDate)
            throw new IllegalStateException('is.release.error.startDate.equals.endDate')
        if (startDate.before(product.startDate)) {
            throw new IllegalStateException('is.release.error.startDate.before.productStartDate')
        }
        int ind = product.releases.asList().indexOf(release)

        // Check that the start date is after the previous release end date
        if (ind > 0) {
            Release _previous = product.releases.asList()[ind - 1]
            if (_previous.endDate.after(startDate)) {
                throw new IllegalStateException('is.release.error.startDate.before.previous')
            }
        }

        def sprintService = (SprintService) ApplicationHolder.application.mainContext.getBean('sprintService');

        if (release.startDate != startDate && startDate >= release.startDate) {
            if (!release.sprints.isEmpty()) {
                def firstSprint = release.sprints.asList().first()
                //we update the first sprint and next sprints in release if needed
                if (firstSprint.startDate < startDate && firstSprint.state >= Sprint.STATE_INPROGRESS) {
                    throw new IllegalStateException('is.release.error.endDate.before.inprogress.sprint')
                }
                if (firstSprint.startDate < startDate) {
                    sprintService.update(firstSprint, startDate, (startDate + (firstSprint.endDate - firstSprint.startDate)))
                }
            }
        }

        // If there are sprints that are out of the bound of the release's dates
        // we reduce the time alocated to the sprints or delete them if there is not enough time.
        if (release.endDate != endDate && endDate <= release.endDate) {
            if (!release.sprints.isEmpty()) {
                // Retrieve the sprints that are out of the bound of the dates interval
                def tooHighSprint = release.sprints.findAll {it.startDate >= endDate}
                if (tooHighSprint) {
                    // Those sprints are deleted and their stories return in the backlog
                    for (Sprint s: tooHighSprint) {
                        sprintService.delete(s)
                    }
                }

                // Check for a sprint that can be reduced
                def sprintToReduce = release.sprints.find {it.endDate > endDate}
                if (sprintToReduce && sprintToReduce.state < Sprint.STATE_INPROGRESS) {
                    sprintToReduce.endDate = endDate
                    sprintService.update(sprintToReduce, sprintToReduce.startDate, endDate)
                } else if (sprintToReduce && sprintToReduce.state >= Sprint.STATE_INPROGRESS) {
                    throw new IllegalStateException('is.release.error.endDate.before.inprogress.sprint')
                }
            }
        }

        // Check that the end date is before the next release start date
        if (ind < product.releases.size() - 1) {
            Release _next = product.releases.asList()[ind + 1]
            if (_next.startDate <= (endDate)) {
                if (!release.save())
                    throw new RuntimeException()
                _next.startDate = endDate + 1
                _next.endDate = _next.endDate + 1
                //We update all releases after
                this.update(_next)
                if (!_next.sprints.isEmpty()) {
                    def firstSprint = _next.sprints.asList().first()
                    //we update the first sprint and next sprints in release if needed
                    if (firstSprint.startDate < _next.startDate) {
                        sprintService.update(firstSprint, _next.startDate, firstSprint.endDate)
                    }
                }
                return
            }
        }

        release.endDate = endDate
        release.startDate = startDate
        if (!release.save(flush: true))
            throw new RuntimeException()

        broadcast(function: 'update', message: release)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
    }

    void updateVision(Release release) {
        if (!release.save()) {
            throw new RuntimeException()
        }
        broadcast(function: 'vision', message: release)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumReleaseEvent.EVENT_UPDATED_VISION))
    }

    @PreAuthorize('(productOwner() or scrumMaster()) and !archivedProduct()')
    void activate(Release release) {
        def relActivated = false
        def lastRelClose = 0
        def product = release.parentProduct
        product.releases.sort {a, b -> a.orderNumber <=> b.orderNumber}.eachWithIndex { r, i ->
            if (r.state == Release.STATE_INPROGRESS)
                relActivated = true
            else if (r.state == Release.STATE_DONE)
                lastRelClose = r.orderNumber
        }
        if (relActivated)
            throw new IllegalStateException('is.release.error.already.active')
        if (release.state != Release.STATE_WAIT)
            throw new IllegalStateException('is.release.error.not.state.wait')
        if (release.orderNumber != lastRelClose + 1)
            throw new IllegalStateException('is.release.error.not.next')
        if (release.sprints.size() <= 0)
            throw new IllegalStateException('is.release.error.no.sprint')
        release.state = Release.STATE_INPROGRESS
        if (!release.save())
            throw new RuntimeException()

        broadcast(function: 'activate', message: release)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumReleaseEvent.EVENT_ACTIVATED))
    }

    @PreAuthorize('(productOwner() or scrumMaster()) and !archivedProduct()')
    void close(Release release) {
        def product = release.parentProduct
        if (release.sprints.size() == 0 || release.sprints.any { it.state != Sprint.STATE_DONE })
            throw new IllegalStateException('is.release.error.sprint.not.done')
        release.state = Release.STATE_DONE

        def velocity = release.sprints.sum { it.velocity }
        velocity = (velocity / release.sprints.size())
        release.releaseVelocity = velocity.toDouble()

        def lastDate = release.sprints.asList().last().endDate
        release.endDate = lastDate

        if (release.orderNumber == product.releases.size()) {
            product.endDate = lastDate
        }

        if (!release.save())
            throw new RuntimeException()

        broadcast(function: 'close', message: release)
        publishEvent(new IceScrumReleaseEvent(release, this.class, (User) springSecurityService.currentUser, IceScrumReleaseEvent.EVENT_CLOSED))
    }

    @PreAuthorize('(productOwner() or scrumMaster()) and !archivedProduct()')
    void delete(Release release) {
        def product = release.parentProduct
        if (release.state == Release.STATE_INPROGRESS || release.state == Release.STATE_DONE)
            throw new IllegalStateException("is.release.error.not.deleted")

        def nextReleases = product.releases.findAll { it.orderNumber > release.orderNumber }

        storyService.unPlanAll(release.sprints)
        product.removeFromReleases(release)

        nextReleases.each {
            storyService.unPlanAll(it.sprints)
            product.removeFromReleases((Release) it)
            broadcast(function: 'delete', message: [class: it.class, id: it.id])
        }
        product.endDate = product.releases?.min {it.orderNumber}?.endDate ?: null

        broadcast(function: 'delete', message: [class: release.class, id: release.id])
    }


    def releaseBurndownValues(Release release) {
        def values = []
        Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])?.each { it ->
            def xmlRoot = new XmlSlurper().parseText(it.data)
            if (xmlRoot) {
                values << [
                        label: xmlRoot."${Cliche.SPRINT_ID}".toString(),
                        userstories: xmlRoot."${Cliche.FUNCTIONAL_STORY_PRODUCT_REMAINING_POINTS}".toInteger(),
                        technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_PRODUCT_REMAINING_POINTS}".toInteger(),
                        defectstories: xmlRoot."${Cliche.DEFECT_STORY_PRODUCT_REMAINING_POINTS}".toInteger()
                ]
            }
        }
        return values
    }

    @Transactional(readOnly = true)
    def unMarshall(NodeChild release, Product p = null, ProgressSupport progress) {
        try {
            def r = new Release(
                    state: release.state.text().toInteger(),
                    releaseVelocity: (release.releaseVelocity.text().isNumber()) ? release.releaseVelocity.text().toDouble() : 0,
                    name: release.name.text(),
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
            }
            return r
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            progress?.progressError(g.message(code: 'is.parse.error', args: [g.message(code: 'is.sprint')]))
            throw new RuntimeException(e)
        }
    }
}