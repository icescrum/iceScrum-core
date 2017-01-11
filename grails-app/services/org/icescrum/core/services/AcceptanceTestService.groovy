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

import grails.transaction.Transactional
import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class AcceptanceTestService extends IceScrumEventPublisher {

    def springSecurityService
    def grailsApplication

    @PreAuthorize('inProject(#parentStory.backlog) and !archivedProject(#parentStory.backlog)')
    void save(AcceptanceTest acceptanceTest, Story parentStory, User user) {
        acceptanceTest.creator = user
        acceptanceTest.uid = AcceptanceTest.findNextUId(parentStory.backlog.id)
        acceptanceTest.parentStory = parentStory
        acceptanceTest.save()
        publishSynchronousEvent(IceScrumEventType.CREATE, acceptanceTest)
        def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        storyService.update(parentStory)
    }

    @PreAuthorize('inProject(#acceptanceTest.parentProject) and !archivedProject(#acceptanceTest.parentProject)')
    void update(AcceptanceTest acceptanceTest) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, acceptanceTest)
        acceptanceTest.save()
        publishSynchronousEvent(IceScrumEventType.UPDATE, acceptanceTest, dirtyProperties)
        def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        storyService.update(acceptanceTest.parentStory)
    }

    @PreAuthorize('inProject(#acceptanceTest.parentProject) and !archivedProject(#acceptanceTest.parentProject)')
    void delete(AcceptanceTest acceptanceTest) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, acceptanceTest)
        acceptanceTest.parentStory.removeFromAcceptanceTests(acceptanceTest)
        acceptanceTest.delete()
        publishSynchronousEvent(IceScrumEventType.DELETE, acceptanceTest, dirtyProperties)
    }

    def unMarshall(def acceptanceTestXml, def options) {
        Project project = options.project
        Story story = options.story
        AcceptanceTest.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def state = acceptanceTestXml."${'state'}".text()
                if (state) {
                    state = state.toInteger()
                } else {
                    state = (story.state < Story.STATE_DONE) ? AcceptanceTestState.TOCHECK.id : AcceptanceTestState.SUCCESS.id
                }
                def acceptanceTest = new AcceptanceTest(
                        name: acceptanceTestXml."${'name'}".text(),
                        description: acceptanceTestXml."${'description'}".text() ?: null,
                        state: state,
                        uid: acceptanceTestXml.@uid.text().toInteger()
                )
                // References on other objects
                if (project) {
                    def u = ((User) project.getAllUsers().find {
                        it.uid == acceptanceTestXml.creator.@uid.text()
                    }) ?: null
                    acceptanceTest.creator = (User) (u ?: project.productOwners.first())
                }
                if (story) {
                    story.addToAcceptanceTests(acceptanceTest)
                }
                // Save before some hibernate stuff
                if (options.save) {
                    acceptanceTest.save()
                }
                // Child objects
                options.acceptanceTest = acceptanceTest
                def activityService = (ActivityService) grailsApplication.mainContext.getBean('activityService')
                options.parent = acceptanceTest
                acceptanceTestXml.activities.activity.each { it ->
                    activityService.unMarshall(it, options)
                }
                options.parent = null
                if (options.save) {
                    acceptanceTest.save()
                }
                options.acceptanceTest = null
                return (AcceptanceTest) importDomainsPlugins(acceptanceTestXml, acceptanceTest, options)
            } catch (Exception e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
                throw new RuntimeException(e)
            }
        }
    }
}
