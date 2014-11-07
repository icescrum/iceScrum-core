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
    static final int EXECUTION_FREQUENCY_HOUR = 0
    static final int EXECUTION_FREQUENCY_DAY = 1
    static final int EXECUTION_FREQUENCY_WEEK = 2
    static final int EXECUTION_FREQUENCY_MONTH = 3

    int type = 0
    int executionFrequency = Story.EXECUTION_FREQUENCY_DAY
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
    Story dependsOn

    static belongsTo = [
            creator: User,
            feature: Feature,
            parentSprint: Sprint,
            actor: Actor
    ]

    static hasMany = [
            tasks: Task,
            acceptanceTests: AcceptanceTest,
            likers: User,
            followers: User,
    ]

    static mappedBy = [
            tasks: 'parentStory'
    ]

    static transients = [
            'todo', 'dependences', 'deliveredVersion', 'testState', 'testStateEnum', 'activity', 'liked', 'followed'
    ]

    static mapping = {
        cache true
        table 'icescrum2_story'
        tasks cascade: 'all'
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
        parentSprint(nullable: true)
        feature(nullable: true, validator: { newFeature, story -> newFeature == null || newFeature.backlog == story.backlog }) // TODO custom message
        actor(nullable: true)
        affectVersion(nullable: true)
        effort(nullable: true, validator: { newEffort, story -> newEffort == null || (newEffort >= 0 && newEffort < 1000) }) // TODO custom message
        creator(nullable: true) // in case of a user deletion, the story can remain without owner
        dependsOn(nullable: true, validator: { newDependsOn, story -> newDependsOn == null || newDependsOn.backlog == story.backlog }) // TODO custom message
        origin(nullable: true)
    }

    def getActivity(){
        def activities = this.activities + this.tasks*.activities.flatten() + this.acceptanceTests*.activities.flatten()
        return activities.sort { a, b-> b.dateCreated <=> a.dateCreated }
    }

    def getDependences(){
        return Story.findAllByDependsOn(this,[cache:true,sort:"state",order:"asc"])
    }

    def getDeliveredVersion(){
        return this.state == STATE_DONE ? this.parentSprint.deliveredVersion ?: null : null
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

        findInStoriesAcceptedEstimated {p, term ->
            backlog {
                eq 'id', p
            }
            or {
                def termInteger = term?.replaceAll('%','')
                if (termInteger?.isInteger()){
                    eq 'uid', termInteger.toInteger()
                } else{
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

        findAllStoriesInSprints { p ->
            parentSprint {
                parentRelease {
                    parentProduct {
                        eq 'id', p.id
                    }
                    order('orderNumber')
                }
                order('orderNumber')
            }
            order('rank')
        }

        findNextStoryInSprints {p ->
            parentSprint {
                parentRelease {
                    parentProduct {
                        eq 'id', p.id
                    }
                    order('orderNumber')
                }
                order('orderNumber')
            }
            order('rank')
        }

        findPreviousStoryInSprints {p ->
            parentSprint {
                parentRelease {
                    parentProduct {
                        eq 'id', p.id
                    }
                    order('orderNumber')
                }
                order('orderNumber')
            }
            order('rank')
        }

        storiesByRelease { r ->
            parentSprint {
                parentRelease {
                    eq 'id', r.id
                }
            }
        }

        findPreviousSuggested { p, d ->
            backlog {
                eq 'id', p
            }
            eq 'state', Story.STATE_SUGGESTED
            gt 'suggestedDate', d
            maxResults(1)
            order("suggestedDate", "asc")
        }

        findFirstSuggested { p ->
            backlog {
                eq 'id', p
            }
            eq 'state', Story.STATE_SUGGESTED
            maxResults(1)
            order("suggestedDate", "asc")
        }

        findNextSuggested { p, d, u = null ->
            backlog {
                eq 'id', p
            }
            if (u){
                creator {
                    eq 'id', u
                }
            }
            eq 'state', Story.STATE_SUGGESTED
            lt 'suggestedDate', d
            maxResults(1)
            order("suggestedDate", "desc")
        }

        findNextAcceptedOrEstimated { p, r ->
            backlog {
                eq 'id', p
            }
            or {
                eq 'state', Story.STATE_ACCEPTED
                eq 'state', Story.STATE_ESTIMATED
            }
            eq 'rank', r + 1
            maxResults(1)
        }

        findPreviousAcceptedOrEstimated { p, r ->
            backlog {
                eq 'id', p
            }
            or {
                eq 'state', Story.STATE_ACCEPTED
                eq 'state', Story.STATE_ESTIMATED
            }
            eq 'rank', r - 1
            maxResults(1)
        }

        findNextStoryBySprint { s, r ->
            parentSprint {
                eq 'id', s
            }
            or {
                ne 'state', Story.STATE_DONE
            }
            eq 'rank', r + 1
            maxResults(1)
        }

        findLastAcceptedOrEstimated { p ->
            backlog {
                eq 'id', p
            }
            or {
                eq 'state', Story.STATE_ACCEPTED
                eq 'state', Story.STATE_ESTIMATED
            }
            maxResults(1)
            order("rank", "desc")
        }

        findAllAcceptedOrEstimated { p ->
            backlog {
                eq 'id', p
            }
            or {
                eq 'state', Story.STATE_ACCEPTED
                eq 'state', Story.STATE_ESTIMATED
            }
        }

        findLastBySprint { s ->
            parentSprint {
                eq 'id', s
            }
            maxResults(1)
            order("rank", "desc")
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

        findStoriesFilter { s, term = null, u = null, userid = null ->
            cache false
            parentSprint {
                eq 'id', s.id
            }

            or {
                def termInteger = term?.replaceAll('%','')
                if (termInteger?.isInteger()){
                    eq 'uid', termInteger.toInteger()
                }
                tasks {
                    if (term) {
                        or {
                            if (termInteger?.isInteger()){
                                eq 'uid', termInteger.toInteger()
                            }else{
                                ilike 'name', term
                                ilike 'description', term
                                ilike 'notes', term
                            }
                        }
                    }
                    if (userid) {
                        responsible {
                            eq 'id', userid
                        }
                    } else if (u) {
                        responsible {
                            if (u.preferences.filterTask == 'myTasks') {
                                eq 'id', u.id
                            }
                        }
                        if (u.preferences.filterTask == 'freeTasks') {
                            isNull('responsible')
                        }
                        if (u.preferences.filterTask == 'blockedTasks') {
                            eq 'blocked', true
                        }
                    }
                }
                if (term) {
                    feature {
                        ilike 'name', term
                    }
                }
            }
            if (u?.preferences?.hideDoneState && s?.state == Sprint.STATE_INPROGRESS) {
                ne 'state', Story.STATE_DONE
            }
        }

        findPossiblesDependences{ Story story ->
            backlog {
                eq 'id', story.backlog.id
            }
            or{
                if (story.state == Story.STATE_SUGGESTED){
                    and {
                        ge 'state', Story.STATE_SUGGESTED
                        ne 'id', story.id
                        if (story.dependences){
                        not {
                                'in' 'id', story.dependences.collect{ it.id }
                            }
                        }
                    }
                }

                if (story.state in [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]){
                    and {
                        'in' 'state', [Story.STATE_ACCEPTED, Story.STATE_ESTIMATED]
                        lt 'rank', story.rank
                    }
                    and {
                        gt 'state', Story.STATE_ESTIMATED
                    }
                }
                else if (story.state in [Story.STATE_PLANNED, Story.STATE_INPROGRESS]){
                    and {
                        'in' 'state', [Story.STATE_PLANNED, Story.STATE_INPROGRESS, Story.STATE_DONE]
                        lt 'rank', story.rank
                        parentSprint{
                            eq 'id', story.parentSprint.id
                        }
                    }
                    and {
                        parentSprint{
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

        getInProduct {p, id ->
            backlog {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }

        getInProductByUid {p, id ->
            backlog {
                eq 'id', p
            }
            and {
                eq 'uid', id
            }
            uniqueResult = true
        }
    }

    static Story withStory(long productId, long id){
        Story story = (Story) getInProduct(productId, id).list()
        if (!story)
            throw new ObjectNotFoundException(id,'Story')
        return story
    }

    static List<Story> withStories(def params, def id = 'id'){
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Story> stories = ids ? Story.getAll(ids) : null
        if (!stories)
            throw new ObjectNotFoundException(ids,'Story')
        return stories
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(s.uid)
                   FROM org.icescrum.core.domain.Story as s, org.icescrum.core.domain.Product as p
                   WHERE s.backlog = p
                   AND p.id = :pid """, [pid: pid])[0]?:0) + 1
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

    static search(product, options){
        List<Story> stories = []
        def criteria = {
            backlog {
                eq 'id', product
            }
            if (options.term || options.story){
                if (options.term) {
                    or {
                        if (options.term instanceof List){
                            options.term.each{
                                ilike 'name', '%'+it+'%'
                                ilike 'description', '%'+it+'%'
                                ilike 'notes', '%'+it+'%'
                            }
                        } else if (options.term?.isInteger()){
                            eq 'uid', options.term.toInteger()
                        } else {
                            ilike 'name', '%'+options.term+'%'
                            ilike 'description', '%'+options.term+'%'
                            ilike 'notes', '%'+options.term+'%'
                        }
                    }
                }
                if (options.story?.feature?.isLong()){
                    feature {
                        eq 'id', options.story.feature.toLong()
                    }
                }
                if (options.story?.actor?.isLong()){
                    actor {
                        eq 'id', options.story.actor.toLong()
                    }
                }
                if (options.story?.state?.isInteger()){
                    eq 'state', options.story.state.toInteger()
                }
                if (options.story?.parentRelease?.isLong()){
                    parentSprint {
                        parentRelease{
                            eq 'id', options.story.parentRelease.toLong()
                        }
                    }
                }
                if (options.story?.parentSprint?.isLong()){
                    parentSprint {
                        eq 'id', options.story.parentSprint.toLong()
                    }
                }
                if (options.story?.creator?.isLong()){
                    creator {
                        eq 'id', options.story.creator.toLong()
                    }
                }
                if (options.story?.type?.isInteger()){
                    eq 'type', options.story.type.toInteger()
                }
                if (options.story?.dependsOn?.isLong()){
                    dependsOn {
                        eq 'id', options.story.dependsOn.toLong()
                    }
                }
                if (options.story?.effort?.isBigDecimal()){
                    eq 'effort', options.story.effort.toBigDecimal()
                }
                if (options.story?.affectedVersion){
                    eq 'affectVersion', options.story.affectedVersion
                }
                if (options.story?.deliveredVersion){
                    parentSprint {
                        eq 'deliveredVersion', options.story.deliveredVersion
                    }
                }
            }
        }
        if (options.tag){
            stories = Story.findAllByTagWithCriteria(options.tag) {
                criteria.delegate = delegate
                criteria.call()
            }
        } else if(options.term || options.story != null) {
            stories = Story.createCriteria().list(options.list?:[:]) {
                criteria.delegate = delegate
                criteria.call()
            }
        }
        if (stories){
            Map storiesGrouped = stories?.groupBy{ it.feature }
            stories = []
            storiesGrouped?.each{
                it.value?.sort{ st -> st.state }
                stories.addAll(it.value)
            }
        }
        return stories ?: Collections.EMPTY_LIST
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
            ORDER BY test.state ASC """, [id: id, cache:true]
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

    def xml(builder){
        builder.story(uid:this.uid){
            type(this.type)
            rank(this.rank)
            state(this.state)
            value(this.value)
            effort(this.effort)
            doneDate(this.doneDate)
            plannedDate(this.plannedDate)
            acceptedDate(this.acceptedDate)
            creationDate(this.creationDate)
            suggestedDate(this.suggestedDate)
            estimatedDate(this.estimatedDate)
            inProgressDate(this.inProgressDate)
            executionFrequency(this.executionFrequency)

            tags { builder.mkp.yieldUnescaped("<![CDATA[${this.tags}]]>") }
            name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            notes { builder.mkp.yieldUnescaped("<![CDATA[${this.notes?:''}]]>") }
            description { builder.mkp.yieldUnescaped("<![CDATA[${this.description?:''}]]>") }

            creator(uid:this.creator.uid)

            if (this.feature){
                feature(uid:this.feature.uid)
            }
            if (this.actor){
                actor(uid:this.actor.uid)
            }
            if (dependsOn){
                dependsOn(uid:this.dependsOn.uid)
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