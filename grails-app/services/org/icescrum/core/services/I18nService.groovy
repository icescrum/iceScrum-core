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

import org.codehaus.groovy.grails.web.util.WebUtils
import org.springframework.context.MessageSource
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import org.springframework.web.servlet.support.RequestContextUtils

class I18nService {

    boolean transactional = false

    SessionLocaleResolver localeResolver
    MessageSource messageSource

    def msg(String msgKey, String defaultMessage = null, List objs = null) {
        def _request = null
        try {
            _request = WebUtils.retrieveGrailsWebRequest().getCurrentRequest()
        } catch (Exception e) {}
        def locale = _request ? RequestContextUtils.getLocale(_request) : localeResolver.defaultLocale
        def msg = messageSource.getMessage(msgKey, objs?.toArray(), defaultMessage, locale)
        if (msg == null || msg == defaultMessage) {
            msg = defaultMessage
        }
        return msg
    }

    def message(Map args) {
        return msg(args.code, args.default, args.attrs)
    }
}