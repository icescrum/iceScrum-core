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

class UserPreferences implements Serializable{

    static final long serialVersionUID = 813649045202976126L

    String language = "en"
    String activity
    String filterTask = "allTasks"
    String lastProductOpened
    String emailsSettingsData //[onStory:['pkey','pkey2'...],onUrgentTask:['pkey','pkey2'...],autoFollow['pkey','pkey2'...]]
    boolean displayWhatsNew = false

    boolean hideDoneState = false

    Date lastReadActivities = new Date()

    Map menu = ["project": "1", "backlog": "2", "timeline": "3", "releasePlan": "4", "sprintPlan": "5"]
    Map menuHidden = ["actor": "1", "feature": "2"]

    static transients = ["emailsSettings"]

    static constraints = {
        activity nullable: true
        lastProductOpened nullable: true
        emailsSettingsData nullable: true
        lastReadActivities nullable: true
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

    public removeEmailsSettings(pkey){
        def settings = getEmailsSettings()
        if (settings) {
            settings.each { setting, projects ->
                if (projects?.indexOf(pkey) >= 0) {
                    projects.remove(projects.indexOf(pkey))
                }
            }
            setEmailsSettings(settings)
        }
    }

    def xml = { builder ->
        builder.preferences(id:this.id) {
            menu(this.menu)
            language(this.language)
            activity(this.activity)
            filterTask(this.filterTask)
            menuHidden(this.menuHidden)
            hideDoneState(this.hideDoneState)
            lastProductOpened(this.lastProductOpened)
            emailsSettingsData(this.lastProductOpened)
        }
    }
}