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
import org.icescrum.core.domain.*
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.utils.DateUtils

@Transactional
class FeatureService extends IceScrumEventPublisher {

    def springSecurityService
    def grailsApplication
    def activityService
    def securityService
    def commentService

    void save(Feature feature, workspace, String workspaceType = WorkspaceType.PROJECT) {
        ApplicationSupport.validateHexdecimalColor(feature.color)
        feature.name = feature.name?.trim()
        if (feature.value == null) {
            feature.value = 0 // TODO check if relevant (previously, it wasn't possible to create a feature with no value)
        }
        feature.uid = Feature.findNextUId(workspace.id, workspaceType)
        if (workspaceType == WorkspaceType.PROJECT) {
            feature.rank = Feature.countByBacklog(workspace) + 1
            feature.backlog = workspace
        } else {
            feature.rank = Feature.countByPortfolio(workspace) + 1
            feature.portfolio = workspace
        }
        workspace.addToFeatures(feature)
        feature.save(flush: true)
        feature.refresh() // required to initialize collections to empty list
        publishSynchronousEvent(IceScrumEventType.CREATE, feature)
    }

    void delete(Feature feature, String workspaceType = WorkspaceType.PROJECT) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, feature)
        def workspace
        if (workspaceType == WorkspaceType.PROJECT) {
            workspace = feature.backlog
            dirtyProperties.project = dirtyProperties.backlog
        } else if (workspaceType == WorkspaceType.PORTFOLIO) {
            workspace = feature.portfolio
        }
        feature.stories?.each {
            it.feature = null
            it.save()
        }
        workspace.removeFromFeatures(feature)
        workspace.features.each {
            if (it.rank > feature.rank) {
                it.rank--
                it.save()
            }
        }
        workspace.save()
        publishSynchronousEvent(IceScrumEventType.DELETE, feature, dirtyProperties)
    }

    void update(Feature feature, Map props = [:], String workspaceType = WorkspaceType.PROJECT) {
        ApplicationSupport.validateHexdecimalColor(feature.color)
        feature.name = feature.name.trim()
        if (workspaceType == WorkspaceType.PROJECT && props.state != null && feature.state != props.state) {
            state(feature, props.state)
        }
        if (feature.isDirty('rank')) {
            if (workspaceType == WorkspaceType.PROJECT) {
                Project project = (Project) feature.backlog
                if (project.portfolio && !securityService.businessOwner(project.portfolio, springSecurityService.authentication)) {
                    throw new BusinessException(code: 'is.feature.error.not.business.owner')
                }
            }
            rank(feature, workspaceType)
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, feature)
        if (feature.isDirty('color')) {
            feature.stories*.lastUpdated = new Date()
        }
        feature.save()
        publishSynchronousEvent(IceScrumEventType.UPDATE, feature, dirtyProperties)
    }

    private void state(Feature feature, Integer newState) {
        if (feature.state == Feature.STATE_BUSY && newState == Feature.STATE_DONE) {
            if (feature.stories.find { it.state < Story.STATE_DONE }) {
                throw new BusinessException(code: 'is.feature.error.done.stories')
            } else {
                feature.doneDate = new Date()
            }
        } else if (feature.state == Feature.STATE_DONE && newState == Feature.STATE_BUSY) {
            feature.doneDate = null
        }
    }

    private double calculateCompletion(stories) {
        double items = stories.size()
        double itemsDone = stories.findAll { it.state == Story.STATE_DONE }.size()
        return itemsDone / items
    }

    private void rank(Feature feature, String workspaceType) {
        Range affectedRange = feature.getPersistentValue('rank')..feature.rank
        int delta = affectedRange.isReverse() ? 1 : -1
        def backlog = workspaceType == WorkspaceType.PROJECT ? feature.backlog : feature.portfolio
        backlog.features.findAll {
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
                    todoDate: DateUtils.parseDateFromExport(featureXml.todoDate.text()),
                    doneDate: featureXml.doneDate.text() ? DateUtils.parseDateFromExport(featureXml.doneDate.text()) : null,
                    value: featureXml.value.text().isEmpty() ? 0 : featureXml.value.text().toInteger(),
                    type: featureXml.type.text().toInteger(),
                    rank: featureXml.rank.text().toInteger(),
                    uid: featureXml.@uid.text().toInteger()
            )
            // References on other objects
            if (project) {
                project.addToFeatures(feature)
                featureXml.comments.comment.each { _commentXml ->
                    def uid = options.userUIDByImportedID?."${_commentXml.posterId.text().toInteger()}" ?: null
                    User user = project.getUserByUidOrOwner(uid)
                    commentService.importComment(feature, user, _commentXml.body.text(), DateUtils.parseDateFromExport(_commentXml.dateCreated.text()))
                }
                feature.comments_count = featureXml.comments.comment.size() ?: 0
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
