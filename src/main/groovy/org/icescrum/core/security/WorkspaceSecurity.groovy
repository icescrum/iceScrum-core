/*
 * Copyright (c) 2020 Kagilum.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY without even the implied warranty of
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

package org.icescrum.core.security

import grails.util.Holders
import org.icescrum.core.support.ApplicationSupport
import org.springframework.expression.Expression
import org.springframework.security.access.expression.ExpressionUtils
import org.springframework.security.web.FilterInvocation

trait WorkspaceSecurity {

    def checkPermission = { Map permissions ->
        def isAuthorized = { securityExpression ->
            if (securityExpression) {
                def springSecurityService = Holders.grailsApplication.mainContext['springSecurityService']
                def webExpressionHandler = (WebScrumExpressionHandler) Holders.grailsApplication.mainContext['webExpressionHandler']
                Expression expression = webExpressionHandler.expressionParser.parseExpression(securityExpression)
                FilterInvocation fi = new FilterInvocation(request, response, ApplicationSupport.DUMMY_CHAIN)
                def ctx = webExpressionHandler.createEvaluationContext(springSecurityService.authentication, fi)
                return ExpressionUtils.evaluateAsBoolean(expression, ctx)
            } else {
                return false
            }
        }
        if (isAuthorized(permissions[params.workspaceType])) {
            return true
        } else {
            render(status: 403)
            return false
        }
    }
}
