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
        changeSet(id:'rename_password_column', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                    dbms(type:'hsqldb')
                }
                columnExists(tableName:"icescrum2_user", columnName:'password')
            }
            dropColumn(tableName:"icescrum2_user", columnName:"passwd")
            renameColumn(tableName:"icescrum2_user", oldColumnName:'password', newColumnName:"passwd", columnDataType:'varchar(255)')
        }

        changeSet(id:'rename_password_column_hsql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'hsqldb')
                sqlCheck("SELECT count(COLUMN_NAME) FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS WHERE TABLE_NAME = 'ICESCRUM2_USER' AND COLUMN_NAME = 'password'",expectedResult:'1')
            }
            dropColumn(tableName:"icescrum2_user", columnName:"passwd")
            renameColumn(tableName:"icescrum2_user", oldColumnName:'"password"', newColumnName:"passwd", columnDataType:'varchar(255)')
        }

        changeSet(id:'add_uid_column_user', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                  or {
                    dbms(type:'mssql')
                    dbms(type:'hsqldb')
                    dbms(type:'postgresql')
                    dbms(type:'oracle')
                  }
                }
            }
            sql('UPDATE icescrum2_user set uid = MD5(CONCAT(username,\'\',email)) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_user",columnName:'uid',columnDataType:'varchar(255)')
        }

        changeSet(id:'add_uid_column_user_postgresql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'postgresql')
            }
            sql('UPDATE icescrum2_user set uid = MD5(username || email) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_user",columnName:'uid',columnDataType:'varchar(255)')
        }

        changeSet(id:'add_uid_column_user_mssql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'mssql')
            }
            sql('UPDATE icescrum2_user set uid = SUBSTRING(sys.fn_sqlvarbasetostr(HASHBYTES(\'MD5\',username + email)),3,32) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_user",columnName:'uid',columnDataType:'varchar(max)')
        }

        changeSet(id:'add_uid_column_user_hsql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                  dbms(type:'hsqldb')
            }
            sql('CREATE ALIAS MD5 FOR "org.hsqldb.lib.MD5.encodeString"')
            sql('UPDATE icescrum2_user set uid = MD5(CONCAT(username,email)) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_user",columnName:'uid',columnDataType:'varchar(255)')
        }

        changeSet(id:'user_constraint_external_column', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                    or{
                        dbms(type:'mssql')
                        dbms(type:'oracle')
                    }
                }
            }
            sql('UPDATE icescrum2_user set account_external = false WHERE account_external is NULL')
            addNotNullConstraint(tableName:"icescrum2_user",columnName:'account_external',columnDataType:'BOOLEAN')
        }

        changeSet(id:'user_constraint_external_column_mssql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'mssql')
            }
            sql('UPDATE icescrum2_user set account_external = 0 WHERE account_external is NULL')
            addNotNullConstraint(tableName:"icescrum2_user",columnName:'account_external',columnDataType:'BIT')
        }

        changeSet(id:'user_update_en_to_us', author:'vbarrier', filePath:filePath) {
            sql('UPDATE icescrum2_user_preferences set language = \'en_US\' WHERE language = \'en\'')
        }
    }

    static def getFilePath(){
        return ""
    }
}

