/*
 * Copyright (c) 2017 Kagilum SAS
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

package org.icescrum.core.domain


import grails.util.Holders
import org.icescrum.core.services.AppDefinitionService

class SimpleProjectApp implements Serializable {

    boolean enabled = false
    String appDefinitionId

    static belongsTo = [parentProject: Project]

    static mapping = {
        cache true
        table 'is_simple_project_app'
    }

    static transients = [
            'availableForServer', 'enabledForServer'
    ]

    static countEnabledByParentProjectOwner(String user, params) {
        executeQuery("""SELECT DISTINCT COUNT(spa.id)
                        FROM org.icescrum.core.domain.SimpleProjectApp as spa,
                             grails.plugin.springsecurity.acl.AclClass as ac,
                             grails.plugin.springsecurity.acl.AclObjectIdentity as ai,
                             grails.plugin.springsecurity.acl.AclSid as acl
                        WHERE enabled = true
                        AND ac.className = 'org.icescrum.core.domain.Project'
                        AND ai.aclClass = ac.id
                        AND ai.owner.sid = :sid
                        AND acl.id = ai.owner
                        AND spa.parentProject.id = ai.objectId""", [sid: user], params ?: [:])
    }

    boolean getAvailableForServer() {
        AppDefinitionService appDefinitionService = (AppDefinitionService) Holders.grailsApplication.mainContext.getBean('appDefinitionService')
        return appDefinitionService.getAppDefinition(appDefinitionId)?.availableForServer ?: false
    }

    boolean getEnabledForServer() {
        AppDefinitionService appDefinitionService = (AppDefinitionService) Holders.grailsApplication.mainContext.getBean('appDefinitionService')
        return appDefinitionService.getAppDefinition(appDefinitionId)?.enabledForServer ?: false
    }

    def xml(builder) {
        builder.simpleProjectApp {
            builder.appDefinitionId(this.appDefinitionId)
            builder.enabled(this.enabled)
            exportDomainsPlugins(builder)
        }
    }
}
