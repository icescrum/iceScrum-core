/*
 * Copyright (c) 2014 Kagilum SAS
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
 *
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import grails.validation.ValidationException
import org.icescrum.core.domain.*
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class FeatureService extends IceScrumEventPublisher {

    def springSecurityService
    def grailsApplication
    def activityService

    @PreAuthorize('productOwner(#project) and !archivedProject(#project)')
    void save(Feature feature, Project project) {
        feature.name = feature.name?.trim()
        feature.rank = Feature.countByBacklog(project) + 1
        if (feature.value == null) {
            feature.value = 0 // TODO check if relevant (previously, it wasn't possible to create a feature with no value)
        }
        feature.uid = Feature.findNextUId(project.id)
        feature.backlog = project
        project.addToFeatures(feature)
        feature.save(flush: true)
        feature.refresh() // required to initialize collections to empty list
        publishSynchronousEvent(IceScrumEventType.CREATE, feature)
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProject(#feature.backlog)')
    void delete(Feature feature) {
        def project = feature.backlog
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, feature)
        feature.stories?.each {
            it.feature = null
            it.save()
        }
        project.removeFromFeatures(feature)
        project.features.each {
            if (it.rank > feature.rank) {
                it.rank--
                it.save()
            }
        }
        project.save()
        publishSynchronousEvent(IceScrumEventType.DELETE, feature, dirtyProperties)
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProject(#feature.backlog)')
    void update(Feature feature) {
        feature.name = feature.name.trim()
        if (feature.isDirty('rank')) {
            rank(feature)
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, feature)
        if (feature.isDirty('color')) {
            feature.stories*.lastUpdated = new Date()
        }
        feature.save()
        publishSynchronousEvent(IceScrumEventType.UPDATE, feature, dirtyProperties)
    }

    @PreAuthorize('productOwner(#features[0].backlog) and !archivedProject(#features[0].backlog)')
    def createStoryEpic(List<Feature> features) {
        def stories = []
        StoryService storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        features.each { Feature feature ->
            def story = new Story(
                    name: feature.name,
                    description: feature.description,
                    suggestedDate: new Date(),
                    acceptedDate: new Date(), // The proper state will be set automatically
                    feature: feature,
                    backlog: feature.backlog, // Will be set again by storyService but required to pass validation
            )
            feature.addToStories(story)
            story.validate()
            def i = 1
            while (story.hasErrors()) {
                if (story.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    story.name = story.name + '_' + i
                    story.validate()
                } else if (story.errors.getFieldError('name')?.code?.contains("maxSize.exceeded")) {
                    story.name = story.name[0..20]
                    story.validate()
                } else {
                    throw new ValidationException('Validation Error(s) occurred during save()', story.errors)
                }
            }
            storyService.save(story, story.backlog, (User) springSecurityService.currentUser)
            stories << story
        }
        return stories
    }

    private double calculateCompletion(stories) {
        double items = stories.size()
        double itemsDone = stories.findAll { it.state == Story.STATE_DONE }.size()
        return itemsDone / items
    }

    private void rank(Feature feature) {
        Range affectedRange = feature.getPersistentValue('rank')..feature.rank
        int delta = affectedRange.isReverse() ? 1 : -1
        feature.backlog.features.findAll {
            it != feature && it.rank in affectedRange
        }.each {
            it.rank += delta
            it.save()
        }
    }

    def projectParkingLotValues(Project project) {
        def values = []
        project.features?.each { feature ->
            def stories = Story.findAllByBacklogAndFeature(project, feature)
            if (stories) {
                def value = 100d * calculateCompletion(stories)
                values << [label: feature.name, value: value, color: feature.color, feature: feature]
            }
        }
        return values.sort { a, b -> a.value <=> b.value ?: a.label <=> b.label }
    }

    def releaseParkingLotValues(Release release) {
        def values = []
        release.parentProject.features?.each { feature ->
            def stories = Story.findAllByReleaseAndFeature(release, feature).list()
            if (stories) {
                def value = 100d * calculateCompletion(stories)
                values << [label: feature.name, value: value, color: feature.color]
            }
        }
        return values.sort { a, b -> a.value <=> b.value ?: a.label <=> b.label }
    }

    def unMarshall(def featureXml, def options) {
        Project project = options.project
        Feature.withTransaction(readOnly: !options.save) { transaction ->
            def feature = new Feature(
                    name: featureXml."${'name'}".text(),
                    description: featureXml.description.text(),
                    notes: featureXml.notes.text(),
                    color: featureXml.color.text(),
                    todoDate: ApplicationSupport.parseDate(featureXml.todoDate.text()),
                    value: featureXml.value.text().isEmpty() ? 0 : featureXml.value.text().toInteger(),
                    type: featureXml.type.text().toInteger(),
                    rank: featureXml.rank.text().toInteger(),
                    uid: featureXml.@uid.text().toInteger()
            )
            // References on other objects
            if (project) {
                project.addToFeatures(feature)
            }
            // Save before some hibernate stuff
            if (options.save) {
                feature.save()
                // Handle tags
                if (featureXml.tags.text()) {
                    feature.tags = featureXml.tags.text().replaceAll(' ', '').replace('[', '').replace(']', '').split(',')
                }
                featureXml.attachments.attachment.each { _attachmentXml ->
                    def uid = options.userUIDByImportedID?."${_attachmentXml.posterId.text().toInteger()}" ?: null
                    User user = project.getUserByUidOrOwner(uid)
                    ApplicationSupport.importAttachment(feature, user, options.path, _attachmentXml)
                }
                feature.attachments_count = featureXml.attachments.attachment.size() ?: 0
            }
            // Child objects
            options.feature = feature
            options.parent = feature
            featureXml.activities.activity.each { def activityXml ->
                activityService.unMarshall(activityXml, options)
            }
            options.parent = null
            if (options.save) {
                feature.save()
            }
            options.feature = null
            return (Feature) importDomainsPlugins(featureXml, feature, options)
        }
    }
}
