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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */
package org.icescrum.core.domain

class TimeboxMigration {
		static migration = {
            // HSQL & PostgreSQL
            changeSet(id:'timebox_add_constraint_dateCreated', author:'vbarrier') {
                  preConditions(onFail:"MARK_RAN"){
                      or {
                          dbms(type:'hsqldb')
                          dbms(type:'postgresql')
                      }
                  }
                  sql('UPDATE icescrum2_timebox set date_created = CURRENT_DATE WHERE date_created is NULL')
                  addNotNullConstraint(tableName:"icescrum2_timebox",columnName:'date_created',columnDataType:'DATETIME')
            }
            changeSet(id:'timebox_add_constraint_lastUpdated', author:'vbarrier') {
                  preConditions(onFail:"MARK_RAN"){
                      or {
                          dbms(type:'hsqldb')
                          dbms(type:'postgresql')
                      }
                  }
                  sql('UPDATE icescrum2_timebox set last_updated = CURRENT_DATE WHERE last_updated is NULL')
                  addNotNullConstraint(tableName:"icescrum2_timebox",columnName:'last_updated',columnDataType:'DATETIME')
            }
            // MSSQL
            changeSet(id:'timebox_add_constraint_dateCreated_mssql', author:'vbarrier') {
                  preConditions(onFail:"MARK_RAN"){
                      dbms(type:'mssql')
                  }
                  sql('UPDATE icescrum2_timebox set date_created = GETDATE() WHERE date_created is NULL')
                  addNotNullConstraint(tableName:"icescrum2_timebox",columnName:'date_created',columnDataType:'DATETIME')
            }
            changeSet(id:'timebox_add_constraint_lastUpdated_mssql', author:'vbarrier') {
                  preConditions(onFail:"MARK_RAN"){
                      dbms(type:'mssql')
                  }
                  sql('UPDATE icescrum2_timebox set last_updated = GETDATE() WHERE last_updated is NULL')
                  addNotNullConstraint(tableName:"icescrum2_timebox",columnName:'last_updated',columnDataType:'DATETIME')
            }
            changeSet(id:'timebox_change_text_datatype_mssql', author:'nnoullet') {
                  preConditions(onFail:"MARK_RAN"){
                      dbms(type:'mssql')
                  }
                  sql('ALTER TABLE icescrum2_timebox ALTER COLUMN description VARCHAR(MAX)')
                  sql('ALTER TABLE icescrum2_timebox ALTER COLUMN goal VARCHAR(MAX)')
            }
            // OTHERS
            changeSet(id:'timebox_add_constraint_dateCreated_sql', author:'vbarrier') {
                  preConditions(onFail:"MARK_RAN"){
                      not{
                          or {
                              dbms(type:'hsqldb')
                              dbms(type:'postgresql')
                              dbms(type:'mssql')
                          }
                      }
                  }
                  sql('UPDATE icescrum2_timebox set date_created = CURRENT_DATE() WHERE date_created is NULL')
                  addNotNullConstraint(tableName:"icescrum2_timebox",columnName:'date_created',columnDataType:'DATETIME')
            }
            changeSet(id:'timebox_add_constraint_lastUpdated_sql', author:'vbarrier') {
                  preConditions(onFail:"MARK_RAN"){
                      not{
                          or {
                              dbms(type:'hsqldb')
                              dbms(type:'postgresql')
                              dbms(type:'mssql')
                          }
                      }
                  }
                  sql('UPDATE icescrum2_timebox set last_updated = CURRENT_DATE() WHERE last_updated is NULL')
                  addNotNullConstraint(tableName:"icescrum2_timebox",columnName:'last_updated',columnDataType:'DATETIME')
            }
        }
}