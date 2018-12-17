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
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class AcceptanceTestService extends IceScrumEventPublisher {

    def grailsApplication

    @PreAuthorize('inProject(#parentStory.backlog) and !archivedProject(#parentStory.backlog)')
    void save(AcceptanceTest acceptanceTest, Story parentStory, User user) {
        acceptanceTest.creator = user
        acceptanceTest.uid = AcceptanceTest.findNextUId(parentStory.backlog.id)
        acceptanceTest.parentStory = parentStory
        acceptanceTest.rank = AcceptanceTest.countByParentStory(parentStory) + 1
        acceptanceTest.save()
        publishSynchronousEvent(IceScrumEventType.CREATE, acceptanceTest)
        parentStory.addToAcceptanceTests(acceptanceTest) // Required otherwise the AT is not seen attached on the story immediately & not taken into account for JSON or delete
        def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        storyService.update(parentStory)
    }

    @PreAuthorize('inProject(#acceptanceTest.parentProject) and !archivedProject(#acceptanceTest.parentProject)')
    void update(AcceptanceTest acceptanceTest, props = [:]) {
        if (props.rank != null) {
            updateRank(acceptanceTest, props.rank)
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, acceptanceTest)
        acceptanceTest.save()
        publishSynchronousEvent(IceScrumEventType.UPDATE, acceptanceTest, dirtyProperties)
        def storyService = (StoryService) grailsApplication.mainContext.getBean('storyService')
        storyService.update(acceptanceTest.parentStory)
    }

    @PreAuthorize('inProject(#acceptanceTest.parentProject) and !archivedProject(#acceptanceTest.parentProject)')
    void delete(AcceptanceTest acceptanceTest) {
        def story = acceptanceTest.parentStory
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, acceptanceTest)
        dirtyProperties.parentProject = story.project
        resetRank(acceptanceTest)
        story.removeFromAcceptanceTests(acceptanceTest)
        acceptanceTest.delete()
        publishSynchronousEvent(IceScrumEventType.DELETE, acceptanceTest, dirtyProperties)
    }

    @PreAuthorize('inProject(#acceptanceTest.parentProject) and !archivedProject(#acceptanceTest.parentProject)')
    def copy(AcceptanceTest acceptanceTest, User creator) {
        def clonedAcceptanceTest = new AcceptanceTest(name: acceptanceTest.name, description: acceptanceTest.description)
        save(clonedAcceptanceTest, acceptanceTest.parentStory, creator)
        return clonedAcceptanceTest
    }

    private void resetRank(AcceptanceTest acceptanceTest) {
        def story = acceptanceTest.parentStory
        cleanRanks(story)
        story.acceptanceTests.findAll {
            it.rank > acceptanceTest.rank
        }.each {
            it.rank--
            it.save()
        }
    }

    private void updateRank(AcceptanceTest acceptanceTest, int newRank) {
        def story = acceptanceTest.parentStory
        cleanRanks(story)
        Range affectedRange = acceptanceTest.rank..newRank
        int delta = affectedRange.isReverse() ? 1 : -1
        story.acceptanceTests.findAll {
            it != acceptanceTest && it.rank in affectedRange
        }.each {
            it.rank += delta
            it.save()
        }
        acceptanceTest.rank = newRank
    }

    private void cleanRanks(Story story) {
        def acceptanceTests = story.acceptanceTests.sort { a, b -> a.rank <=> b.rank ?: a.uid <=> b.uid }
        def error = false
        for (int i = 0; i < acceptanceTests.size() && !error; i++) {
            error = acceptanceTests[i].rank != (i + 1)
        }
        if (error) {
            acceptanceTests.eachWithIndex { acceptanceTest, ind ->
                if (acceptanceTest.rank != ind + 1) {
                    acceptanceTest.rank = ind + 1
                    acceptanceTest.save()
                }
            }
        }
    }

    def unMarshall(def acceptanceTestXml, def options) {
        Project project = options.project
        Story story = options.story
        AcceptanceTest.withTransaction(readOnly: !options.save) { transaction ->
            User creator = project ? project.getUserByUidOrOwner(acceptanceTestXml.creator.@uid.text()) : null
            def acceptanceTest = new AcceptanceTest(
                    name: acceptanceTestXml."${'name'}".text(),
                    description: acceptanceTestXml.description.text() ?: null,
                    state: acceptanceTestXml.state.text().toInteger(),
                    uid: acceptanceTestXml.@uid.text().toInteger(),
                    rank: (acceptanceTestXml.rank.text().isNumber()) ? acceptanceTestXml.rank.text().toInteger() : null,
            )
            // References on other objects
            if (project) {
                acceptanceTest.creator = creator
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
        }
    }
}
