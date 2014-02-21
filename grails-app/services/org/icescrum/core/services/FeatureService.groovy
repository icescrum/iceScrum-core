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

import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

import java.text.SimpleDateFormat
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*

class FeatureService extends IceScrumEventPublisher {

    def springSecurityService

    @PreAuthorize('productOwner(#product) and !archivedProduct(#product)')
    void save(Feature feature, Product product) {
        feature.name = feature.name?.trim()
        def maxRank = Feature.countByBacklog(product) + 1
        if (feature.rank == 0) {
            feature.rank = maxRank
        } else {
            if (!(feature.rank in 1..maxRank)) {
                throw new RuntimeException()
            }
            rank(feature)
        }
        if (feature.value == null) {
            feature.value = 0 // TODO check if relevant (previously, it wasn't possible to create a feature with no value)
        }
        feature.uid = Feature.findNextUId(product.id)
        feature.backlog = product
        product.addToFeatures(feature)
        if (!feature.save()) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.CREATE, feature)
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    void delete(Feature feature) {
        def product = feature.backlog
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, feature)
        feature.stories?.each{
            it.feature = null
            it.save()
        }
        product.removeFromFeatures(feature)
        product.features.each {
            if (it.rank > feature.rank) {
                it.rank --
                it.save() // TODO consider push
            }
        }
        product.save()
        publishSynchronousEvent(IceScrumEventType.DELETE, feature, dirtyProperties)
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    void update(Feature feature) {
        feature.name = feature.name.trim()
        def product = feature.backlog
        if (feature.isDirty('rank')) {
            def maxRank = Feature.countByBacklog(product)
            if (!(feature.rank in 1..maxRank)) {
                throw new RuntimeException()
            }
            rank(feature)
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, feature)
        if (feature.isDirty('color')) {
            feature.stories*.lastUpdated = new Date()
        }
        if (!feature.save(flush: true)) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, feature, dirtyProperties)
    }

    // TODO check content of this method...
    @PreAuthorize('productOwner(#features[0].backlog) and !archivedProduct(#features[0].backlog)')
    def copyToBacklog(List<Feature> features) {
        def stories = []
        features.each { Feature feature ->
            def story = new Story(
                    name: feature.name,
                    description: feature.description,
                    suggestedDate: new Date(),
                    acceptedDate: new Date(),
                    state: Story.STATE_ACCEPTED,
                    feature: feature,
                    creator: (User)springSecurityService.currentUser,
                    rank: (Story.countAllAcceptedOrEstimated(feature.backlog.id)?.list()[0] ?: 0) + 1,
                    backlog: feature.backlog,
                    uid: Story.findNextUId(feature.backlog.id)
            )
            feature.addToStories(story)
            story.validate()
            def i = 1
            while (story.hasErrors()) {
                if (story.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                    i += 1
                    story.name = story.name + '_' + i
                    story.validate()
                } else if (story.errors.getFieldError('name')?.defaultMessage?.contains("maximum size")) {
                    story.name = story.name[0..20]
                    story.validate()
                }else {
                    throw new RuntimeException()
                }
            }
            if (!story.save()) {
                throw new RuntimeException(story.errors.toString())
            }
            broadcast(function: 'add', message: story, channel:'product-'+story.backlog.id)
            stories << story
        }
        return stories
    }

    double calculateCompletion(Feature feature, Release release = null) {
        def stories = Story.filterByFeature(feature.backlog, feature, release).list()
        if (stories.size() == 0) {
            return 0d
        }
        double items = stories.size()
        double itemsDone = stories.findAll {it.state == Story.STATE_DONE}.size()
        return itemsDone / items
    }

    private void rank(Feature feature) {
        Range affectedRange = feature.getPersistentValue('rank')..feature.rank
        int delta = affectedRange.isReverse() ? 1 : -1
        feature.backlog.features.findAll {
            it != feature && it.rank in affectedRange
        }.each {
            it.rank += delta
            it.save() // consider push
        }
    }

    def productParkingLotValues(Product product) {
        def values = []
        product.features?.each { it ->
            def value = 100d * calculateCompletion(it)
            values << [label: it.name, value: value]
        }
        return values
    }

    def releaseParkingLotValues(Release release) {
        def values = []
        release.parentProduct.features?.each { it ->
            def value = 100d * calculateCompletion(it, release)
            values << [label: it.name, value: value]
        }
        return values
    }

    @Transactional(readOnly = true)
    def unMarshall(def feat) {
        try {
            def f = new Feature(
                    name: feat."${'name'}".text(),
                    description: feat.description.text(),
                    notes: feat.notes.text(),
                    color: feat.color.text(),
                    creationDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(feat.creationDate.text()),
                    value: feat.value.text().toInteger(),
                    type: feat.type.text().toInteger(),
                    rank: feat.rank.text()?.toInteger(),
                    uid: feat.@uid.text()?.isEmpty() ? feat.@id.text().toInteger() : feat.@uid.text().toInteger()
            )
            return f
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }
}