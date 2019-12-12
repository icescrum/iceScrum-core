/*
 * Copyright (c) 2017 Kagilum SAS.
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
package org.icescrum.core.security.rest

import grails.util.Holders

import javax.servlet.http.HttpServletRequest

class TokenExtractor {
    static final String TOKEN_HEADER = "x-icescrum-token"
    protected static final String TOKEN_PARAMETER = "icescrum-token"

    static String getToken(HttpServletRequest request) {
        String token = request.getHeader(TOKEN_HEADER)
        token = token ?: request.getParameter(TOKEN_PARAMETER) ?: lookForAppsSpecificTokens(request)
        return token
    }

    static private String lookForAppsSpecificTokens(HttpServletRequest request) {
        String token
        Holders.grailsApplication.config.icescrum.security.authorizedTokenHeaders.find { String it ->
             token = request.getHeader(it)
            if (token) {
                return true
            }
        }
        return token ?: null
    }
}
