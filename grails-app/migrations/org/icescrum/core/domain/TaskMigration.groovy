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
package org.icescrum.core.domain

class TaskMigration {

    static migration = {
            // List of changesets
        changeSet(id:'task_constraint_block_column', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                    or {
                        dbms(type:'mssql')
                        dbms(type:'oracle')
                    }
                }
            }
            sql('UPDATE icescrum2_task set blocked = false WHERE blocked is NULL')
            addNotNullConstraint(tableName:"icescrum2_task",columnName:'blocked',columnDataType:'BOOLEAN')
        }

        changeSet(id:'task_constraint_block_column_mssql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'mssql')
            }
            sql('UPDATE icescrum2_task set blocked = 0 WHERE blocked is NULL')
            addNotNullConstraint(tableName:"icescrum2_task",columnName:'blocked',columnDataType:'BIT')
        }

        changeSet(id:'task_estimation_integer_tofloat_column', author:'vbarrier', filePath:filePath) {
            modifyColumn(tableName:"icescrum2_task"){
                column(name:'estimation', type:'FLOAT')
            }
        }

        changeSet(id:'remove_task_backlog_non_nullable', author:'vbarrier', filePath:filePath) {
            dropNotNullConstraint(tableName:"icescrum2_task", columnName:'backlog_id', columnDataType:'BIGINT')
        }
    }

    static def getFilePath(){
        return ""
    }
}

