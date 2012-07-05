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
class UiDefinitionsBuilder {

    private final log = LoggerFactory.getLogger(this.class.name)

    private ConcurrentHashMap definitionsById
    private boolean disabled = false
    
    UiDefinitionsBuilder(ConcurrentHashMap definitionsById, boolean disabled) {
        this.definitionsById = definitionsById
        this.disabled = disabled
    }

    def invokeMethod(String name, args) {
        if (args.size() == 1 && args[0] instanceof Closure) {
            def uiDefinitionClosure = args[0]
            UiDefinition uiDefinition = new UiDefinition(name, disabled)
            uiDefinitionClosure.delegate = uiDefinition
            uiDefinitionClosure.resolveStrategy = Closure.DELEGATE_FIRST
            uiDefinitionClosure()
            if(definitionsById[name]) {
                log.warn("UI definition for $name will be overriden")
            }
            definitionsById[name] = uiDefinition
            if (log.debugEnabled) { log.debug("Added new UI definition for $name and status is : ${disabled ? 'disabled' : 'enabled'}") }
        }
    }
}
