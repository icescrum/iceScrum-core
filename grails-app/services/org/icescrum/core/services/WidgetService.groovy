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
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.error.BusinessException
import org.icescrum.core.ui.WidgetDefinition

@Transactional
class WidgetService {

    def uiDefinitionService
    def grailsApplication

    Widget save(User user, WidgetDefinition widgetDefinition) {
        int duplicate = Widget.countByUserPreferencesAndWidgetDefinitionId(user.preferences, widgetDefinition.id)
        if (duplicate && !widgetDefinition.allowDuplicate) {
            throw new BusinessException(code: 'is.widget.error.duplicate')
        }
        Widget widget = new Widget(position: Widget.countByUserPreferences(user.preferences) + 1, widgetDefinitionId: widgetDefinition.id, userPreferences: user.preferences, settings: widgetDefinition.defaultSettings)
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
        user.lastUpdated = new Date()
        user.save()
        return widget
    }

    void update(Widget widget, Map props) {
        User user = widget.userPreferences.user
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
        user.lastUpdated = new Date()
        user.save()
    }

    void initUserWidgets(User user) {
        save(user, uiDefinitionService.getWidgetDefinitionById('quickProjects'))
        save(user, uiDefinitionService.getWidgetDefinitionById('tasks'))
        save(user, uiDefinitionService.getWidgetDefinitionById('feed'))
        Widget notesWidget = save(user, uiDefinitionService.getWidgetDefinitionById('notes'))
        def noteProperties = notesWidget.properties.collectEntries { key, val -> [(key): val] }
        noteProperties.settings = [text: '']
        try { // Required because it will failed if no request (bootstraping)
            ApplicationTagLib g = grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ApplicationTagLib')
            noteProperties.settings.text = g.message(code: 'is.ui.widget.notes.default')
        } catch (Exception) {}
        update(notesWidget, noteProperties)
    }

    void delete(Widget widget) {
        UserPreferences userPreferences = widget.userPreferences
        widget.delete(flush: true)
        cleanPositions(userPreferences.widgets)
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
        User user = userPreferences.user
        user.lastUpdated = new Date()
        user.save()
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
        def widgets = widget.userPreferences.widgets
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
        widget.userPreferences.user.lastUpdated = new Date()
        widget.userPreferences.user.save()
    }

    def unMarshall(def widgetXml, def options) {
        Widget.withTransaction(readOnly: !options.save) { transaction ->
            def widget = new Widget(
                    position: widgetXml.position.toInteger(),
                    settingsData: widgetXml.settingsData.text() ?: null,
                    widgetDefinitionId: widgetXml.widgetDefinitionId.text())
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
