/*
 * Copyright (c) 2018 Kagilum SAS.
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

package org.icescrum.core.services

import org.codehaus.groovy.grails.plugins.web.taglib.ValidationTagLib
import org.icescrum.core.domain.User
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.servlet.i18n.SessionLocaleResolver

class I18nService {

    boolean transactional = false

    SessionLocaleResolver localeResolver
    def grailsApplication
    def springSecurityService

    def message(Map args) {
        def _request = RequestContextHolder.requestAttributes?.currentRequest
        if (!_request) {
            args.locale = springSecurityService.isLoggedIn() ? User.getLocale(springSecurityService.principal.id) : localeResolver.defaultLocale
        }
        ValidationTagLib validationTagLib = (ValidationTagLib) grailsApplication.mainContext.getBean('org.codehaus.groovy.grails.plugins.web.taglib.ValidationTagLib')
        def messageMethod = validationTagLib.message // Big hack because closure cannot be called directly because taglibs don't work without request
        return messageMethod(args)
    }
}