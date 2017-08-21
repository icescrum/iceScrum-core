package org.icescrum.core.ui

import org.slf4j.LoggerFactory

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
 */

class WidgetDefinition {

    private final log = LoggerFactory.getLogger(this.class.name)

    boolean disabled
    boolean footer = false
    boolean settings = false
    boolean allowRemove = true
    boolean allowDuplicate = true

    int height = 1
    int width = 4

    String id
    String name
    String help = ''
    String icon = ''
    String title = ''
    String context = null
    String description = ''
    String pluginName = null
    String templatePath = null
    String ngController = null
    String secured = 'permitAll()'

    Closure onSave   = { def widget -> }
    Closure onUpdate = { def widget, settings -> }
    Closure onDelete = { def widget -> }

    Map options = [:]
    Map defaultSettings = [:]

    WidgetDefinition(String id, String pluginName, boolean disabled) {
        this.id = id
        this.disabled = disabled
        this.pluginName = pluginName
        this.name = "is.ui.widget.${id}.name"
        this.help = "is.ui.widget.${id}.help"
        this.description = "is.ui.widget.${id}.description"

        this.title = this.name
    }

    void icon(String icon) {
        this.icon = icon
    }

    void title(String title) {
        this.title = title
    }

    void height(int height) {
        this.height = height
    }

    void width(int width) {
        this.width = width
    }

    void context(String context) {
        this.context = context
    }

    void secured(String secured) {
        this.secured = secured
    }

    void onSave(Closure onSave) {
        this.onSave = onSave
    }

    void onUpdate(Closure onUpdate) {
        this.onUpdate = onUpdate
    }

    void onDelete(Closure onDelete) {
        this.onDelete = onDelete
    }

    void allowRemove(boolean allowRemove) {
        this.allowRemove = allowRemove
    }

    void ngController(String ngController) {
        this.ngController = ngController
    }

    void templatePath(String templatePath) {
        this.templatePath = templatePath
    }

    void allowDuplicate(boolean allowDuplicate) {
        this.allowDuplicate = allowDuplicate
    }

    def methodMissing(String name, args) {
        log.warn("The field $name is unrecognized for $id UI Widget definition")
    }

    def propertyMissing(String name, value) {
        this.options."$name" = value
        log.debug("The field $name is unrecognized for $id UI Widget definition added to options")
    }
}
