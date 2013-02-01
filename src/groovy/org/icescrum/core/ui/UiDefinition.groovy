package org.icescrum.core.ui

import org.slf4j.LoggerFactory

/*
 * Copyright (c) 2012 Kagilum SAS
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
class UiDefinition {

    private final log = LoggerFactory.getLogger(this.class.name)

    boolean disabled
    String id
    MenuBarDefinition menuBar
    WidgetDefinition widget
    WindowDefinition window
    def shortcuts
    def options = [:]

    UiDefinition(String id, boolean disabled) {
        this.id = id
        this.disabled = disabled
    }
    
    void menuBar(Closure menuBarClosure) {
        MenuBarDefinition menuBar = new MenuBarDefinition()
        menuBarClosure.delegate = menuBar
        menuBarClosure.resolveStrategy = Closure.DELEGATE_FIRST
        menuBarClosure()
        this.menuBar = menuBar
    }

    void widget(Closure widgetClosure) {
        WidgetDefinition widget = new WidgetDefinition()
        widgetClosure.delegate = widget
        widgetClosure.resolveStrategy = Closure.DELEGATE_FIRST
        widgetClosure()
        this.widget = widget
    }
    
    void window(Closure windowClosure) {
        WindowDefinition window = new WindowDefinition()
        windowClosure.delegate = window
        windowClosure.resolveStrategy = Closure.DELEGATE_FIRST
        windowClosure()
        this.window = window
    }
    
    def methodMissing(String name, args) {
        log.warn("The field $name is unrecognized for $id UI definition")
    }

    def propertyMissing(String name, value){
        this.options."$name" = value
        log.debug("The field $name is unrecognized for $id UI definition added to options")
    }
}
