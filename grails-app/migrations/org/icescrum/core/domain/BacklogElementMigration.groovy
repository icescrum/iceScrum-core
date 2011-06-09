/*
 *
 * Copyright (c) 2011 Kagilum SAS
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
package org.icescrum.core.domain

class BacklogElementMigration {

    static runAfter = [ UserMigration ]

    static migration = {
            // List of changesets
            changeSet(id:'remove_id_from_import_column_feature', author:'vbarrier') {
                preConditions(onFail:"MARK_RAN"){
                    columnExists(tableName:"icescrum2_feature", columnName:"id_from_import")
                }
                dropColumn(tableName:"icescrum2_feature", columnName:"id_from_import")
            }
            changeSet(id:'remove_id_from_import_column_task', author:'vbarrier') {
                preConditions(onFail:"MARK_RAN"){
                    columnExists(tableName:"icescrum2_task", columnName:"id_from_import")
                }
                dropColumn(tableName:"icescrum2_task", columnName:"id_from_import")
            }
            changeSet(id:'remove_id_from_import_column_story', author:'vbarrier') {
                preConditions(onFail:"MARK_RAN"){
                    columnExists(tableName:"icescrum2_story", columnName:"id_from_import")
                }
                dropColumn(tableName:"icescrum2_story", columnName:"id_from_import")
            }
            changeSet(id:'remove_id_from_import_column_impediment', author:'vbarrier') {
                preConditions(onFail:"MARK_RAN"){
                    columnExists(tableName:"icescrum2_impediment", columnName:"id_from_import")
                }
                dropColumn(tableName:"icescrum2_impediment", columnName:"id_from_import")
            }
    }
}

