/*
 * Copyright (c) 2011 Kagilum SAS
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.services

import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState
import org.springframework.security.access.prepost.PreAuthorize
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Product
import org.icescrum.core.event.IceScrumAcceptanceTestEvent

class AcceptanceTestService {

    static transactional = true

    def springSecurityService

    @PreAuthorize('inProduct(#parentStory.backlog) and !archivedProduct(#parentStory.backlog)')
    void save(AcceptanceTest acceptanceTest, Story parentStory, User user) {

        acceptanceTest.creator = user
        acceptanceTest.uid = AcceptanceTest.findNextUId(parentStory.backlog.id)
        acceptanceTest.parentStory = parentStory
        parentStory.lastUpdated = new Date()

        if (!acceptanceTest.save()) {
            throw new RuntimeException()
        }

        acceptanceTest.addActivity(user, 'acceptanceTestSave', acceptanceTest.name)
        publishEvent(new IceScrumAcceptanceTestEvent(acceptanceTest, this.class, user, IceScrumAcceptanceTestEvent.EVENT_CREATED))

        def channel = 'product-' + parentStory.backlog.id
        bufferBroadcast(channel:channel)
        broadcast(function: 'add', message: acceptanceTest, channel: channel)
        broadcast(function: 'update', message: parentStory, channel: channel)
        resumeBufferedBroadcast(channel:channel)
    }

    @PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void update(AcceptanceTest acceptanceTest, User user, boolean stateChanged) {

        def parentStory = acceptanceTest.parentStory
        parentStory.lastUpdated = new Date()

        if (!acceptanceTest.save()) {
            throw new RuntimeException()
        }

        def activityType = 'acceptanceTest' + (stateChanged ? acceptanceTest.stateEnum.name().toLowerCase().capitalize() : 'Update')
        acceptanceTest.addActivity(user, activityType, acceptanceTest.name)
        publishEvent(new IceScrumAcceptanceTestEvent(acceptanceTest, this.class, user, IceScrumAcceptanceTestEvent.EVENT_UPDATED))

        def channel = 'product-' + parentStory.backlog.id
        bufferBroadcast(channel:channel)
        broadcast(function: 'update', message: acceptanceTest, channel: channel)
        broadcast(function: 'update', message: parentStory, channel: channel)
        resumeBufferedBroadcast(channel:channel)
    }

    @PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void delete(AcceptanceTest acceptanceTest) {

        def parentStory = acceptanceTest.parentStory
        parentStory.removeFromAcceptanceTests(acceptanceTest)
        acceptanceTest.delete()

        parentStory.addActivity(springSecurityService.currentUser, 'acceptanceTestDelete', acceptanceTest.name)

        def channel = 'product-' + parentStory.backlog.id
        bufferBroadcast(channel:channel)
        broadcast(function: 'delete', message: [class: acceptanceTest.class, id: acceptanceTest.id], channel: channel)
        broadcast(function: 'update', message: parentStory, channel: channel)
        resumeBufferedBroadcast(channel:channel)
    }

    def unMarshall(def acceptanceTest, Product product) {
        try {
            def at = new AcceptanceTest(
                name: acceptanceTest."${'name'}".text(),
                description: acceptanceTest."${'description'}".text(),
                state: acceptanceTest."${'state'}".text().toInteger(),
                uid: acceptanceTest.@uid.text().toInteger()
            )
            if (product) {
                def u = ((User) product.getAllUsers().find { it.uid == acceptanceTest.creator.@uid.text() } ) ?: null
                at.creator = u ?:  product.productOwners.first()
            }
            return at

        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }
}
