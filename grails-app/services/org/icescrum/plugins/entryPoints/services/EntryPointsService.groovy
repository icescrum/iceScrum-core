/*
* Copyright (c) 2011 Vincent Barrier.
*
*
* entry-points is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License.
*
* entry-points is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.icescrum.plugins.entryPoints.services

import org.icescrum.plugins.entryPoints.EntryPointsBuilder
import org.icescrum.plugins.entryPoints.EntryPointsDeclarationsFactory

import java.util.concurrent.ConcurrentHashMap

class EntryPointsService {

    static transactional = false

    def grailsApplication
    def entriesByPointId
    def pluginManager

    def getEntries(def pointId) {
        entriesByPointId[pointId]
    }

    private loadEntries() {
        if (log.infoEnabled) {
            log.info "Loading entries declarations..."
        }

        entriesByPointId = new ConcurrentHashMap()
        def declarations = EntryPointsDeclarationsFactory.getEntriesDeclarations(grailsApplication, pluginManager)
        def builder = new EntryPointsBuilder(entriesByPointId)

        declarations.each { sourceClassName, dslList ->
            if (dslList) {
                if (log.debugEnabled) {
                    log.debug("Evaluating ${dslList.size()} entry blocks from $sourceClassName")
                }
                builder.pluginName = sourceClassName
                dslList.each { dsl ->
                    dsl.delegate = builder
                    dsl.resolveStrategy = Closure.DELEGATE_FIRST
                    dsl()
                }
            }
        }
    }

    def reload() {
        log.info("Reloading entry points")
        loadEntries()
    }

    def getEntriesToChain(def pointId) {
        entriesByPointId[pointId]?.findAll { it.form }
    }
}
