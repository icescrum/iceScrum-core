package org.icescrum.core.domain

import grails.converters.JSON
import org.icescrum.core.domain.preferences.UserPreferences

class Window implements Serializable {

    static final long serialVersionUID = 813639045722976162L

    String context
    Long contextId
    String settingsData
    String windowDefinitionId

    static belongsTo = [user: User]

    static transients = ["settings"]

    static constraints = {
        context nullable: true
        contextId nullable: true
        settingsData nullable: true
    }

    static mapping = {
        cache true
        settingsData type: 'text'
        table 'is_up_window'
        user index: 'is_up_win_index'
        context index: 'is_up_win_index'
        contextId index: 'is_up_win_index'
        windowDefinitionId index: 'is_up_win_index'
    }

    public void setSettings(Map settings) {
        settingsData = settings ? settings as JSON : null
    }

    public Map getSettings() {
        settingsData ? JSON.parse(settingsData) as Map : [:]
    }

    def xml = { builder ->
        builder.widget() {
            builder.widgetDefinitionId(this.windowDefinitionId)
            builder.settingsData { builder.mkp.yieldUnescaped("<![CDATA[${this.settingsData ?: ''}]]>") }
            exportDomainsPlugins(builder)
        }
    }
}