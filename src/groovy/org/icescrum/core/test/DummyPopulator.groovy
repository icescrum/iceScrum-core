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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */


package org.icescrum.core.test

import grails.util.Holders
import org.springframework.security.core.context.SecurityContextHolder as SCH
import org.icescrum.core.domain.preferences.UserPreferences
import org.icescrum.core.domain.User

class DummyPopulator {

    public static dummyze = {
        println "Dummy Data loading...."
        def app = Holders.grailsApplication
        def sessionFactory = app.mainContext.sessionFactory
        def springSecurityService = app.mainContext.springSecurityService
        def usera, userz, userx
        if (User.count() <= 1) {
            usera = new User(username: "a", email: "a@gmail.com", firstName: "Roberto", password: springSecurityService.encodePassword('a'), preferences: new UserPreferences(language: 'en', activity: 'Consultant')).save()
            userz = new User(username: "z", email: "z@gmail.com", firstName: "Bernardo", password: springSecurityService.encodePassword('z'), preferences: new UserPreferences(language: 'en', activity: 'WebDesigner', menu: ["feature": "1", "backlog": "2"])).save()
            userx = new User(username: "x", email: "x@gmail.com", firstName: "Antonio", password: springSecurityService.encodePassword('x'), preferences: new UserPreferences(language: 'en', activity: 'Consultant')).save()
            sessionFactory.currentSession.flush()
            SCH.clearContext()
        }
    }
}
