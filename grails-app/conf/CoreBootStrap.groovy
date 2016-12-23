/*
 * Copyright (c) 2014 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 */

import grails.util.Environment
import grails.util.Holders
import org.icescrum.core.domain.User
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.test.DummyPopulator
import org.springframework.security.core.context.SecurityContextHolder

class CoreBootStrap {
    def userService
    def init = { servletContext ->

        def dev = Environment.current == Environment.DEVELOPMENT && !System.properties['icescrum.noDummyze']
        if(dev){
            println "Dummy Data loading...."
            if (User.count() <= 1) {
                def usera = new User(username: "a", email: "a@gmail.com", firstName: "Roberto", password: "a", preferences: new UserPreferences(language: 'en', activity: 'Consultant'))
                def userz = new User(username: "z", email: "z@gmail.com", firstName: "Bernardo", password: "z", preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', menu: ["feature": "1", "backlog": "2"]))
                userService.save(usera)
                userService.save(userz)
            }
        }

    }
}