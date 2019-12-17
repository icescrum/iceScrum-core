/*
 * Copyright (c) 2011 Kagilum SAS
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.domain

import org.hibernate.ObjectNotFoundException
import org.icescrum.core.utils.ServicesUtils

class AcceptanceTest implements Serializable {

    String name
    String description
    int uid

    Date dateCreated
    Date lastUpdated

    Integer rank = 0

    int state = AcceptanceTestState.TOCHECK.id

    SortedSet<Activity> activities
    Set<MetaData> metaDatas

    static belongsTo = [
            creator    : User,
            parentStory: Story
    ]

    static hasMany = [activities: Activity, metaDatas: MetaData]

    static constraints = {
        description(nullable: true, maxSize: 1000)
        rank(nullable: true)
        name(blank: false)
    }

    static mapping = {
        cache true
        table 'is_acceptance_test'
        metaDatas cascade: 'delete-orphan'
        activities cascade: 'delete-orphan'
    }

    static transients = ['parentProject', 'stateEnum']

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(t.uid)
                   FROM org.icescrum.core.domain.AcceptanceTest as t, org.icescrum.core.domain.Story as s
                   WHERE t.parentStory = s
                   AND s.backlog.id = :pid """, [pid: pid])[0] ?: 0) + 1
    }

    static AcceptanceTest getInProject(projectId, id) {
        def results = executeQuery(
                """SELECT at
                   FROM org.icescrum.core.domain.AcceptanceTest as at
                   WHERE at.id = :id
                   AND at.parentStory.backlog.id = :pid """, [id: id, pid: projectId])
        results ? results.first() : null
    }

    static AcceptanceTest getInProjectWithUid(projectId, uid) {
        def results = executeQuery(
                """SELECT at
                   FROM org.icescrum.core.domain.AcceptanceTest as at
                   WHERE at.uid = :uid
                   AND at.parentStory.backlog.id = :pid """, [uid: uid, pid: projectId])
        results ? results.first() : null
    }

    static List<AcceptanceTest> getAllInProject(projectId, term = '', states = []) {
        executeQuery(
                """SELECT at
                   FROM org.icescrum.core.domain.AcceptanceTest as at
                   WHERE at.parentStory.backlog.id = :pid 
                   AND (at.name LIKE :term OR at.description LIKE :term)
                   ${states ? "AND at.state in (${states.join(',')})" : ''}
                   ORDER BY at.parentStory.state""", [pid: projectId, term: '%' + term + '%']
        )
    }

    static List<AcceptanceTest> getAllInStory(projectId, storyId, term = '', states = []) {
        executeQuery(
                """SELECT at
                   FROM org.icescrum.core.domain.AcceptanceTest as at
                   WHERE at.parentStory.backlog.id = :pid
                   AND (at.name LIKE :term OR at.description LIKE :term)
                   AND at.parentStory.id = :sid
                   ${states ? "AND at.state in (${states.join(',')})" : ''}
                   ORDER BY at.rank, at.uid""", [sid: storyId, pid: projectId, term: '%' + term + '%'])
    }

    static List<AcceptanceTest> getAllInSprint(projectId, sprintId, term = '', states = []) {
        executeQuery(
                """SELECT at
                   FROM org.icescrum.core.domain.AcceptanceTest as at
                   WHERE at.parentStory.backlog.id = :pid
                   AND (at.name LIKE :term OR at.description LIKE :term)
                   AND at.parentStory.parentSprint.id = :sid
                   ${states ? "AND at.state in (${states.join(',')})" : ''}
                   ORDER BY at.parentStory.rank, at.rank, at.uid""", [sid: sprintId, pid: projectId, term: '%' + term + '%'])
    }

    static AcceptanceTest withAcceptanceTest(long projectId, long id) {
        AcceptanceTest acceptanceTest = getInProject(projectId, id)
        if (!acceptanceTest) {
            throw new ObjectNotFoundException(id, 'AcceptanceTest')
        }
        return acceptanceTest
    }

    static AcceptanceTest findByProjectIdAndUid(long projectId, int uid) {
        AcceptanceTest acceptanceTest = getInProjectWithUid(projectId, uid)
        if (!acceptanceTest) {
            throw new ObjectNotFoundException(uid, 'AcceptanceTest')
        }
        return acceptanceTest
    }

    @Override
    boolean equals(o) {
        if (this.is(o)) return true;
        if (getClass() != o.class) return false;

        AcceptanceTest that = (AcceptanceTest) o;

        if (uid != that.uid) return false;
        if (dateCreated != that.dateCreated) return false;
        if (description != that.description) return false;
        if (lastUpdated != that.lastUpdated) return false;
        if (name != that.name) return false;

        return true;
    }

    @Override
    int hashCode() {
        int result;
        result = (name != null ? name.hashCode() : 0);
        result = 31 * result + (description != null ? description.hashCode() : 0);
        result = 31 * result + uid;
        result = 31 * result + (dateCreated != null ? dateCreated.hashCode() : 0);
        result = 31 * result + (lastUpdated != null ? lastUpdated.hashCode() : 0);
        return result;
    }

    def getParentProject() {
        return this.parentStory.project
    }

    enum AcceptanceTestState {

        TOCHECK(1),
        FAILED(5),
        SUCCESS(10)

        final Integer id

        static AcceptanceTestState byId(Integer id) { values().find { AcceptanceTestState stateEnum -> stateEnum.id == id } }

        static boolean exists(Integer id) { values().id.contains(id) }

        static Map asMap() {
            Map entries = [:]
            values().each {
                entries[it.id] = it.toString()
            }
            entries
        }

        private AcceptanceTestState(Integer id) { this.id = id }

        String toString() { "is.acceptanceTest.state." + name().toLowerCase() }
    }

    AcceptanceTestState getStateEnum() {
        AcceptanceTestState.byId(state)
    }

    void setStateEnum(AcceptanceTestState stateEnum) {
        state = stateEnum.id
    }

    def beforeValidate() {
        name = ServicesUtils.cleanXml(name)
        description = ServicesUtils.cleanXml(description)
    }

    def xml(builder) {
        builder.acceptanceTest(uid: this.uid) {
            builder.state(this.state)
            builder.rank(this.rank)
            builder.creator(uid: this.creator.uid)
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }
            builder.activities() {
                this.activities.each { _activity ->
                    _activity.xml(builder)
                }
            }
            exportDomainsPlugins(builder)
        }
    }
}
