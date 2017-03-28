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

class SimpleProjectApp implements Serializable {

    boolean enabled = false
    String appDefinitionId

    static belongsTo = [parentProject: Project]

    static mapping = {
        cache true
        table 'is_simple_project_app'
    }

    static List<String> getEnabledAppIdsForProject(Project project) {
        findAllByParentProjectAndEnabled(project, true)*.appDefinitionId
    }

    def xml(builder) {
        builder.simpleProjectApp {
            builder.appDefinitionId(this.appDefinitionId)
            builder.enabled(this.enabled)
            exportDomainsPlugins(builder)
        }
    }
}
