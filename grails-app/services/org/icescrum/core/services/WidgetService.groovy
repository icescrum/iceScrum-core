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

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Widget
import org.icescrum.core.ui.WidgetDefinition
import org.springframework.transaction.annotation.Transactional

@Transactional
class WidgetService {

    def uiDefinitionService

    Widget save(User user, WidgetDefinition widgetDefinition, boolean onRight) {
        int duplicate = Widget.countByUserPreferencesAndWidgetDefinitionId(user.preferences, widgetDefinition.id)
        if (duplicate && !widgetDefinition.allowDuplicate) {
            throw new RuntimeException()
        }
        int count = Widget.countByUserPreferencesAndOnRight(user.preferences, onRight)
        Widget widget = new Widget(position: count + 1, widgetDefinitionId: widgetDefinition.id, userPreferences: user.preferences, settings: widgetDefinition.defaultSettings, onRight: onRight)
        try {
            widgetDefinition.onSave(widget)
        } catch (Exception e) {
            throw new RuntimeException()
        }
        if (!widget.save(flush: true)) {
            throw new RuntimeException()
        }
        user.lastUpdated = new Date()
        if (!user.save()) {
            throw new RuntimeException()
        }
        return widget
    }

    void update(Widget widget, Map props) {

        User user = widget.userPreferences.user
        if (props.position != widget.position || props.onRight != widget.onRight) {
            updatePosition(widget, props.position, props.onRight)
        }
        try {
            uiDefinitionService.getWidgetDefinitionById(widget.widgetDefinitionId).onUpdate(widget, props.settings)
            if (props.settings) {
                widget.setSettings(props.settings)
            }
        } catch (Exception e) {
            log.debug(e)
            throw new RuntimeException()
        }
        if (!widget.save()) {
            throw new RuntimeException()
        }
        user.lastUpdated = new Date()
        if (!user.save()) {
            throw new RuntimeException()
        }
    }

    void delete(Widget widget) {
        User user = widget.userPreferences.user
        widget.delete()
        Widget.findAllByOnRightAndUserPreferences(widget.onRight, widget.userPreferences, [sort: 'position'])?.eachWithIndex { it, index ->
            it.position = index + 1
        }
        try {
            uiDefinitionService.getWidgetDefinitionById(widget.widgetDefinitionId).onDelete(widget)
        } catch (Exception e) {
            throw new RuntimeException()
        }
        user.lastUpdated = new Date()
        if (!user.save()) {
            throw new RuntimeException()
        }
    }

    private updatePosition(Widget widget, int position, boolean onRight) {
        def currentWidgets
        if (onRight) {
            currentWidgets = Widget.findAllByOnRightAndUserPreferences(true, widget.userPreferences)
            if (!currentWidgets.contains(widget)) {
                widget.onRight = onRight
                widget.position = currentWidgets.size() + 1
                def widgetsOnLeft = Widget.findAllByOnRightAndUserPreferences(false, widget.userPreferences)
                if (widgetsOnLeft.contains(widget)) {
                    updatePosition(widget, widgetsOnLeft.size() - 1, false)
                }
            }
        } else {
            currentWidgets = Widget.findAllByOnRightAndUserPreferences(false, widget.userPreferences)
            if (!currentWidgets.contains(widget)) {
                widget.onRight = onRight
                widget.position = currentWidgets.size() + 1
                def widgetsOnRight = Widget.findAllByOnRightAndUserPreferences(true, widget.userPreferences)
                if (widgetsOnRight.contains(widget)) {
                    updatePosition(widget, widgetsOnRight.size() - 1, true)
                }
            }
        }
        def from = widget.position
        from = from ?: 1
        def to = position
        if (from != to) {
            if (from > to) {
                currentWidgets.each { Widget it ->
                    if (it.position >= to && it.position <= from && it.id != widget.id) {
                        it.position++
                    } else if (it.id == widget.id) {
                        it.position = position
                    }
                }
            } else {
                currentWidgets.each { Widget it ->
                    if (it.position <= to && it.position >= from && it.id != widget.id) {
                        it.position--
                    } else if (it.id == widget.id) {
                        it.position = position
                    }
                }
            }
        }
        widget.userPreferences.user.lastUpdated = new Date()
        if (!widget.userPreferences.user.save()) {
            throw new RuntimeException()
        }
    }
}
