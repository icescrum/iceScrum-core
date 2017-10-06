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
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport
import org.springframework.security.access.prepost.PreAuthorize

//TODO: implement event publisher
@Transactional
class TimeBoxNotesTemplateService {

    @PreAuthorize('inProject(#project)')
    void save(TimeBoxNotesTemplate template, Project project) {
        template.parentProject = project
        template.save(flush: true)
        project.addToTimeBoxNotesTemplates(template)
    }

    @PreAuthorize('inProject(#template.parentProject)')
    void update(TimeBoxNotesTemplate template) {
        template.save(flush: true)
    }

    @PreAuthorize('inProject(#template.parentProject)')
    void delete(TimeBoxNotesTemplate template) {
        template.delete()
    }

    @PreAuthorize('inProject(#release.parentProject)')
    def computeReleaseNotes(Release release, TimeBoxNotesTemplate template) {
        def result = new StringBuffer()
        if (template.header) {
            result << template.header
            result << '\n'
        }
        template.configs.each { config ->
            // Filter stories
            Closure tagCondition = { !(config.storyTags) || it.tags.intersect(config.storyTags) }
            Closure typeCondition = { !(config.containsKey('storyType')) || (config.storyType == it.type) }
            def allStories = release.sprints*.stories.flatten()
            def filteredStories = allStories.findAll { it.state == Story.STATE_DONE && tagCondition(it) && typeCondition(it) }
            result << computeTimeBoxNotesSection(filteredStories, config)
        }
        if (template.footer) {
            result.append(template.footer)
        }
        return result.toString()
    }

    @PreAuthorize('inProject(#sprint.parentRelease.parentProject)')
    def computeSprintNotes(Sprint sprint, TimeBoxNotesTemplate template) {
        def result = new StringBuffer()
        if (template.header) {
            result << template.header
            result << '\n'
        }
        template.configs.each { config ->
            // Filter stories
            Closure tagCondition = { !(config.storyTags) || it.tags.intersect(config.storyTags) }
            Closure typeCondition = { !(config.containsKey('storyType')) || (config.storyType == it.type) }
            def filteredStories = sprint.stories.findAll { it.state == Story.STATE_DONE && tagCondition(it) && typeCondition(it) }
            result << computeTimeBoxNotesSection(filteredStories, config)
        }
        if (template.footer) {
            result.append(template.footer)
        }
        return result.toString()
    }

    private static String computeTimeBoxNotesSection(Collection stories, def config) {
        def result = new StringBuffer()
        if (config.header) {
            result << config.header
            result << '\n'
        }
        if (config.lineTemplate) {
            stories?.each { story ->
                result << parseStoryVariables(story, config.lineTemplate)
                result << '\n'
            }
        }
        if (config.footer) {
            result << config.footer
            result << '\n'
        }
        return result.toString()
    }

    def unMarshall(def timeBoxNotesTemplateXml, def options) {
        Project project = options.project
        TimeBoxNotesTemplate.withTransaction(readOnly: !options.save) { transaction ->
            def timeBoxNotesTemplate = new TimeBoxNotesTemplate(
                    name: timeBoxNotesTemplateXml.name.text(),
                    header: timeBoxNotesTemplateXml.header.text(),
                    footer: timeBoxNotesTemplateXml.footer.text(),
                    configsData: timeBoxNotesTemplateXml.configsData.text()
            )
            if (project) {
                timeBoxNotesTemplate.parentProject = project
                project.addToTimeBoxNotesTemplates(timeBoxNotesTemplate)
            }
            if (options.save) {
                timeBoxNotesTemplate.save()
            }
            return (TimeBoxNotesTemplate) importDomainsPlugins(timeBoxNotesTemplateXml, timeBoxNotesTemplate, options)
        }
    }

    private static String parseStoryVariables(Story story, String value) {
        def simple = new SimpleTemplateEngine()
        def binding = [
                serverUrl: ApplicationSupport.serverURL(),
                baseUrl  : ApplicationSupport.serverURL() + '/' + story.backlog.pkey,
                project  : [id         : story.backlog.id,
                            name       : story.backlog.name,
                            pkey       : story.backlog.pkey,
                            description: story.backlog.description,
                            startDate  : story.backlog.startDate,
                            endDate    : story.backlog.endDate],
                story    : [id            : story.uid,
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
                            comments      : story.comments],
                sprint   : [id              : story.parentSprint.id,
                            goal            : story.parentSprint.goal,
                            startDate       : story.parentSprint.startDate,
                            velocity        : story.parentSprint.velocity,
                            capacity        : story.parentSprint.capacity,
                            endDate         : story.parentSprint.endDate,
                            deliveredVersion: story.parentSprint.deliveredVersion,
                            orderNumber     : story.parentSprint.orderNumber,
                            index           : story.parentSprint.index],
                release  : [id         : story.parentSprint.parentRelease.id,
                            name       : story.parentSprint.parentRelease.name,
                            startDate  : story.parentSprint.parentRelease.startDate,
                            endDate    : story.parentSprint.parentRelease.endDate,
                            orderNumber: story.parentSprint.parentRelease.orderNumber]]
        try {
            return simple.createTemplate(value).make(binding).toString()
        } catch (Exception e) {
            return value
        }
    }
}
