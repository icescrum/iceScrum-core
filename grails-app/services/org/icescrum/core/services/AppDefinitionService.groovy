/*
 * Copyright (c) 2017 Kagilum SAS
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
 *
 */
package org.icescrum.core.services

import org.icescrum.core.app.AppDefinition

class AppDefinitionService {

    static transactional = false

    def grailsApplication

    List<AppDefinition> getAppDefinitions() {
        return grailsApplication.config.icescrum.appDefinitions ? grailsApplication.config.icescrum.appDefinitions.values() as List : []
    }

    AppDefinition getAppDefinition(String appDefinitionId) {
        return getAppDefinitions().find { it.id == appDefinitionId }
    }

    void registerAppDefinitions(List<AppDefinition> appDefinitions) {
        if (!grailsApplication.config.icescrum.appDefinitions) {
            grailsApplication.config.icescrum.appDefinitions = [:]
        }
        appDefinitions.each { AppDefinition appDefinition ->
            grailsApplication.config.icescrum.appDefinitions[appDefinition.id] = appDefinition
        }
    }

    void loadAppDefinitions() {
        log.info('Loading App definitions...')
        grailsApplication.appsClasses.each {
            new ConfigSlurper().parse(it.clazz)
        }
    }

    void reloadAppDefinitions() {
        log.info('Reloading App definitions')
        loadAppDefinitions()
    }
}
