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
package org.icescrum.core.services

import org.icescrum.core.ui.WidgetDefinition
import org.icescrum.core.ui.WindowDefinitionsBuilder

import java.util.concurrent.ConcurrentHashMap
import org.icescrum.core.ui.WidgetDefinitionsBuilder
import org.icescrum.core.ui.WindowDefinition

class UiDefinitionService {

    static transactional = false

    def pluginManager
    def grailsApplication

    private ConcurrentHashMap widgetsDefinitionsById
    private ConcurrentHashMap windowsDefinitionsById

    def loadDefinitions() {
        loadWindowsDefinitions()
        loadWidgetsDefinitions()
    }

    def loadWindowsDefinitions() {
        if (log.infoEnabled) { log.info "Loading UI Windows definitions..." }
        windowsDefinitionsById = new ConcurrentHashMap()
        grailsApplication.uiDefinitionClasses.each{
            def config = new ConfigSlurper().parse(it.clazz)
            def enabled = config.pluginName ? pluginManager.getUserPlugins().find{ it.name == config.pluginName && it.isEnabled() } : true
            enabled = enabled ? true : false

            def windows = config.windows
            if(windows instanceof Closure) {
                if (log.debugEnabled) { log.debug("Evaluating UI Windows definitions from $it.clazz.name") }
                def builder = new WindowDefinitionsBuilder(windowsDefinitionsById, !enabled)
                windows.delegate = builder
                windows.resolveStrategy = Closure.DELEGATE_FIRST
                windows()
            } else {
                log.warn("UI definitions file $it.clazz.name does not define any UI window definition")
            }
        }
    }

    def loadWidgetsDefinitions() {
        if (log.infoEnabled) { log.info "Loading UI Widgets definitions..." }
        widgetsDefinitionsById = new ConcurrentHashMap()
        grailsApplication.uiDefinitionClasses.each{
            def config = new ConfigSlurper().parse(it.clazz)
            def enabled = config.pluginName ? pluginManager.getUserPlugins().find{ it.name == config.pluginName && it.isEnabled() } : true
            enabled = enabled ? true : false

            def widgets = config.widgets
            if(widgets instanceof Closure) {
                if (log.debugEnabled) { log.debug("Evaluating UI widgets definitions from $it.clazz.name") }
                def builder = new WidgetDefinitionsBuilder(widgetsDefinitionsById, !enabled)
                widgets.delegate = builder
                widgets.resolveStrategy = Closure.DELEGATE_FIRST
                widgets()
            } else {
                log.warn("UI definitions file $it.clazz.name does not define any UI widget definition")
            }
        }
    }

    def reload() {
        if (log.infoEnabled) { log.info("Reloading UI Windows & Widgets definitions") }
        loadDefinitions()
    }

    WindowDefinition getWindowDefinitionById(String id) {
        if (windowsDefinitionsById[id] && !windowsDefinitionsById[id]?.disabled)
            windowsDefinitionsById[id]
        else
            null
    }

    def getWindowDefinitions() {
        windowsDefinitionsById.findAll { !it.value.disabled }
    }

    boolean hasWindowDefinition(String id) {
        windowsDefinitionsById.containsKey(id)
    }

    WidgetDefinition getWidgetDefinitionById(String id) {
        if (widgetsDefinitionsById[id] && !widgetsDefinitionsById[id]?.disabled)
            widgetsDefinitionsById[id]
        else
            null
    }

    def getWidgetDefinitions() {
        widgetsDefinitionsById.findAll { !it.value.disabled }
    }

    boolean hasWidgetDefinition(String id) {
        widgetsDefinitionsById.containsKey(id)
    }
}
