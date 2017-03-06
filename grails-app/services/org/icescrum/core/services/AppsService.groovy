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

import org.icescrum.core.apps.App

class AppsService {

    static transactional = false

    def grailsApplication

    List<App> getApps() {
        return grailsApplication.config.icescrum.apps ? grailsApplication.config.icescrum.apps.values() as List : []
    }

    void registerApps(List<App> apps) {
        if (!grailsApplication.config.icescrum.apps) {
            grailsApplication.config.icescrum.apps = [:]
        }
        apps.each { App app ->
            grailsApplication.config.icescrum.apps[app.id] = app
        }
    }

    void loadApps() {
        log.debug("Load apps")
        grailsApplication.appsClasses.each {
            new ConfigSlurper().parse(it.clazz)
        }
    }

    void reloadApps() {
        log.debug("Reload apps")
        loadApps()
    }
}
