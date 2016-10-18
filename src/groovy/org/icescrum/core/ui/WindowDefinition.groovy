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
class WindowDefinition {

    private final log = LoggerFactory.getLogger(this.class.name)

    boolean disabled
    boolean flex = true
    boolean details = false
    boolean printable = true
    boolean fullScreen = true

    String id
    String icon = ''
    String help = ''
    String title = ''
    String pluginName = null
    String templatePath = null
    String context = "product"
    String secured = 'permitAll()'

    def options = [:]
    def exportFormats = []

    MenuDefinition menu
    Closure before = null

    WindowDefinition(String id, String pluginName, boolean disabled) {
        this.id = id
        this.disabled = disabled
        this.pluginName = pluginName
    }
    
    void menu(Closure menuClosure) {
        MenuDefinition menu = new MenuDefinition()
        menuClosure.delegate = menu
        menuClosure.resolveStrategy = Closure.DELEGATE_FIRST
        menuClosure()
        this.menu = menu
        this.menu.title = this.title
    }

    void icon(String icon) {
        this.icon = icon
    }

    void title(String title) {
        this.title = title
    }

    void context(String context) {
        this.context = context
    }

    void secured(String secured) {
        this.secured = secured
    }

    void templatePath(String templatePath) {
        this.templatePath = templatePath
    }

    void details(boolean details) {
        this.details = details
    }

    void flex(boolean flex) {
        this.flex = flex
    }

    void help(String help) {
        this.help = help
    }

    void before(Closure before) {
        this.before = before
    }

    void fullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen
    }

    void printable(boolean printable) {
        this.printable = printable
    }
    
    def methodMissing(String name, args) {
        log.warn("The field $name is unrecognized for $id UI definition")
    }

    def propertyMissing(String name, value){
        this.options."$name" = value
        log.debug("The field $name is unrecognized for $id UI definition added to options")
    }
}
