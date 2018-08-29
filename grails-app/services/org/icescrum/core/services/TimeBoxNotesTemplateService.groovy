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
            result << parseReleaseVariables(release, template.header)
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
            result << parseReleaseVariables(release, template.footer)
        }
        return result.toString()
    }

    @PreAuthorize('inProject(#sprint.parentRelease.parentProject)')
    def computeSprintNotes(Sprint sprint, TimeBoxNotesTemplate template) {
        def result = new StringBuffer()
        if (template.header) {
            result << parseSprintVariables(sprint, template.header)
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
            result << parseSprintVariables(sprint, template.footer)
        }
        return result.toString()
    }

    private static String computeTimeBoxNotesSection(Collection stories, def config) {
        if (!stories) {
            return ""
        } else {
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
        def binding = getProjectBinding(story.backlog) + getReleaseBinding(story.parentSprint.parentRelease) + getSprintBinding(story.parentSprint) + getStoryBinding(story)
        try {
            return simple.createTemplate(value).make(binding).toString()
        } catch (Exception e) {
            return value
        }
    }

    private static String parseSprintVariables(Sprint sprint, String value) {
        def simple = new SimpleTemplateEngine()
        def binding = getProjectBinding(sprint.parentProject) + getReleaseBinding(sprint.parentRelease) + getSprintBinding(sprint)
        try {
            return simple.createTemplate(value).make(binding).toString()
        } catch (Exception e) {
            return value
        }
    }

    private static String parseReleaseVariables(Release release, String value) {
        def simple = new SimpleTemplateEngine()
        def binding = getProjectBinding(release.parentProject) + getReleaseBinding(release)
        try {
            return simple.createTemplate(value).make(binding).toString()
        } catch (Exception e) {
            return value
        }
    }

    private static Map getProjectBinding(Project project) {
        return [
                serverUrl: ApplicationSupport.serverURL(),
                baseUrl  : ApplicationSupport.serverURL() + '/' + project.pkey,
                project  : [id         : project.id,
                            name       : project.name,
                            pkey       : project.pkey,
                            description: project.description,
                            startDate  : project.startDate,
                            endDate    : project.endDate]
        ]
    }

    private static Map getReleaseBinding(Release release) {
        return [
                release: [id         : release.id,
                          name       : release.name,
                          startDate  : release.startDate,
                          endDate    : release.endDate,
                          orderNumber: release.orderNumber]
        ]
    }

    private static Map getSprintBinding(Sprint sprint) {
        return [
                sprint: [id              : sprint.id,
                         goal            : sprint.goal,
                         startDate       : sprint.startDate,
                         velocity        : sprint.velocity,
                         capacity        : sprint.capacity,
                         endDate         : sprint.endDate,
                         deliveredVersion: sprint.deliveredVersion,
                         orderNumber     : sprint.orderNumber,
                         index           : sprint.index]
        ]
    }

    private static Map getStoryBinding(Story story) {
        return [
                story: [id            : story.uid,
                        name          : story.name,
                        description   : story.description,
                        notes         : story.notes,
                        origin        : story.origin,
                        effort        : story.effort,
                        rank          : story.rank,
                        affectVersion : story.affectVersion,
                        frozenDate    : story.frozenDate,
                        suggestedDate : story.suggestedDate,
                        acceptedDate  : story.acceptedDate,
                        plannedDate   : story.plannedDate,
                        estimatedDate : story.estimatedDate,
                        inProgressDate: story.inProgressDate,
                        inReviewDate  : story.inReviewDate,
                        doneDate      : story.doneDate,
                        comments      : story.comments]
        ]
    }
}
