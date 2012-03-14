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

import java.util.concurrent.ConcurrentHashMap
import org.icescrum.core.ui.UiDefinitionsBuilder
import org.icescrum.core.ui.UiDefinition

class UiDefinitionService {

    static transactional = false

    def grailsApplication

    private Map definitionsById

    def loadDefinitions() {
        if (log.infoEnabled) { log.info "Loading UI definitions..." }
        definitionsById = new ConcurrentHashMap()
        grailsApplication.uiDefinitionClasses.each{
            def uiDefinitions = new ConfigSlurper().parse(it.clazz).uiDefinitions
            if(uiDefinitions instanceof Closure) {
                if (log.debugEnabled) { log.debug("Evaluating UI definitions from $it.clazz.name") }
                def builder = new UiDefinitionsBuilder(definitionsById)
                uiDefinitions.delegate = builder
                uiDefinitions.resolveStrategy = Closure.DELEGATE_FIRST
                uiDefinitions()
            } else {
                log.warn("UI definitions file $it.clazz.name does not define any UI definition")
            }
        }
    }

    def reload() {
        if (log.infoEnabled) { log.info("Reloading UI definitions") }
        loadDefinitions()
    }

    UiDefinition getDefinitionById(String id) { definitionsById[id] }

    def getDefinitions() { definitionsById }

    boolean hasDefinition(String id) { definitionsById.containsKey(id) }
}
