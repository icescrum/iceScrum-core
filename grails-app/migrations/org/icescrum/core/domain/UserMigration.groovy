/*
 * Copyright (c) 2011 Kagilum SAS
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

class UserMigration {

    static migration = {
            // List of changesets
            changeSet(id:'rename_password_column', author:'vbarrier') {
                preConditions(onFail:"MARK_RAN"){
                    not{
                        dbms(type:'hsqldb')
                    }
                    columnExists(tableName:"icescrum2_user", columnName:'password')
                }
                dropColumn(tableName:"icescrum2_user", columnName:"passwd")
                renameColumn(tableName:"icescrum2_user", oldColumnName:'password', newColumnName:"passwd", columnDataType:'varchar(255)')
            }

            changeSet(id:'rename_password_column_hsql', author:'vbarrier') {
                preConditions(onFail:"MARK_RAN"){
                    dbms(type:'hsqldb')
                    sqlCheck("SELECT count(COLUMN_NAME) FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS WHERE TABLE_NAME = 'ICESCRUM2_USER' AND COLUMN_NAME = 'password'",expectedResult:'1')
                }
                dropColumn(tableName:"icescrum2_user", columnName:"passwd")
                renameColumn(tableName:"icescrum2_user", oldColumnName:'"password"', newColumnName:"passwd", columnDataType:'varchar(255)')
            }
    }
}

