/*
 * Copyright (c) 2017 Kagilum SAS
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
 * Colin Bontemps (cbontemps@kagilum.com)
 */
package org.icescrum.core.services

import grails.transaction.Transactional
import groovy.text.SimpleTemplateEngine
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.TimeBoxNotesTemplate
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize

//TODO: implement event publisher
@Transactional
class TimeBoxNotesTemplateService extends IceScrumEventPublisher {

    @PreAuthorize('inProject(#template.parentProject)')
    void delete(TimeBoxNotesTemplate template) {
        template.delete()
    }

    @PreAuthorize('inProject(#template.parentProject)')
    void update(TimeBoxNotesTemplate template) {
        template.save(flush: true)
    }

    @PreAuthorize('inProject(#release.parentProject)')
    def computeReleaseNotes(Release release, TimeBoxNotesTemplate template) {

        def result = new StringBuffer()
        if (template.header) {
            result << template.header
            result << '\n'
        }

        template.configs.each { config ->
            if (config.header) {
                result << config.header
                result << '\n'
            }
            Closure tagCondition = {!(config.storyTags) || it.tags.intersect(config.storyTags)}
            Closure typeCondition = {!(config.containsKey('storyType')) || (config.storyType == it.type)}
            def allStories = release.sprints*.stories.flatten()
            def filteredStories = allStories.findAll {tagCondition(it) && typeCondition(it)}

            if (filteredStories) {
                filteredStories.each { story ->
                    result << parseStoryVariables(story, config.lineTemplate)
                    result << '\n'
                }
            }
            if (config.footer) {
                result << config.footer
                result << '\n'
            }
        }
        result.append(template.footer)
        return result.toString()
    }

    @PreAuthorize('inProject(#project)')
    void save(TimeBoxNotesTemplate template, Project project) {
        template.parentProject = project
        template.save(flush: true)
        project.addToTimeBoxNotesTemplates(template) // TODO : does not requires save ???
        publishSynchronousEvent(IceScrumEventType.CREATE, template)
    }

    private static String parseStoryVariables(Story story, String value) {
        def simple = new SimpleTemplateEngine()
        def binding = [project  : [
                id         : story.backlog.id,
                name       : story.backlog.name,
                pkey       : story.backlog.pkey,
                description: story.backlog.description,
                startDate  : story.backlog.startDate,
                endDate    : story.backlog.endDate
        ], story                : [id            : story.uid,
                                   name          : story.name,
                                   description   : story.description,
                                   notes         : story.notes,
                                   origin        : story.origin,
                                   effort        : story.effort,
                                   rank          : story.rank,
                                   affectVersion : story.affectVersion,
                                   suggestedDate : story.suggestedDate,
                                   acceptedDate  : story.acceptedDate,
                                   plannedDate   : story.plannedDate,
                                   estimatedDate : story.estimatedDate,
                                   inProgressDate: story.inProgressDate,
                                   doneDate      : story.doneDate,
                                   comments      : story.comments
        ], sprint               : story.parentSprint ? [goal            : story.parentSprint.goal,
                                                        startDate       : story.parentSprint.startDate,
                                                        velocity        : story.parentSprint.velocity,
                                                        capacity        : story.parentSprint.capacity,
                                                        endDate         : story.parentSprint.endDate,
                                                        deliveredVersion: story.parentSprint.deliveredVersion,
                                                        orderNumber     : story.parentSprint.orderNumber,
                                                        index           : story.parentSprint.index] : null
                       , release: story.parentSprint?.parentRelease ? [name       : story.parentSprint.parentRelease.name,
                                                                       startDate  : story.parentSprint.parentRelease.startDate,
                                                                       endDate    : story.parentSprint.parentRelease.endDate,
                                                                       orderNumber: story.parentSprint.parentRelease.orderNumber] : null]
        try {
            return simple.createTemplate(value).make(binding).toString()
        } catch (Exception e) {
            return value
        }
    }


}
