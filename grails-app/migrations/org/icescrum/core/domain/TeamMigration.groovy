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

class TeamMigration {

    static migration = {
        changeSet(id:'add_uid_column_team', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                  or {
                    dbms(type:'mssql')
                    dbms(type:'hsqldb')
                    dbms(type:'oracle')
                  }
                }
            }
            sql('UPDATE icescrum2_team set uid = MD5(name) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_team",columnName:'uid',columnDataType:'varchar(255)')
        }

        changeSet(id:'add_uid_column_team_hsql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'hsqldb')
            }
            sql('CREATE ALIAS MD5 FOR "org.hsqldb.lib.MD5.encodeString"')
            sql('UPDATE icescrum2_team set uid = MD5(name) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_team",columnName:'uid',columnDataType:'varchar(255)')
        }

        changeSet(id:'add_uid_column_team_mssql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'mssql')
            }
            sql('UPDATE icescrum2_team set uid = SUBSTRING(sys.fn_sqlvarbasetostr(HASHBYTES(\'MD5\',name)),3,32) WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_team",columnName:'uid',columnDataType:'varchar(max)')
        }
    }

    static def getFilePath(){
        return ""
    }
}

