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
    def productService
    def springSecurityService

    @PreAuthorize('productOwner(#p) and !archivedProduct(#p)')
    void save(Feature feature, Product p) {

        feature.name = feature.name?.trim()

        def rankProvided = null
        if (feature.rank != 0)
            rankProvided = feature.rank

        //We force last rank (if another rank has benn provide we will update it below
        feature.rank = Feature.countByBacklog(p) + 1
        feature.uid = Feature.findNextUId(p.id)


        feature.backlog = p
        p.addToFeatures(feature)


        if (!feature.save()) {
            throw new RuntimeException()
        }

        //We put the real rank if we need
        if (rankProvided)
            rank(feature, rankProvided)

        broadcast(function: 'add', message: feature)
        publishEvent(new IceScrumFeatureEvent(feature, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    void delete(Feature feature) {
        def p = feature.backlog

        bufferBroadcast()
        feature.stories?.each{
            it.feature = null
            it.save()
            broadcast(function: 'dissociated', message: it)
        }


        def oldRank = feature.rank
        def id = feature.id

        p.removeFromFeatures(feature)

        //update rank on all features after that one
        p.features.each { it ->
            if (it.rank > oldRank) {
                it.rank = it.rank - 1
                it.save()
            }
        }
        broadcast(function: 'delete', message: [class: feature.class, id: id])
        resumeBufferedBroadcast()
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    void update(Feature feature) {
        feature.name = feature.name.trim()

        if (!feature.save(flush: true)) {
            throw new RuntimeException()
        }
        broadcast(function: 'update', message: feature)
        publishEvent(new IceScrumFeatureEvent(feature, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
    }

    @PreAuthorize('productOwner(#feature.backlog) and !archivedProduct(#feature.backlog)')
    def copyToBacklog(feature) {
        def story = new Story(
                name: feature.name,
                description: feature.description,
                suggestedDate: new Date(),
                acceptedDate: new Date(),
                state: Story.STATE_ACCEPTED,
                feature: feature,
                creator: (User)springSecurityService.currentUser,
                rank: (Story.countAllAcceptedOrEstimated(feature.backlog.id)?.list()[0] ?: 0) + 1,
                backlog: feature.backlog
        )
        if (!story.save()) {
            throw new RuntimeException(story.errors.toString())
        }
        broadcast(function: 'add', message: story)
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

            broadcast(function: 'update', message: feature)
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