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
import org.icescrum.core.domain.preferences.UserPreferences

class Widget implements Serializable {

    static final long serialVersionUID = 813639045722976126L

    int position
    boolean onRight = false

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

    def beforeInsert() {
        position = !position ? Widget.countByOnRight(onRight) + 1 : position
    }

    static transients = ["settings"]

    public void setSettings(Map settings) {
        settingsData = settings ? settings as JSON : null
    }

    public Map getSettings() {
        settingsData ? JSON.parse(settingsData) as Map : [:]
    }

    def xml = { builder ->
        builder.widget() {
            builder.onRight(this.onRight)
            builder.position(this.position)
            builder.widgetDefinitionId(this.widgetDefinitionId)
            builder.settingsData { builder.mkp.yieldUnescaped("<![CDATA[${this.settingsData ?: ''}]]>") }
            exportDomainsPlugins(builder)
        }
    }
}
