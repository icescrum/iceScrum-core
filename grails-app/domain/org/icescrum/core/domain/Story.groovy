/*
 * Copyright (c) 2014 Kagilum SAS.
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

import grails.plugin.springsecurity.SpringSecurityService
import grails.util.GrailsNameUtils
import grails.util.Holders
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.AcceptanceTest.AcceptanceTestState


class Story extends BacklogElement implements Cloneable, Serializable {

    static final long serialVersionUID = -6800252507987149001L

    static final int STATE_SUGGESTED = 1
    static final int STATE_ACCEPTED = 2
    static final int STATE_ESTIMATED = 3
    static final int STATE_PLANNED = 4
    static final int STATE_INPROGRESS = 5
    static final int STATE_DONE = 7
    static final int TYPE_USER_STORY = 0
    static final int TYPE_DEFECT = 2
    static final int TYPE_TECHNICAL_STORY = 3

    int type = 0
    Date suggestedDate
    Date acceptedDate
    Date plannedDate
    Date estimatedDate
    Date inProgressDate
    Date doneDate
    String origin
    BigDecimal effort = null
    int rank = 0
    int state = Story.STATE_SUGGESTED
    int value = 0
    String affectVersion

    static belongsTo = [
            creator: User,
            feature: Feature,
            parentSprint: Sprint,
            actor: Actor,
            dependsOn: Story
    ]

    static hasMany = [
            tasks: Task,
            acceptanceTests: AcceptanceTest,
            likers: User,
            followers: User,
            dependences: Story
    ]

    static mappedBy = [
            tasks: 'parentStory' // Todo check if can be removed, probably yes because there is no ambiguity in mapping: task is related to story only with "parentStory"
    ]

    static transients = [
            'deliveredVersion', 'testState', 'testStateEnum', 'activity', 'liked', 'followed', 'sameBacklogStories', 'countDoneTasks'
    ]

    static mapping = {
        cache true
        table 'is_story'
        tasks cascade: 'all'
        dependences cache: true, sort: "state", order: "asc"
        acceptanceTests sort: 'uid'
        effort precision: 5, scale: 2
    }

    static constraints = {
        suggestedDate(nullable: true)
        acceptedDate(nullable: true)
        estimatedDate(nullable: true)
        plannedDate(nullable: true)
        inProgressDate(nullable: true)
        doneDate(nullable: true)
        parentSprint(nullable: true, validator: { newSprint, story -> newSprint == null || newSprint.parentProduct.id == story.backlog.id ?: 'invalid'})
        feature(nullable: true, validator: { newFeature, story -> newFeature == null || newFeature.backlog.id == story.backlog.id ?: 'invalid' })
        actor(nullable: true, validator: { newActor, story -> newActor == null || newActor.backlog.id == story.backlog.id ?: 'invalid' })
        affectVersion(nullable: true)
        effort(nullable: true, validator: { newEffort, story -> newEffort == null || (newEffort >= 0 && newEffort < 1000) ?: 'invalid' })
        creator(nullable: true) // in case of a user deletion, the story can remain without owner
        dependsOn(nullable: true, validator: { newDependsOn, story -> newDependsOn == null || (newDependsOn.backlog.id == story.backlog.id && newDependsOn.state >= story.state) ?: 'invalid' })
        origin(nullable: true)
    }

    def getActivity() {
        def activities = this.activities + this.tasks*.activities.flatten() + this.acceptanceTests*.activities.flatten()
        return activities.sort { a, b -> b.dateCreated <=> a.dateCreated }
    }

    def getDeliveredVersion() {
        return this.state == STATE_DONE ? this.parentSprint.deliveredVersion ?: null : null
    }

    def getCountDoneTasks() {
        return state >= STATE_INPROGRESS ? tasks.count { it.state == Task.STATE_DONE } : 0;
    }

    List<Story> getSameBacklogStories() {
        def stories
        if (state == STATE_SUGGESTED) {
            stories = backlog.stories.findAll {
                it.state == STATE_SUGGESTED
            }
        } else if (state in [STATE_ACCEPTED, STATE_ESTIMATED]) {
            stories = backlog.stories.findAll {
                it.state in [STATE_ACCEPTED, STATE_ESTIMATED]
            }
        } else if (state > STATE_ESTIMATED) {
            stories = parentSprint?.stories
        }
        return stories ? stories.asList().sort { it.rank } : []
    }

    int getTestState() {
        getTestStateEnum().id
    }

    boolean getLiked() {
        def springSecurityService = (SpringSecurityService) Holders.grailsApplication.mainContext.getBean('springSecurityService')
        return likers ? likers.contains(springSecurityService.currentUser) : false
    }

    boolean getFollowed() {
        def springSecurityService = (SpringSecurityService) Holders.grailsApplication.mainContext.getBean('springSecurityService')
        return followers ? followers.contains(springSecurityService.currentUser) : false
    }

    static namedQueries = {

        findInStoriesAcceptedEstimated { p, term ->
            backlog {
                eq 'id', p
            }
            or {
                def termInteger = term?.replaceAll('%', '')
                if (termInteger?.isInteger()) {
                    eq 'uid', termInteger.toInteger()
                } else {
                    ilike 'name', term
                    ilike 'description', term
                    ilike 'notes', term
                    ilike 'affectVersion', term
                    feature {
                        ilike 'name', term
                    }
                }
            }
            and {
                or {
                    eq 'state', Story.STATE_ACCEPTED
                    eq 'state', Story.STATE_ESTIMATED
                }
            }
        }

        storiesByRelease { r ->
            parentSprint {
                parentRelease {
                    eq 'id', r.id
                }
            }
        }

        countAllAcceptedOrEstimated { p ->
            backlog {
                eq 'id', p
            }
            or {
                eq 'state', Story.STATE_ACCEPTED
                eq 'state', Story.STATE_ESTIMATED
            }
            projections {
                rowCount()
            }
        }

        findPossiblesDependences { Story story ->
            backlog {
                eq 'id', story.backlog.id
            }
            or {
                if (story.state == Story.STATE_SUGGESTED) {
                    and {
                        ge 'state', Story.STATE_SUGGESTED
                        ne 'id', story.id
                        if (story.dependences) {
                            not {
                                'in' 'id', story.dependences.collect { it.id }
                            }
                        }
                    }
                }

                if (story.state in [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]) {
                    and {
                        'in' 'state', [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]
                        lt 'rank', story.rank
                    }
                    and {
                        gt 'state', Story.STATE_ESTIMATED
                    }
                } else if (story.state in [Story.STATE_PLANNED, Story.STATE_INPROGRESS]) {
                    and {
                        'in' 'state', [Story.STATE_PLANNED, Story.STATE_INPROGRESS, Story.STATE_DONE]
                        lt 'rank', story.rank
                        parentSprint {
                            eq 'id', story.parentSprint.id
                        }
                    }
                    and {
                        parentSprint {
                            lt 'startDate', story.parentSprint.startDate
                        }
                    }
                }
            }
        }

        filterByFeature { p, f, r = null ->
            backlog {
                eq 'id', p.id
            }
            if (r) {
                parentSprint {
                    parentRelease {
                        eq 'id', r.id
                    }
                }
            }
            feature {
                eq 'id', f.id
            }
        }

        // Return the total number of points in the backlog
        totalPoint { idProduct ->
            projections {
                sum 'effort'
                backlog {
                    eq 'id', idProduct
                }
                isNull 'parentSprint'
                isNull 'effort'
            }
        }

        getInProduct { p, id ->
            backlog {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Story withStory(long productId, long id) {
        Story story = (Story) getInProduct(productId, id).list()
        if (!story) {
            throw new ObjectNotFoundException(id, 'Story')
        }
        return story
    }

    static List<Story> withStories(def params, def id = 'id') {
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Story> stories = ids ? getAll(ids).findAll { it && it.backlog.id == params.product.toLong() } : null
        if (!stories) {
            throw new ObjectNotFoundException(ids, 'Story')
        }
        return stories
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(s.uid)
                   FROM org.icescrum.core.domain.Story as s, org.icescrum.core.domain.Product as p
                   WHERE s.backlog = p
                   AND p.id = :pid """, [pid: pid])[0] ?: 0) + 1
    }

    static findLastUpdatedComment(def element) {
        executeQuery("SELECT c.lastUpdated " +
            "FROM org.grails.comments.Comment as c, org.grails.comments.CommentLink as cl, ${element.class.name} as b " +
            "WHERE c = cl.comment " +
            "AND cl.commentRef = b " +
            "AND cl.type = :type " +
            "AND b.id = :id " +
            "ORDER BY c.lastUpdated DESC",
            [id: element.id, type: GrailsNameUtils.getPropertyName(element.class)],
            [max: 1])[0]
    }

    int compareTo(Story o) {
        return rank.compareTo(o.rank)
    }

    @Override
    int hashCode() {
        final Integer prime = 31
        int result = 1
        result = prime * result + ((!effort) ? 0 : effort.hashCode())
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!backlog) ? 0 : backlog.hashCode())
        result = prime * result + ((!parentSprint) ? 0 : parentSprint.hashCode())
        result = prime * result + ((!state) ? 0 : state.hashCode())
        return result
    }

    @Override
    boolean equals(obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        Story other = (Story) obj
        if (effort == null) {
            if (other.effort != null)
                return false
        } else if (!effort.equals(other.effort))
            return false
        if (name == null) {
            if (other.name != null)
                return false
        } else if (!name.equals(other.name))
            return false
        if (backlog == null) {
            if (other.backlog != null)
                return false
        } else if (!backlog.equals(other.backlog))
            return false
        if (parentSprint == null) {
            if (other.parentSprint != null)
                return false
        } else if (!parentSprint.equals(other.parentSprint))
            return false
        if (state == null) {
            if (other.state != null)
                return false
        } else if (!state.equals(other.state))
            return false
        return true
    }

    static search(product, options, rowCount = false) {
        List<Story> stories = []
        def getList = { it instanceof  List ? it : (it instanceof Object[] ? it as List : [it]) }
        def criteria = {
            if (rowCount) {
                projections {
                    count()
                }
            }
            backlog {
                eq 'id', product
            }
            if (options.story) {
                if (options.story.term) {
                    or {
                        if (options.story.term instanceof List) {
                            options.story.term.each {
                                ilike 'name', '%' + it + '%'
                                ilike 'description', '%' + it + '%'
                                ilike 'notes', '%' + it + '%'
                            }
                        } else if (options.story.term?.isInteger()) {
                            eq 'uid', options.story.term.toInteger()
                        } else {
                            ilike 'name', '%' + options.story.term + '%'
                            ilike 'description', '%' + options.story.term + '%'
                            ilike 'notes', '%' + options.story.term + '%'
                        }
                    }
                }
                if (options.story.feature) {
                    feature {
                        or {
                            getList(options.story.feature).each { feature ->
                                eq 'id', new Long(feature)
                            }
                        }
                    }
                }
                if (options.story.actor) {
                    actor {
                        or {
                            getList(options.story.actor).each { actor ->
                                eq 'id', new Long(actor)
                            }
                        }
                    }
                }
                if (options.story.state) {
                    or {
                        getList(options.story.state).each { state ->
                            eq 'state', new Integer(state)
                        }
                    }
                }
                if (options.story.parentRelease) {
                    parentSprint {
                        parentRelease {
                            or {
                                getList(options.story.parentRelease).each { parentRelease ->
                                    eq 'id', new Long(parentRelease)
                                }
                            }
                        }
                    }
                }
                if (options.story.parentSprint) {
                    parentSprint {
                        or {
                            getList(options.story.parentSprint).each { parentSprint ->
                                eq 'id', new Long(parentSprint)
                            }
                        }
                    }
                }
                if (options.story.creator) {
                    creator {
                        or {
                            getList(options.story.creator).each { creator ->
                                eq 'id', new Long(creator)
                            }
                        }
                    }
                }
                if (options.story.type != null) { // Be careful type user story is 0 so it is falsy
                    or {
                        getList(options.story.type).each { type ->
                            eq 'type', new Integer(type)
                        }
                    }
                }
                if (options.story.dependsOn) {
                    dependsOn {
                        or {
                            getList(options.story.dependsOn).each { dependsOn ->
                                eq 'id', new Long(dependsOn)
                            }
                        }
                    }
                }
                if (options.story.effort) {
                    or {
                        getList(options.story.effort).each { effort ->
                            eq 'effort', new BigDecimal(effort)
                        }
                    }
                }
                if (options.story.affectedVersion) {
                    or {
                        getList(options.story.affectedVersion).each { affectedVersion ->
                            eq 'affectVersion', affectedVersion
                        }
                    }
                }
                if (options.story.deliveredVersion) {
                    parentSprint {
                        or {
                            getList(options.story.deliveredVersion).each { deliveredVersion ->
                                eq 'deliveredVersion', deliveredVersion
                            }
                        }
                    }
                }
            }
        }
        def criteriaCall = {
            criteria.delegate = delegate
            criteria.call()
        }
        if (options.story?.tag) {
            stories = Story.findAllByTagsWithCriteria(getList(options.story.tag), criteriaCall)
        } else if (options.story != null) {
            stories = Story.createCriteria().list(options.list ?: [:], criteriaCall)
        }
        if (rowCount) {
            return stories ? stories.get(0) : 0
        } else {
            return stories ?: Collections.EMPTY_LIST
        }
    }

    static searchByTermOrTag(productId, searchOptions, term) {
        search(productId, addTermOrTagToSearch(searchOptions, term))
    }

    static searchAllByTermOrTag(productId, term) {
        def searchOptions = [story: [:]]
        searchByTermOrTag(productId, searchOptions, term)
    }

    static searchByTermOrTagInSandbox(productId, term) {
        def searchOptions = [story: [state: STATE_SUGGESTED.toString()]]
        searchByTermOrTag(productId, searchOptions, term)
    }

    static searchByTermOrTagInBacklog(product, term) {
        def stories
        if (term) {
            if (hasTagKeyword(term)) {
                stories = search(product.id, [tag: removeTagKeyword(term)]).findAll { it.state in [STATE_ACCEPTED, STATE_ESTIMATED] }
            } else {
                stories = findInStoriesAcceptedEstimated(product.id, '%' + term + '%').list()
            }
        } else {
            stories = findAllByBacklogAndStateBetween(product, STATE_ACCEPTED, STATE_ESTIMATED, [cache: true, sort: 'rank'])
        }
        stories
    }

    enum TestState {
        NOTEST(0),
        TOCHECK(1),
        FAILED(5),
        SUCCESS(10)

        final Integer id
        static TestState byId(Integer id) { values().find { TestState stateEnum -> stateEnum.id == id } }
        private TestState(Integer id) { this.id = id }
        String toString() { "is.story.teststate." + name().toLowerCase() }
    }

    TestState getTestStateEnum() {
        Map testsByStateCount = countTestsByState()
        if (testsByStateCount.size() == 0) {
            TestState.NOTEST
        } else if (testsByStateCount[AcceptanceTestState.FAILED] > 0) {
            TestState.FAILED
        } else if (testsByStateCount[AcceptanceTestState.TOCHECK] > 0) {
            TestState.TOCHECK
        } else {
            TestState.SUCCESS
        }
    }

    Map countTestsByState() {
        // Criteria didn't work because sort on acceptanceTests uid isn't in "group by" clause
        Story.executeQuery("""
            SELECT test.state, COUNT(test.id)
            FROM Story story INNER JOIN story.acceptanceTests AS test
            WHERE story.id = :id
            GROUP BY test.state
            ORDER BY test.state ASC """, [id: id, cache: true]
        ).inject([:]) { countByState, group ->
            def (state, stateCount) = group
            if (AcceptanceTestState.exists(state)) {
                countByState[AcceptanceTestState.byId(state)] = stateCount
            }
            countByState
        }
    }

    Boolean canUpdate(isProductOwner, currentUser) {
        return isProductOwner || ((state == STATE_SUGGESTED && currentUser == creator))
    }

    def xml(builder) {
        builder.story(uid: this.uid) {
            type(this.type)
            rank(this.rank)
            state(this.state)
            value(this.value)
            effort(this.effort)
            doneDate(this.doneDate)
            plannedDate(this.plannedDate)
            acceptedDate(this.acceptedDate)
            todoDate(this.todoDate)
            suggestedDate(this.suggestedDate)
            estimatedDate(this.estimatedDate)
            inProgressDate(this.inProgressDate)

            tags { builder.mkp.yieldUnescaped("<![CDATA[${this.tags}]]>") }
            name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            notes { builder.mkp.yieldUnescaped("<![CDATA[${this.notes ?: ''}]]>") }
            description { builder.mkp.yieldUnescaped("<![CDATA[${this.description ?: ''}]]>") }

            creator(uid: this.creator.uid)
            if (this.feature) {
                feature(uid: this.feature.uid)
            }
            if (this.actor) {
                actor(uid: this.actor.uid)
            }
            if (dependsOn) {
                dependsOn(uid: this.dependsOn.uid)
            }

            comments() {
                this.comments.each { _comment ->
                    comment() {
                        dateCreated(_comment.dateCreated)
                        posterId(_comment.posterId)
                        posterClass(_comment.posterClass)
                        body { builder.mkp.yieldUnescaped("<![CDATA[${_comment.body}]]>") }
                    }
                }
            }
            activities() {
                this.activities.each { _activity ->
                    activity() {
                        code(_activity.code)
                        poster(uid: _activity.poster.uid)
                        dateCreated(_activity.dateCreated)
                        label { builder.mkp.yieldUnescaped("<![CDATA[${_activity.label}]]>") }
                        field { _activity.field }
                        beforeValue { builder.mkp.yieldUnescaped("<![CDATA[${_activity.beforeValue}]]>") }
                        afterValue { builder.mkp.yieldUnescaped("<![CDATA[${_activity.afterValue}]]>") }
                    }
                }
            }
            acceptanceTests() {
                this.acceptanceTests.each { _acceptanceTest ->
                    _acceptanceTest.xml(builder)
                }
            }
            attachments() {
                this.attachments.each { _att ->
                    _att.xml(builder)
                }
            }
        }
    }
}