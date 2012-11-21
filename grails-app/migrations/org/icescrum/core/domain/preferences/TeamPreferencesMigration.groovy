/*
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



class TeamPreferencesMigration {

    static migration = {
      changeSet(id:'team_preferences_drop_allow_role_change_column', author:'vbarrier', filePath:filePath) {
        preConditions(onFail:"MARK_RAN"){
            columnExists(tableName:'icescrum2_team_preferences', columnName:"allow_role_change")
        }
        dropColumn(tableName:'icescrum2_team_preferences', columnName:"allow_role_change")
      }
    }

    static def getFilePath(){
        return ""
    }

}

