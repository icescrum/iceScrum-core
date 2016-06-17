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
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Product
import org.springframework.transaction.annotation.Transactional

@Transactional
class AcceptanceTestService extends IceScrumEventPublisher {

    def springSecurityService
    def grailsApplication

    @PreAuthorize('inProduct(#parentStory.backlog) and !archivedProduct(#parentStory.backlog)')
    void save(AcceptanceTest acceptanceTest, Story parentStory, User user) {
        acceptanceTest.creator = user
        acceptanceTest.uid = AcceptanceTest.findNextUId(parentStory.backlog.id)
        acceptanceTest.parentStory = parentStory
        acceptanceTest.save()
        publishSynchronousEvent(IceScrumEventType.CREATE, acceptanceTest)
        def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        storyService.update(parentStory)
    }

    //TODO Fix security on this
    //@PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void update(AcceptanceTest acceptanceTest) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, acceptanceTest)
        acceptanceTest.save()
        publishSynchronousEvent(IceScrumEventType.UPDATE, acceptanceTest, dirtyProperties)
        def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        storyService.update(acceptanceTest.parentStory)
    }

    @PreAuthorize('inProduct(#acceptanceTest.parentProduct) and !archivedProduct(#acceptanceTest.parentProduct)')
    void delete(AcceptanceTest acceptanceTest) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, acceptanceTest)
        acceptanceTest.parentStory.removeFromAcceptanceTests(acceptanceTest)
        acceptanceTest.delete()
        publishSynchronousEvent(IceScrumEventType.DELETE, acceptanceTest, dirtyProperties)
    }

    def unMarshall(def acceptanceTest, Product product, Story story) {
        try {
            def state = acceptanceTest."${'state'}".text()
            if (state) {
                state = state.toInteger()
            } else {
                state = (story.state < Story.STATE_DONE) ? AcceptanceTestState.TOCHECK.id : AcceptanceTestState.SUCCESS.id
            }
            def at = new AcceptanceTest(
                name: acceptanceTest."${'name'}".text(),
                description: acceptanceTest."${'description'}".text(),
                state: state,
                uid: acceptanceTest.@uid.text().toInteger()
            )
            if (product) {
                def u = ((User) product.getAllUsers().find { it.uid == acceptanceTest.creator.@uid.text() }) ?: null
                at.creator = u ?: product.productOwners.first()
            }
            return at

        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }
}
