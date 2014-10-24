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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */
package org.icescrum.core.domain

class ProductTeamsAndFluxiableMigration {

    static migration = {
        changeSet(id:'product_teams_mssql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'mssql')
                not{
                    tableExists(tableName:"icescrum2_product_teams")
                }
            }
            sql('create table icescrum2_product_teams (product_id numeric(19,0) not null, team_id numeric(19,0) not null, primary key (product_id, team_id))')
            sql('alter table icescrum2_product_teams add constraint FK_PRODUCT_TEAMS_PRODUCT foreign key (product_id) references icescrum2_product')
            sql('alter table icescrum2_product_teams add constraint FK_PRODUCT_TEAMS_TEAM foreign key (team_id) references icescrum2_team')
        }

        // TODO check R7
        changeSet(id:'fluxiable_mssql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'mssql')
            }
            sql('alter table fluxiable_activity alter column cached_description varchar(max)')
            sql('alter table fluxiable_activity alter column cached_label varchar(max)')
        }
    }

    static def getFilePath(){
        return ""
    }

}

