/*
 * Copyright (c) 2015 Kagilum SAS
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
 * Vincent Barrier (vbarrier@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */


package org.icescrum.core.services

import grails.util.Environment
import org.icescrum.core.support.ApplicationSupport

class BootStrapService {

    def pluginManager
    def grailsApplication
    def authorityService

    void start() {
        def dev = Environment.current == Environment.DEVELOPMENT && !System.properties['icescrum.noDummyze']
        def config = grailsApplication.config
        if (!config.icescrum.createDefaultAdmin) {
            config.icescrum.createDefaultAdmin = dev
        }
        authorityService.initSecurity()

        ApplicationSupport.checkInitialConfig(config)
        ApplicationSupport.generateFolders(config)
        ApplicationSupport.initEnvironment(config)

        if(!dev){
            ApplicationSupport.checkForUpdateAndReportUsage(config)
        }

        config.grails.attachmentable.baseDir = config.icescrum.baseDir.toString()
        config.grails.mail.default.from = config.icescrum.alerts.default.from

        if (config.grails.mail.props && config.grails.mail.props instanceof String) {
            config.grails.mail = config.grails.mail.clone() // Make MailGrailsPlugin onConfigChange see a new hashcode and take the update into account (just changing the props don't change the hashcode)
            config.grails.mail.props = ApplicationSupport.stringToMap(config.grails.mail.props)
            pluginManager.informPluginsOfConfigChange()
        }
    }
}
