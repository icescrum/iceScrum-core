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



class ProductPreferencesMigration {
    static migration = {
      changeSet(id:'product_preferences_constraint_releasePlanningHour_column', author:'vbarrier') {
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'release_planning_hour',columnDataType:'varchar(255)',defaultNullValue:'9:00')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'daily_meeting_hour',columnDataType:'varchar(255)',defaultNullValue:'11:00')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'sprint_planning_hour',columnDataType:'varchar(255)',defaultNullValue:'9:00')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'sprint_retrospective_hour',columnDataType:'varchar(255)',defaultNullValue:'15:00')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'sprint_review_hour',columnDataType:'varchar(255)',defaultNullValue:'14:00')
      }

      changeSet(id:'product_preferences_constraint_hideweekend_column', author:'vbarrier') {
          preConditions(onFail:"MARK_RAN"){
            not{
              dbms(type:'mssql')
            }
          }
          sql('UPDATE icescrum2_product_preferences set hide_weekend = false WHERE hide_weekend is NULL')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'hide_weekend',columnDataType:'BOOLEAN')
      }

      changeSet(id:'product_preferences_constraint_hideweekend_column_mssql', author:'vbarrier') {
          preConditions(onFail:"MARK_RAN"){
            dbms(type:'mssql')
          }
          sql('UPDATE icescrum2_product_preferences set hide_weekend = 0 WHERE hide_weekend is NULL')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'hide_weekend',columnDataType:'BIT')
      }

      changeSet(id:'product_preferences_drop_lock_po_column', author:'vbarrier') {
        preConditions(onFail:"MARK_RAN"){
            columnExists(tableName:"icescrum2_product_preferences", columnName:"lock_po")
        }
        dropColumn(tableName:"icescrum2_product_preferences", columnName:"lock_po")
      }

      changeSet(id:'product_preferences_drop_new_teams_column', author:'vbarrier') {
        preConditions(onFail:"MARK_RAN"){
            columnExists(tableName:"icescrum2_product_preferences", columnName:"new_teams")
        }
        dropColumn(tableName:"icescrum2_product_preferences", columnName:"new_teams")
      }

      changeSet(id:'product_preferences_constraint_R3_R4_column', author:'vbarrier') {
          preConditions(onFail:"MARK_RAN"){
            not{
              dbms(type:'mssql')
            }
          }
          sql('UPDATE icescrum2_product_preferences set archived = false WHERE archived is NULL')
          sql('UPDATE icescrum2_product_preferences set webservices = false WHERE webservices is NULL')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'webservices',columnDataType:'BOOLEAN')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'archived',columnDataType:'BOOLEAN')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'timezone',columnDataType:'varchar(255)',defaultNullValue:'UTC')
      }

      changeSet(id:'product_preferences_constraint_R3_R4_column_mssql', author:'vbarrier') {
          preConditions(onFail:"MARK_RAN"){
            dbms(type:'mssql')
          }
          sql('UPDATE icescrum2_product_preferences set archived = 0 WHERE archived is NULL')
          sql('UPDATE icescrum2_product_preferences set webservices = 0 WHERE webservices is NULL')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'webservices',columnDataType:'BIT')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'archived',columnDataType:'BIT')
          addNotNullConstraint(tableName:"icescrum2_product_preferences",columnName:'timezone',columnDataType:'varchar(255)',defaultNullValue:'UTC')
      }

        changeSet(id:'product_preferences_default_stakeHolderRestrictedViews', author:'vbarrier') {
            sql("UPDATE icescrum2_product_preferences set stake_holder_restricted_views = 'sprintPlan,actor,feature,finder' where stake_holder_restricted_views is NULL")
        }
    }
}

