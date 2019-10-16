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
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */


package org.icescrum.core.domain.preferences

import grails.converters.JSON
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Widget

class UserPreferences implements Serializable {

    static final long serialVersionUID = 813649045202976126L

    String activity
    String language = "en"
    String lastProjectOpened
    String emailsSettingsData //[onStory:['pkey','pkey2'...],onUrgentTask:['pkey','pkey2'...],autoFollow['pkey','pkey2'...]]
    String filterTask = "allTasks"

    String colorScheme

    Boolean displayReleaseNotes = false
    boolean displayWhatsNew = false
    boolean displayWelcomeTour = true
    boolean displayFullProjectTour = true

    Date lastReadActivities = new Date()

    Map menu = ["project": "1", "backlog": "2", "planning": "3", "taskBoard": "4", "feature": "5"]

    static transients = ["emailsSettings"]

    static constraints = {
        activity nullable: true
        lastProjectOpened nullable: true
        emailsSettingsData nullable: true
        displayReleaseNotes nullable: true
        colorScheme nullable: true
    }


    static belongsTo = [
            user: User
    ]

    static hasMany = [
            widgets: Widget
    ]

    static mapping = {
        cache true
        emailsSettingsData type: "text"
        table System.properties['icescrum.oracle'] ? 'is_u_pref' : 'is_user_preferences'
        widgets sort: 'position'
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
                if (projects && projects.indexOf(pkey) >= 0) {
                    projects.remove(projects.indexOf(pkey))
                }
            }
            setEmailsSettings(settings)
        }
    }

    def xml = { builder ->
        builder.preferences(id: this.id) {
            builder.menu(this.menu)
            builder.language(this.language)
            builder.activity(this.activity)
            builder.filterTask(this.filterTask)
            builder.colorScheme(this.colorScheme)
            builder.displayReleaseNotes(this.displayReleaseNotes)
            builder.displayWhatsNew(this.displayWhatsNew)
            builder.lastProjectOpened(this.lastProjectOpened)
            builder.emailsSettingsData(this.emailsSettingsData)
            builder.displayWelcomeTour(this.displayWelcomeTour)
            builder.lastReadActivities(this.lastReadActivities)
            builder.displayFullProjectTour(this.displayFullProjectTour)
            builder.widgets() {
                this.widgets?.each { _widget ->
                    _widget.xml(builder)
                }
            }
            exportDomainsPlugins(builder)
        }
    }
}
