/*
 * Copyright (c) 2016 Kagilum SAS
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
 * Vincent Barrier (vbarrier@kagilum.com)
 *
 */
package org.icescrum.core.services

import grails.converters.JSON
import grails.transaction.Transactional
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Template
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

@Transactional
class TemplateService extends IceScrumEventPublisher {

    void save(Template template, Story story) {
        def copyFields = { source, fieldNames -> // Custom marshalling
            def copy = [:]
            fieldNames.each { fieldName ->
                def fieldValue = source."$fieldName"
                if (fieldValue != null) {
                    copy[fieldName] = fieldValue.hasProperty('id') ? [id: fieldValue.id] : fieldValue
                }
            }
            return copy
        }
        def storyData = copyFields(story, ['affectVersion', 'description', 'notes', 'tags', 'type', 'dependsOn', 'feature'])
        if (story.tasks) {
            storyData.tasks = story.tasks.collect { task ->
                copyFields(task, ['color', 'description', 'estimation', 'name', 'notes', 'tags', 'type'])
            }
        }
        if (story.acceptanceTests) {
            storyData.acceptanceTests = story.acceptanceTests.collect { acceptanceTest ->
                copyFields(acceptanceTest, ['description', 'name'])
            }
        }
        template.itemClass = story.class.name
        template.serializedData = (storyData as JSON).toString()
        template.parentProduct = story.backlog
        template.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.CREATE, template)
    }

    void delete(Template template) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, template)
        template.delete(flush: true)
        publishSynchronousEvent(IceScrumEventType.DELETE, template, dirtyProperties)
    }

    def unMarshall(def templateXml, def options) {
        def product = options.product
        Template.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def template = new Template(
                        serializedData: templateXml.serializedData.text(),
                        itemClass: templateXml.itemClass.text())
                // Reference on other object
                if (product) {
                    template.parentProduct = product
                }
                if (options.save) {
                    template.save()
                }
                return (Template) importDomainsPlugins(template, options)
            } catch (Exception e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
                throw new RuntimeException(e)
            }
        }
    }
}
