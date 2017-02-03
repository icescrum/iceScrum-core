/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 */


package org.icescrum.core.domain.preferences

import grails.converters.JSON
import org.codehaus.groovy.grails.web.json.JSONObject
import org.icescrum.core.domain.User

class UserPreferences implements Serializable {

    static final long serialVersionUID = 813649045202976126L

    String language = "en"
    String activity
    String filterTask = "allTasks"
    String lastProductOpened
    String emailsSettingsData //[onStory:['pkey','pkey2'...],onUrgentTask:['pkey','pkey2'...],autoFollow['pkey','pkey2'...]]


    boolean hideDoneState = false
    boolean displayWhatsNew = false
    boolean displayWelcomeTour = true
    boolean displayFullProjectTour = true

    Map menu = ["project": "1", "sandbox": "2", "backlog": "3", "timeline": "4", "releasePlan": "5", "sprintPlan": "6"]
    Map menuHidden = ["actor": "1", "feature": "2"]

    static transients = ["emailsSettings"]

    static constraints = {
        activity nullable: true
        lastProductOpened nullable: true
        emailsSettingsData nullable: true
    }


    static belongsTo = [
            user: User
    ]

    static mapping = {
        cache true
        table System.properties['icescrum.oracle'] ? 'icescrum2_u_pref' : 'icescrum2_user_preferences'
        emailsSettingsData type: "text"
    }

    public void setEmailsSettings(Map settings) {
        ['onStory', 'autoFollow', 'onUrgentTask'].each { setting ->
            if (settings[setting] instanceof String) {
                settings[setting] = [settings[setting]]
            }
        }
        emailsSettingsData = settings ? settings as JSON : null
    }

    public Map getEmailsSettings() {
        emailsSettingsData ? JSON.parse(emailsSettingsData) as Map : [:]
    }

    public removeEmailsSettings(pkey) {
        def settings = getEmailsSettings()
        if (settings) {
            settings.each { setting, projects ->
                if (projects != JSONObject.NULL && projects?.indexOf(pkey) >= 0) {
                    projects.remove(projects.indexOf(pkey))
                }
            }
            setEmailsSettings(settings)
        }
    }

    // V7
    def xml = { builder ->
        builder.preferences(id: this.id) {
//            builder.menu(this.menu)
            builder.language(this.language)
            builder.activity(this.activity)
            builder.filterTask(this.filterTask)
//            builder.menuHidden(this.menuHidden)
//            builder.displayWhatsNew(this.displayWhatsNew)
//            builder.lastProjectOpened(this.lastProjectOpened)
//            builder.emailsSettingsData(this.emailsSettingsData)
//            builder.displayWelcomeTour(this.displayWelcomeTour)
//            builder.lastReadActivities(this.lastReadActivities)
//            builder.displayFullProjectTour(this.displayFullProjectTour)
            // R6 -> v7
            builder.widgets() {
                [
                        [
                                onRight           : false,
                                position          : 1,
                                widgetDefinitionId: 'quickProjects',
                                settingsData      : ''
                        ], [
                                onRight           : false,
                                position          : 2,
                                widgetDefinitionId: 'notes',
                                settingsData      : '{"text":"Welcome to iceScrum 7! Here is your home, where you can add your widgets, such as this one which allows you to write your personal notes, try updating this text!\\n\\nWe have also created a \\"Peetic\\" project so you can explore iceScrum, try opening it!","text_html":"<?xml version=\'1.0\' encoding=\'utf-8\' ?><html xmlns=\\"http://www.w3.org/1999/xhtml\\"><head><meta http-equiv=\\"Content-Type\\" content=\\"text/html; charset=utf-8\\"/><\\u002fhead><body><p>Welcome to iceScrum 7! Here is your home, where you can add your widgets, such as this one which allows you to write your personal notes, try updating this text!<\\u002fp><p>We have also created a &#171;Peetic&#187; project so you can explore iceScrum, try opening it!<\\u002fp><\\u002fbody><\\u002fhtml>"}'
                        ], [
                                onRight           : true,
                                position          : 1,
                                widgetDefinitionId: 'feed',
                                settingsData      : '{"feeds":[{"url":"https://www.icescrum.com/blog/feed/","title":"iceScrum","selected":true},{"url":"http://www.universfreebox.com/backend.php","title":"Univers Freebox","selected":false}]}'
                        ], [
                                onRight           : true,
                                position          : 2,
                                widgetDefinitionId: 'tasks',
                                settingsData      : ''
                        ]

                ].each { _widget ->
                    builder.widget() {
                        builder.onRight(_widget.onRight)
                        builder.position(_widget.position)
                        builder.widgetDefinitionId(_widget.widgetDefinitionId)
                        builder.settingsData { builder.mkp.yieldUnescaped("<![CDATA[${_widget.settingsData ?: ''}]]>") }
                    }
                }
            }
            exportDomainsPlugins(builder)
        }
    }
}
