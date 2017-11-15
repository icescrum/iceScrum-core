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
 * Vincent Barrier (vbarrier@kagilum.com)
 *
 */
package org.icescrum.core.services

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Window
import org.icescrum.core.error.BusinessException
import org.icescrum.core.ui.WindowDefinition

class WindowService {

    def uiDefinitionService
    def grailsApplication

    Window retrieve(WindowDefinition windowDefinition, User user, def workspace) {
        def window = null
        if (user) {
            window = workspace ? Window.findByWindowDefinitionIdAndUserAndWorkspaceAndWorkspaceId(windowDefinition.id, user, workspace.name, workspace.object.id) : Window.findByWindowDefinitionIdAndUser(windowDefinition.id, user)
            if (!window && windowDefinition.alwaysInitSettings) {
                window = save(windowDefinition, user, workspace)
            }
        }
        return window
    }

    Window save(WindowDefinition windowDefinition, User user, def workspace) {
        def window = null
        if (user) {
            window = workspace ? new Window(windowDefinitionId: windowDefinition.id, user: user, workspace: workspace.name, workspaceId: workspace.object.id, settings: windowDefinition.defaultSettings) : new Window(windowDefinitionId: windowDefinition.id, user: user)
            try {
                windowDefinition.onSave(window)
            } catch (Exception e) {
                if (e instanceof BusinessException) {
                    throw e
                } else {
                    if (log.errorEnabled) log.error(e.message, e)
                    throw new BusinessException(code: 'is.window.error.save')
                }
            }
            window.save(flush: true)
        }
        return window
    }

    void update(Window window, Map props) {
        try {
            uiDefinitionService.getWindowDefinitionById(window.windowDefinitionId).onUpdate(window, props.settings)
            if (props.settings) {
                window.setSettings(props.settings)
            }
        } catch (Exception e) {
            if (e instanceof BusinessException) {
                throw e
            } else {
                if (log.errorEnabled) log.error(e.message, e)
                throw new BusinessException(code: 'is.window.error.update')
            }
        }
        window.save()
    }

    void delete(String workspaceName, long workspaceId) {
        def windows = Window.findAllByWorkspaceAndWorkspaceId(workspaceName, workspaceId)
        windows.each { Window window ->
            try {
                uiDefinitionService.getWindowDefinitionById(window.windowDefinitionId).onDelete(window)
            } catch (Exception e) {
                if (e instanceof BusinessException) {
                    throw e
                } else {
                    if (log.errorEnabled) log.error(e.message, e)
                    throw new BusinessException(code: 'is.window.error.delete')
                }
            }
        }
    }

    def unMarshall(def windowXml, def options) {
        Window.withTransaction(readOnly: !options.save) { transaction ->
            def window = new Window(
                    settingsData: windowXml.settingsData.text() ?: null,
                    windowDefinitionId: windowXml.windowDefinitionId.text())
            // Reference on other object
            if (options.user) {
                window.user = options.user
            }
            if (options.save) {
                window.save()
            }
            return (Window) importDomainsPlugins(windowXml, window, options)
        }
    }
}

