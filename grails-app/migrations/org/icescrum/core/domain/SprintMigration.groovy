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

class SprintMigration {

    static SELECT_FANTOM_SPRINTS = """
        SELECT DISTINCT sprint1.id
        FROM icescrum2_sprint sprint1, icescrum2_timebox timebox1, icescrum2_sprint sprint2, icescrum2_timebox timebox2
        WHERE sprint1.parent_release_id = sprint2.parent_release_id
        AND sprint1.id != sprint2.id
        AND sprint1.id = timebox1.id
        AND sprint2.id = timebox2.id
        AND timebox1.order_number = timebox2.order_number
        AND NOT EXISTS (
            SELECT story.id
            FROM icescrum2_story story
            WHERE story.parent_sprint_id = sprint1.id)
        AND NOT EXISTS (
            SELECT task.id
            FROM icescrum2_task task
            WHERE task.backlog_id = sprint1.id)
        AND sprint1.id != (
            SELECT MIN(sprint3.id)
            FROM icescrum2_sprint sprint3, icescrum2_timebox timebox3
            WHERE sprint3.parent_release_id = sprint1.parent_release_id
            AND sprint3.id = timebox3.id
            AND timebox3.order_number = timebox1.order_number)
        AND sprint1.state = 1
    """

    static migration = {
        changeSet(id:'remove_resource_column_sprint', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                columnExists(tableName:"icescrum2_sprint", columnName:"resource")
            }
            dropColumn(tableName:"icescrum2_sprint", columnName:"resource")
        }
        changeSet(id:'remove_fantom_sprint_availabilities', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                tableExists(tableName:"icescrum2_availability")
            }
            sql('DELETE FROM icescrum2_availability WHERE sprint_id IN (' + SELECT_FANTOM_SPRINTS + ')')
        }
        changeSet(id:'remove_fantom_sprint_timebox', author:'vbarrier', filePath:filePath) {
            sql('DELETE FROM icescrum2_timebox WHERE id IN (SELECT * FROM (' + SELECT_FANTOM_SPRINTS + ') AS TEMP)')
        }
        changeSet(id:'remove_fantom_sprint_sprints', author:'vbarrier', filePath:filePath) {
            sql('''
                DELETE FROM icescrum2_sprint
                WHERE icescrum2_sprint.state = 1
                AND NOT EXISTS (
                    SELECT timebox.id
                    FROM icescrum2_timebox timebox
                    WHERE timebox.id = icescrum2_sprint.id)
                AND NOT EXISTS (
                    SELECT story.id
                    FROM icescrum2_story story
                    WHERE story.parent_sprint_id = icescrum2_sprint.id)
                AND NOT EXISTS (
                    SELECT task.id
                    FROM icescrum2_task task
                    WHERE task.backlog_id = icescrum2_sprint.id)
            ''')
        }
        changeSet(id:'rename_initial_remaining_hours_column', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                    dbms(type:'hsqldb')
                }
                columnExists(tableName:"icescrum2_sprint", columnName:'initial_remaining_hours')
            }
            renameColumn(tableName:"icescrum2_sprint", oldColumnName:'initial_remaining_hours', newColumnName:"initial_remaining_time", columnDataType:'FLOAT')
        }

        changeSet(id:'rename_initial_remaining_hours_column_hsql', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                dbms(type:'hsqldb')
                sqlCheck("SELECT count(COLUMN_NAME) FROM INFORMATION_SCHEMA.SYSTEM_COLUMNS WHERE TABLE_NAME = 'ICESCRUM2_SPRINT' AND COLUMN_NAME = 'INITIAL_REMAINING_HOURS'",expectedResult:'1')
            }
            renameColumn(tableName:"icescrum2_sprint", oldColumnName:'initial_remaining_hours', newColumnName:"initial_remaining_time", columnDataType:'FLOAT')
        }
    }

    static def getFilePath(){
        return ""
    }
}

