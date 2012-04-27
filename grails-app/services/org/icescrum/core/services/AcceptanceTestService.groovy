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
import org.springframework.security.access.prepost.PreAuthorize
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Product
import org.icescrum.core.event.IceScrumAcceptanceTestEvent

class AcceptanceTestService {

    static transactional = true

    @PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void save(AcceptanceTest acceptanceTest, Story parentStory, User user) {
        acceptanceTest.creator = user
        acceptanceTest.uid = AcceptanceTest.findNextUId(parentStory.backlog.id)
        acceptanceTest.parentStory = parentStory
        parentStory.lastUpdated = new Date()
        if (!acceptanceTest.save(flush:true)) {
            throw new RuntimeException()
        }
        parentStory.addActivity(user, 'acceptanceTest', parentStory.name)
        publishEvent(new IceScrumAcceptanceTestEvent(acceptanceTest, this.class, user, IceScrumAcceptanceTestEvent.EVENT_CREATED))
        broadcast(function: 'add', message: acceptanceTest)
        broadcast(function: 'update', message: acceptanceTest.parentStory)
    }

    @PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void update(AcceptanceTest acceptanceTest, User user) {
        if (!acceptanceTest.save(flush:true)) {
            throw new RuntimeException()
        }
        publishEvent(new IceScrumAcceptanceTestEvent(acceptanceTest, this.class, user, IceScrumAcceptanceTestEvent.EVENT_UPDATED))
        broadcast(function: 'update', message: acceptanceTest)
        broadcast(function: 'update', message: acceptanceTest.parentStory)
    }

    @PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void delete(AcceptanceTest acceptanceTest) {
        def story = acceptanceTest.parentStory
        story.removeFromAcceptanceTests(acceptanceTest)
        acceptanceTest.delete()
        broadcast(function: 'delete', message: [class: acceptanceTest.class, id: acceptanceTest.id])
        broadcast(function: 'update', message: story)
    }

    def unMarshall(def acceptanceTest, Product product) {
        try {
            def at = new AcceptanceTest(
                name: acceptanceTest."${'name'}".text(),
                description: acceptanceTest."${'description'}".text(),
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
