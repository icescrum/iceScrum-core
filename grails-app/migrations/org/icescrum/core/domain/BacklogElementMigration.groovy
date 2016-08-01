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
        changeSet(id:'remove_id_from_import_column_feature', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                columnExists(tableName:"icescrum2_feature", columnName:"id_from_import")
            }
            dropColumn(tableName:"icescrum2_feature", columnName:"id_from_import")
        }
        changeSet(id:'remove_id_from_import_column_task', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                columnExists(tableName:"icescrum2_task", columnName:"id_from_import")
            }
            dropColumn(tableName:"icescrum2_task", columnName:"id_from_import")
        }
        changeSet(id:'remove_id_from_import_column_story', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                columnExists(tableName:"icescrum2_story", columnName:"id_from_import")
            }
            dropColumn(tableName:"icescrum2_story", columnName:"id_from_import")
        }
        changeSet(id:'remove_id_from_import_column_impediment', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                columnExists(tableName:"icescrum2_impediment", columnName:"id_from_import")
            }
            dropColumn(tableName:"icescrum2_impediment", columnName:"id_from_import")
        }
        changeSet(id:'add_uid_column_backlogelement', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not {
                    dbms(type:'oracle')
                }
            }
            sql('UPDATE icescrum2_task set uid = id WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_task",columnName:'uid',columnDataType:'BIGINT')
            sql('UPDATE icescrum2_actor set uid = id WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_actor",columnName:'uid',columnDataType:'BIGINT')
            sql('UPDATE icescrum2_feature set uid = id WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_feature",columnName:'uid',columnDataType:'BIGINT')
            sql('UPDATE icescrum2_story set uid = id WHERE uid is NULL')
            addNotNullConstraint(tableName:"icescrum2_story",columnName:'uid',columnDataType:'BIGINT')
        }
        changeSet(id:'remove_not_null_origin_story', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not {
                    dbms(type:'oracle')
                }
            }
            dropNotNullConstraint(tableName:"icescrum2_story", columnName:"origin", columnDataType:'VARCHAR(255)')
        }
        changeSet(id:'story_remove_template', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not{
                    dbms(type:'oracle')
                }
                columnExists(tableName:"icescrum2_story", columnName:"text_as")
                columnExists(tableName:"icescrum2_story", columnName:"textican")
                columnExists(tableName:"icescrum2_story", columnName:"text_to")
                sqlCheck("SELECT count(*) FROM icescrum2_story WHERE text_as IS NOT NULL OR textican IS NOT NULL OR text_to IS NOT NULL",expectedResult:'0')
            }
            dropColumn(tableName:"icescrum2_story", columnName:"text_as")
            dropColumn(tableName:"icescrum2_story", columnName:"textican")
            dropColumn(tableName:"icescrum2_story", columnName:"text_to")
        }

        // Acceptance tests states
        changeSet(id:'add_acceptance_test_state_success_on_done_stories', author:'vbarrier', filePath:filePath) {
            sql('UPDATE acceptance_test SET state = 10 WHERE EXISTS (SELECT * FROM icescrum2_story WHERE acceptance_test.parent_story_id = icescrum2_story.id AND icescrum2_story.state = 7)')
        }
        changeSet(id:'add_acceptance_test_state_tocheck_on_remaining_tests', author:'vbarrier', filePath:filePath) {
            sql('UPDATE acceptance_test SET state = 1 WHERE state IS NULL')
        }
        changeSet(id:'add_acceptance_test_state_non_nullable', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN"){
                not {
                    dbms(type:'oracle')
                }
            }
            addNotNullConstraint(tableName:"acceptance_test", columnName:'state', columnDataType:'INTEGER')
        }

        changeSet(id:'story_effort_from_integer_to_numeric', author:'vbarrier', filePath:filePath) {
            modifyDataType(tableName:"icescrum2_story", columnName:'effort', newDataType:'NUMERIC(5,2)')
        }

        changeSet(id:'add_IX_fluxiable_activity_link_activity_ref', author:'vbarrier', filePath:filePath) {
            preConditions(onFail:"MARK_RAN") {
                not {
                    dbms(type:'oracle')
                }
            }
            createIndex(indexName: 'IX_fluxiable_activity_link_activity_ref', tableName: 'fluxiable_activity_link') {
                column(name:'activity_ref', type:'BIGINT')
            }
        }
    }

    static def getFilePath(){
        return ""
    }
}

