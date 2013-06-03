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
    //[onStory:['pkey','pkey2'...],onUrgentTask:['pkey','pkey2'...],autoFollow['pkey','pkey2'...]]
    String emailsSettingsData

    boolean hideDoneState = false

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
        settings.onStory = settings.onStory instanceof String ? settings.onStory = [settings.onStory] : settings.onStory
        settings.autoFollow = settings.autoFollow instanceof String ? settings.autoFollow = [settings.autoFollow] : settings.autoFollow
        settings.onUrgentTask = settings.onUrgentTask instanceof String ? settings.onUrgentTask = [settings.onUrgentTask] : settings.onUrgentTask
        emailsSettingsData = settings ? settings as JSON : null
    }

    public Map getEmailsSettings() {
        return emailsSettingsData ? JSON.parse(emailsSettingsData) as Map : [:]
    }

    public removeEmailsSettings(value){
        def settings = getEmailsSettings()
        if (settings){
            if(settings.autoFollow != JSONObject.NULL && settings.autoFollow?.indexOf(value) >= 0){
                settings.autoFollow.remove(settings.autoFollow.indexOf(value))
            }
            if(settings.onStory != JSONObject.NULL && settings.onStory?.indexOf(value) >= 0){
                settings.onStory.remove(settings.onStory.indexOf(value))
            }
            if(settings.onUrgentTask != JSONObject.NULL && settings.onUrgentTask?.indexOf(value) >= 0){
                settings.onUrgentTask.remove(settings.onUrgentTask.indexOf(value))
            }
        }
        setEmailsSettings(settings)
    }
}