package org.icescrum.core.ui

import org.slf4j.LoggerFactory
import java.util.concurrent.ConcurrentHashMap

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
class WindowDefinitionsBuilder {

    private final log = LoggerFactory.getLogger(this.class.name)

    private boolean disabled = false
    private String pluginName = null
    private ConcurrentHashMap windowsDefinitionsById

    WindowDefinitionsBuilder(ConcurrentHashMap windowsDefinitionsById, String pluginName, boolean disabled) {
        this.disabled = disabled
        this.pluginName = pluginName
        this.windowsDefinitionsById = windowsDefinitionsById
    }

    def invokeMethod(String name, args) {
        if (args.size() == 1 && args[0] instanceof Closure) {
            def definitionClosure = args[0]
            WindowDefinition windowDefinition = new WindowDefinition(name, pluginName, disabled)
            definitionClosure.delegate = windowDefinition
            definitionClosure.resolveStrategy = Closure.DELEGATE_FIRST
            definitionClosure()
            if(windowsDefinitionsById[name]) {
                log.warn("UI window definition for $name will be overriden")
            }
            windowsDefinitionsById[name] = windowDefinition
            if (log.debugEnabled) { log.debug("Added new UI window definition for $name and status is : ${disabled ? 'disabled' : 'enabled'}") }
        }
    }
}
