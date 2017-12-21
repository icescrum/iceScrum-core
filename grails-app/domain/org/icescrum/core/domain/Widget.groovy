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
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.services.UiDefinitionService

class Widget implements Serializable {

    static final long serialVersionUID = 813639045722976126L

    int position

    WidgetParentType parentType
    String settingsData
    String widgetDefinitionId
    String type
    Long typeId

    static belongsTo = [userPreferences: UserPreferences, portfolio: Portfolio]

    static constraints = {
        settingsData(nullable: true)
        type(nullable: true)
        typeId(nullable: true)
        widgetDefinitionId(shared: 'keyMaxSize')
        userPreferences(nullable: true)
        portfolio(nullable: true)
        parentType(nullable: true, validator: { newParentType, widget ->
            newParentType == WidgetParentType.USER && widget.userPreferences != null && widget.portfolio == null ||
            newParentType == WidgetParentType.PORTFOLIO && widget.userPreferences == null && widget.portfolio != null ?: 'invalid'
        })
    }

    static mapping = {
        cache true
        settingsData type: 'text'
        table 'is_up_widgets'
        userPreferences index: 'up_wdi_index'
        widgetDefinitionId index: 'up_wdi_index'
    }

    static transients = ['settings', 'width', 'height', 'parent']

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

    private String getParentPropertyName() {
        def propertyNames = [(WidgetParentType.USER): 'userPreferences', (WidgetParentType.PORTFOLIO): 'portfolio']
        return propertyNames[parentType]
    }

    def getParent() {
        return this."$parentPropertyName"
    }

    def setParent(parent) {
        this."$parentPropertyName" = parent
    }

    static Widget withWidget(long id) {
        Widget widget = Widget.get(id)
        if (!widget) {
            throw new ObjectNotFoundException(id, 'Widget')
        }
        return widget
    }

    enum WidgetParentType {
        USER, PORTFOLIO
    }

    // BE CAREFUL: Only export user widgets
    def xml = { builder ->
        builder.widget() {
            builder.position(this.position)
            builder.widgetDefinitionId(this.widgetDefinitionId)
            builder.settingsData { builder.mkp.yieldUnescaped("<![CDATA[${this.settingsData ?: ''}]]>") }
            exportDomainsPlugins(builder)
        }
    }
}
