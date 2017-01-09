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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 */


package org.icescrum.core.domain.security

import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Team

class Authority implements Serializable {

    static final long serialVersionUID = 813639045272976106L

    static final String ROLE_ADMIN = 'ROLE_ADMIN'
    static final String ROLE_USER = 'ROLE_USER'
    static final String ROLE_VISITOR = 'ROLE_ANONYMOUS'
    static final String ROLE_PERMISSION = 'ROLE_RUN_AS_PERMISSIONS_MANAGER'

    static final int MEMBER = 0
    static final int SCRUMMASTER = 1
    static final int PRODUCTOWNER = 2
    static final int STAKEHOLDER = 3
    static final int PO_AND_SM = 4

    String authority
    static mapping = {
        cache true
    }

    static constraints = {
        authority blank: false, unique: true
    }

    static String getAuthorityString(String authority, Team team) {
        "${authority}_T$team.id"
    }

    static String getAuthorityString(String authority, Project project) {
        "${authority}_P$project.id"
    }
}
