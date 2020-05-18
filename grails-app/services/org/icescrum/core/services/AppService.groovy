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

import grails.transaction.Transactional
import org.icescrum.core.app.AppDefinition
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.SimpleProjectApp
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class AppService extends IceScrumEventPublisher {

    def appDefinitionService
    def grailsApplication

    @PreAuthorize('productOwner(#project) or scrumMaster(#project)')
    void updateEnabledForProject(Project project, String appDefinitionId, boolean enabledForProject) {
        AppDefinition appDefinition = appDefinitionService.getAppDefinitions().find { it.id == appDefinitionId }
        if (!appDefinition.isProject) {
            throw new BusinessException(code: 'Error, the App ' + appDefinitionId + ' cannot be enabled/disabled for the project because it is not at project level')
        }
        updateSimpleProjectAppEnabledForProject(project, appDefinition, enabledForProject)
        if (enabledForProject) {
            if (appDefinition.onEnableForProject) {
                appDefinition.onEnableForProject(project, grailsApplication)
            }
        } else {
            if (appDefinition.onDisableForProject) {
                appDefinition.onDisableForProject(project, grailsApplication)
            }
        }
    }

    boolean isEnabledAppForProject(Project project, String appDefinitionId) {
        def simpleProjectApp = SimpleProjectApp.findByAppDefinitionIdAndParentProject(appDefinitionId, project)
        if (simpleProjectApp) {
            return simpleProjectApp.availableForServer && simpleProjectApp.enabledForServer && simpleProjectApp.enabled
        } else {
            return false
        }
    }

    boolean isAvailableAppForProject(String appDefinitionId) {
        return appDefinitionService.getAppDefinitions().find {
            return appDefinitionId == it.id && it.isProject && (grailsApplication.config.icescrum.beta.enable && grailsApplication.config.icescrum.beta[it.id]?.enable ? true : !it.isBeta)
        } ? true : false
    }

    private void updateSimpleProjectAppEnabledForProject(Project project, AppDefinition appDefinition, enabledForProject) {
        SimpleProjectApp app = SimpleProjectApp.findByAppDefinitionIdAndParentProject(appDefinition.id, project)
        if (!app) {
            app = new SimpleProjectApp(appDefinitionId: appDefinition.id, parentProject: project)
        }
        app.enabled = enabledForProject
        app.save(flush: true)
    }

    def unMarshall(def simpleProjectAppXml, def options) {
        Project project = options.project
        SimpleProjectApp.withTransaction(readOnly: !options.save) { transaction ->
            def simpleProjectApp = new SimpleProjectApp(
                    appDefinitionId: simpleProjectAppXml.appDefinitionId.text(),
                    enabled: simpleProjectAppXml.enabled.text().toBoolean(),
            )
            if (project) {
                simpleProjectApp.parentProject = project
            }
            if (options.save) {
                simpleProjectApp.save()
            }
            return (SimpleProjectApp) importDomainsPlugins(simpleProjectAppXml, simpleProjectApp, options)
        }
    }
}
