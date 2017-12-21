/*
 * Copyright (c) 2016 Kagilum SAS.
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
 *
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Widget
import org.icescrum.core.domain.Widget.WidgetParentType
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.error.BusinessException
import org.icescrum.core.ui.WidgetDefinition

@Transactional
class WidgetService {

    def uiDefinitionService
    def grailsApplication

    Widget save(WidgetDefinition widgetDefinition, WidgetParentType parentType, parent) {
        parent.refresh() // When the parent has just been created, the collections are not initialized yet + we want the size() to be up to date
        def widgetList = parent.widgets
        if (widgetList.find { it -> it.widgetDefinitionId == widgetDefinition.id } && !widgetDefinition.allowDuplicate) {
            throw new BusinessException(code: 'is.widget.error.duplicate')
        }
        Widget widget = new Widget(
                position: widgetList.size() + 1,
                widgetDefinitionId: widgetDefinition.id,
                settings: widgetDefinition.defaultSettings,
                parentType: parentType
        )
        widget.parent = parent
        try {
            widgetDefinition.onSave(widget)
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e
            } else {
                if (log.errorEnabled) log.error(e.message, e)
                throw new BusinessException(code: 'is.widget.error.save')
            }
        }
        widget.save(flush: true)
        return widget
    }

    void update(Widget widget, Map props) {
        if (props.position != widget.position) {
            updatePosition(widget, props.position)
        }
        try {
            uiDefinitionService.getWidgetDefinitionById(widget.widgetDefinitionId).onUpdate(widget, props.settings)
            if (props.settings) {
                widget.setSettings(props.settings)
            }
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e
            } else {
                if (log.errorEnabled) log.error(e.message, e)
                throw new BusinessException(code: 'is.widget.error.update')
            }
        }
        widget.save()
    }

    void initUserWidgets(User user) {
        save(uiDefinitionService.getWidgetDefinitionById('quickProjects'), WidgetParentType.USER, user.preferences)
        save(uiDefinitionService.getWidgetDefinitionById('tasks'), WidgetParentType.USER, user.preferences)
        save(uiDefinitionService.getWidgetDefinitionById('feed'), WidgetParentType.USER, user.preferences)
        Widget notesWidget = save(uiDefinitionService.getWidgetDefinitionById('notes'), WidgetParentType.USER, user.preferences)
        def noteProperties = notesWidget.properties.collectEntries { key, val -> [(key): val] }
        noteProperties.settings = [text: '']
        try { // Required because it will failed if no request (bootstraping)
            ApplicationTagLib g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
            noteProperties.settings.text = g.message(code: 'is.ui.widget.notes.default')
        } catch (Exception) {}
        update(notesWidget, noteProperties)
    }

    void delete(Widget widget) {
        def parent = widget.parent
        widget.delete(flush: true)
        cleanPositions(parent.widgets)
        try {
            uiDefinitionService.getWidgetDefinitionById(widget.widgetDefinitionId).onDelete(widget)
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e
            } else {
                if (log.errorEnabled) log.error(e.message, e)
                throw new BusinessException(code: 'is.widget.error.delete')
            }
        }
    }

    void delete(String type, long typeId) {
        def widgets = Widget.findAllByTypeAndTypeId(type, typeId)
        widgets.each { Widget widget ->
            delete(widget)
        }
    }

    private cleanPositions(Collection<Widget> widgets) {
        widgets.eachWithIndex { it, index ->
            it.position = index + 1
        }
    }

    private updatePosition(Widget widget, int newPosition) {
        def widgets = widget.parent.widgets
        cleanPositions(widgets) // Migration
        def oldPosition = widget.position ?: 1
        if (oldPosition > newPosition) {
            widgets.each { Widget it ->
                if (it.position >= newPosition && it.position <= oldPosition && it.id != widget.id) {
                    it.position++
                } else if (it.id == widget.id) {
                    it.position = newPosition
                }
            }
        } else if (oldPosition < newPosition) {
            widgets.each { Widget it ->
                if (it.position <= newPosition && it.position >= oldPosition && it.id != widget.id) {
                    it.position--
                } else if (it.id == widget.id) {
                    it.position = newPosition
                }
            }
        }
    }

    // BE CAREFUL: Only import user widgets
    def unMarshall(def widgetXml, def options) {
        Widget.withTransaction(readOnly: !options.save) { transaction ->
            def widget = new Widget(
                    parentType: WidgetParentType.USER,
                    position: widgetXml.position.toInteger(),
                    settingsData: widgetXml.settingsData.text() ?: null,
                    widgetDefinitionId: widgetXml.widgetDefinitionId.text()
            )
            // Reference on other object
            if (options.userPreferences) {
                UserPreferences userPreferences = options.userPreferences
                userPreferences.addToWidgets(widget)
                widget.userPreferences = options.userPreferences
            }
            if (options.save) {
                widget.save()
            }
            return (Widget) importDomainsPlugins(widgetXml, widget, options)
        }
    }
}
