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
 */

package org.icescrum.core.services

import java.text.SimpleDateFormat
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumFeatureEvent
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*

class FeatureService {

    static transactional = true
    def springSecurityService

    @PreAuthorize('productOwner(#product) and !archivedProduct(#product)')
    void save(Feature feature, Product product) {

        feature.name = feature.name?.trim()

        def rankProvided = null
        if (feature.rank != 0)
            rankProvided = feature.rank

        //We force last rank (if another rank has benn provide we will update it below
        feature.rank = Feature.countByBacklog(product) + 1
        feature.uid = Feature.findNextUId(product.id)


        feature.backlog = product
        product.addToFeatures(feature)


        if (!feature.save()) {
            throw new RuntimeException()
        }

        //We put the real rank if we need
        if (rankProvided)
            rank(feature, rankProvided)

        broadcast(function: 'add', message: feature, channel:'product-'+product.id)
        publishEvent(new IceScrumFeatureEvent(feature, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    void delete(Feature feature) {
        def product = feature.backlog
        bufferBroadcast(channel:'product-'+product.id)
        feature.stories?.each{
            it.feature = null
            it.save()
            broadcast(function: 'update', message: it, channel:'product-'+product.id)
        }


        def oldRank = feature.rank
        def id = feature.id

        product.removeFromFeatures(feature)

        //update rank on all features after that one
        product.features.each { it ->
            if (it.rank > oldRank) {
                it.rank = it.rank - 1
                it.save()
            }
        }
        broadcast(function: 'delete', message: [class: feature.class, id: id], channel:'product-'+product.id)
        resumeBufferedBroadcast(channel:'product-'+product.id)
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    void update(Feature feature) {
        feature.name = feature.name.trim()

        if (!feature.save(flush: true)) {
            throw new RuntimeException()
        }
        broadcast(function: 'update', message: feature, channel:'product-'+feature.backlog.id)
        publishEvent(new IceScrumFeatureEvent(feature, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    def copyToBacklog(def feature) {
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
        publishEvent(new IceScrumFeatureEvent(feature, story, this.class, (User) springSecurityService.currentUser, IceScrumFeatureEvent.EVENT_COPIED_AS_STORY))
        return story
    }

    double calculateCompletion(Feature feature, Release release = null) {
        def stories = Story.filterByFeature(feature.backlog, feature, release).list()

        if (stories.size() == 0)
            return 0d

        double items = stories.size()
        double itemsDone = stories.findAll {it.state == Story.STATE_DONE}.size()

        return itemsDone / items
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    boolean rank(Feature feature, int rank) {
        if (feature.rank != rank) {
            if (feature.rank > rank) {
                feature.backlog.features?.sort()?.each {it ->
                    if (it.rank >= rank && it.rank <= feature.rank && it != feature) {
                        it.rank = it.rank + 1
                        it.save()
                    }
                }
            } else {
                feature.backlog.features?.sort()?.each {it ->
                    if (it.rank <= rank && it.rank >= feature.rank && it != feature) {
                        it.rank = it.rank - 1
                        it.save()
                    }
                }
            }
            feature.rank = rank

            broadcast(function: 'update', message: feature, channel:'product-'+feature.backlog.id)
            return feature.save() ? true : false
        } else {
            return false
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