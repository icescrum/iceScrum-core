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
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */

package org.icescrum.core.domain

import grails.converters.JSON
import grails.util.Holders
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.services.UiDefinitionService

class Widget implements Serializable {

    static final long serialVersionUID = 813639045722976126L

    int position

    String settingsData
    String widgetDefinitionId
    String type
    Long typeId

    static belongsTo = [userPreferences: UserPreferences]

    static constraints = {
        settingsData nullable: true
        type nullable: true
        typeId nullable: true
    }

    static mapping = {
        cache true
        settingsData type: 'text'
        table 'is_up_widgets'
        userPreferences index: 'up_wdi_index'
        widgetDefinitionId index: 'up_wdi_index'
    }

    static transients = ["settings", "width", "height"]

    void setSettings(Map settings) {
        settingsData = settings ? settings as JSON : null
    }

    Map getSettings() {
        settingsData ? JSON.parse(settingsData) as Map : [:]
    }

    int getWidth() {
        def uiDefinitionService = (UiDefinitionService) Holders.grailsApplication.mainContext.getBean('uiDefinitionService')
        uiDefinitionService.getWidgetDefinitionById(widgetDefinitionId).width
    }

    int getHeight() {
        def uiDefinitionService = (UiDefinitionService) Holders.grailsApplication.mainContext.getBean('uiDefinitionService')
        uiDefinitionService.getWidgetDefinitionById(widgetDefinitionId).height
    }

    def xml = { builder ->
        builder.widget() {
            builder.position(this.position)
            builder.widgetDefinitionId(this.widgetDefinitionId)
            builder.settingsData { builder.mkp.yieldUnescaped("<![CDATA[${this.settingsData ?: ''}]]>") }
            exportDomainsPlugins(builder)
        }
    }
}
