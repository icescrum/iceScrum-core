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
import org.icescrum.core.support.ApplicationSupport
import org.springframework.security.access.prepost.PreAuthorize

import java.text.SimpleDateFormat

@Transactional
class ReleaseService extends IceScrumEventPublisher {

    def storyService
    def clicheService
    def springSecurityService
    def grailsApplication

    @PreAuthorize('(productOwner(#project) or scrumMaster(#project)) and !archivedProject(#project)')
    void save(Release release, Project project) {
        release.parentProject = project
        release.state = Release.STATE_WAIT
        if (project.releases?.size() <= 0 || project.releases == null) {
            release.inProgressDate = new Date()
            release.state = Release.STATE_INPROGRESS
        }
        release.orderNumber = (project.releases?.size() ?: 0) + 1
        release.save(flush: true)
        project.addToReleases(release)
        project.endDate = release.endDate
        publishSynchronousEvent(IceScrumEventType.CREATE, release)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
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
                    def g = grailsApplication.mainContext.getBean("org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib")
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
            if (release.orderNumber == release.parentProject.releases.size()) {
                release.parentProject.endDate = endDate
            }
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, release)
        release.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, release, dirtyProperties)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void activate(Release release) {
        if (release.state != Release.STATE_WAIT) {
            throw new BusinessException(code: 'is.release.error.not.state.wait')
        }
        def project = release.parentProject
        if (project.releases.find { it.state == Release.STATE_INPROGRESS }) {
            throw new BusinessException(code: 'is.release.error.already.active')
        }
        def lastRelease = project.releases.findAll { it.state == Release.STATE_DONE }.max { it.orderNumber }
        if (lastRelease.orderNumber + 1 != release.orderNumber) {
            throw new BusinessException(code: 'is.release.error.not.next')
        }
        release.inProgressDate = new Date()
        release.state = Release.STATE_INPROGRESS
        update(release)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void close(Release release) {
        if (release.state != Release.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.release.error.not.state.wait')
        }
        release.doneDate = new Date()
        release.state = Release.STATE_DONE
        def lastSprintEndDate = release.sprints ? release.sprints.asList().last().endDate : new Date()
        update(release, null, lastSprintEndDate, false)
    }

    @PreAuthorize('(productOwner(#release.parentProject) or scrumMaster(#release.parentProject)) and !archivedProject(#release.parentProject)')
    void delete(Release release) {
        if (release.state >= Release.STATE_INPROGRESS) {
            throw new BusinessException(code: 'is.release.error.not.deleted')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, release)
        if (release.sprints) {
            storyService.unPlanAll(release.sprints)
        }
        release.features?.each { release.removeFromFeatures(it) }
        def project = release.parentProject
        project.removeFromReleases(release)
        if (project.releases) {
            project.releases.sort { it.startDate }.eachWithIndex { Release r, int i ->
                r.orderNumber = i + 1;
            }
            project.endDate = project.releases*.endDate.max()
        }
        project.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.DELETE, release, dirtyProperties)
    }

    @PreAuthorize('stakeHolder(#release.parentProject) or inProject(#release.parentProject)')
    def releaseBurndownValues(Release release) {
        def values = []
        def cliches = []
        //begin of project
        def firstClicheActivation = Cliche.findByParentTimeBoxAndType(release, Cliche.TYPE_ACTIVATION, [sort: "datePrise", order: "asc"])
        if (firstClicheActivation)
            cliches.add(firstClicheActivation)
        //others cliches
        cliches.addAll(Cliche.findAllByParentTimeBoxAndType(release, Cliche.TYPE_CLOSE, [sort: "datePrise", order: "asc"]))
        //transient cliche
        if (release.state == Release.STATE_INPROGRESS) {
            def sprint = null
            sprint = release.sprints.find { it.state == Sprint.STATE_INPROGRESS }
            if (sprint) {
                cliches << [data: clicheService.generateSprintClicheData(sprint, Cliche.TYPE_CLOSE)]
            }
        }
        cliches?.eachWithIndex { cliche, index ->
            def xmlRoot = new XmlSlurper().parseText(cliche.data)
            if (xmlRoot) {
                def sprintEntry = [
                        userstories     : xmlRoot."${Cliche.FUNCTIONAL_STORY_PROJECT_REMAINING_POINTS}".toBigDecimal(),
                        technicalstories: xmlRoot."${Cliche.TECHNICAL_STORY_PROJECT_REMAINING_POINTS}".toBigDecimal(),
                        defectstories   : xmlRoot."${Cliche.DEFECT_STORY_PROJECT_REMAINING_POINTS}".toBigDecimal(),
                        label           : index == 0 ? "Start" : xmlRoot."${Cliche.SPRINT_ID}".toString() + "${cliche.id ?'': " (progress)"}"
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
        Project project = options.project
        Release.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def inProgressDate = null
                if (releaseXml.inProgressDate?.text() && releaseXml.inProgressDate?.text() != "") {
                    inProgressDate = ApplicationSupport.parseDate(releaseXml.inProgressDate.text())
                }
                def doneDate = null
                if (releaseXml.doneDate?.text() && releaseXml.doneDate?.text() != "") {
                    doneDate = ApplicationSupport.parseDate(releaseXml.doneDate.text())
                }
                def todoDate = null
                if (releaseXml.todoDate?.text() && releaseXml.todoDate?.text() != "") {
                    todoDate = ApplicationSupport.parseDate(releaseXml.todoDate.text())
                } else if (releaseXml.dateCreated?.text() && releaseXml.dateCreated?.text() != "") {
                    todoDate = ApplicationSupport.parseDate(releaseXml.dateCreated.text())
                } else if (project) {
                    todoDate = project.todoDate
                }
                def release = new Release(
                        state: releaseXml.state.text().toInteger(),
                        name: releaseXml.name.text(),
                        todoDate: todoDate,
                        startDate: ApplicationSupport.parseDate(releaseXml.startDate.text()),
                        doneDate: doneDate,
                        inProgressDate: inProgressDate,
                        endDate: ApplicationSupport.parseDate(releaseXml.endDate.text()),
                        orderNumber: releaseXml.orderNumber.text().toInteger(),
                        firstSprintIndex: releaseXml.firstSprintIndex.text() ? releaseXml.firstSprintIndex.toInteger() : 1,
                        description: releaseXml.description.text() ?: null,
                        vision: releaseXml.vision.text() ?: null,
                        goal: releaseXml.goal?.text() ?: null)
                options.release = release
                if (project) {
                    project.addToReleases(release)
                    // Save before some hibernate stuff
                    if (options.save) {
                        release.save()
                    }
                    def sprintService = (SprintService) grailsApplication.mainContext.getBean('sprintService')
                    releaseXml.sprints.sprint.eachWithIndex { it, index ->
                        sprintService.unMarshall(it, options)
                    }
                    releaseXml.features.feature.each { feature ->
                        def f = project.features.find { it.uid == feature.@uid.text().toInteger() } ?: null
                        if (f) {
                            release.addToFeatures(f)
                        }
                    }
                }
                // Save before some hibernate stuff
                if (options.save) {
                    release.save()
                    if (project) {
                        releaseXml.attachments.attachment.each { _attachmentXml ->
                            def uid = options.IDUIDUserMatch?."${_attachmentXml.posterId.text().toInteger()}" ?: null
                            User user = (User) project.getAllUsers().find { it.uid == uid } ?: (User) springSecurityService.currentUser
                            ApplicationSupport.importAttachment(release, user, options.path, _attachmentXml)
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
                return (Release) importDomainsPlugins(releaseXml, release, options)
            } catch (Exception e) {
                if (log.debugEnabled) e.printStackTrace()
                throw new RuntimeException(e)
            }
        }
    }
}
