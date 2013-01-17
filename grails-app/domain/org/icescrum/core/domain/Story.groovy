/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */



package org.icescrum.core.domain

import org.grails.comments.Comment
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumStoryEvent
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.springframework.security.core.context.SecurityContextHolder as SCH
import grails.util.GrailsNameUtils


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
    Integer effort = null
    int rank = 0
    int state = Story.STATE_SUGGESTED
    int value = 0
    String textAs
    String textICan
    String textTo
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
            acceptanceTests: AcceptanceTest
    ]

    static mappedBy = [
            tasks: 'parentStory'
    ]

    static transients = [
            'todo', 'dependences', 'deliveredVersion'
    ]

    static mapping = {
        cache true
        table 'icescrum2_story'
        tasks cascade: 'all'
        acceptanceTests sort: 'uid'
    }

    static constraints = {
        textAs(maxSize: 500, nullable: true)
        textICan(maxSize: 1000, nullable: true)
        textTo(maxSize: 1000, nullable: true)
        suggestedDate(nullable: true)
        acceptedDate(nullable: true)
        estimatedDate(nullable: true)
        plannedDate(nullable: true)
        inProgressDate(nullable: true)
        doneDate(nullable: true)
        parentSprint(nullable: true)
        feature(nullable: true)
        actor(nullable: true)
        affectVersion(nullable: true)
        effort(nullable: true)
        creator(nullable: true) // in case of a user deletion, the story can remain without owner
        dependsOn(nullable: true)
        origin(nullable: true)
    }

    static namedQueries = {

        findInStoriesSuggested {p, term ->
            backlog {
                eq 'id', p
            }
            or {
                def termInteger = term?.replaceAll('%','')
                if (termInteger?.isInteger()){
                    eq 'uid', termInteger.toInteger()
                } else{
                    ilike 'name', term
                    ilike 'textAs', term
                    ilike 'textICan', term
                    ilike 'textTo', term
                    ilike 'description', term
                    ilike 'notes', term
                    ilike 'affectVersion', term
                    feature {
                        ilike 'name', term
                    }
                }
            }
            and {
                eq 'state', Story.STATE_SUGGESTED
            }
        }

        findAllByProductAndTerm{p, term ->
            backlog {
                eq 'id', p
            }
            or {
                def termInteger = term?.replaceAll('%','')
                if (termInteger?.isInteger()){
                    eq 'uid', termInteger.toInteger()
                } else{
                    ilike 'name', term
                    ilike 'textAs', term
                    ilike 'textICan', term
                    ilike 'textTo', term
                    ilike 'description', term
                    ilike 'notes', term
                    ilike 'affectVersion', term
                    feature {
                        ilike 'name', term
                    }
                }
            }
        }

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
                    ilike 'textAs', term
                    ilike 'textICan', term
                    ilike 'textTo', term
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

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(s.uid)
                   FROM org.icescrum.core.domain.Story as s, org.icescrum.core.domain.Product as p
                   WHERE s.backlog = p
                   AND p.id = :pid """, [pid: pid])[0]?:0) + 1
    }

    static recentActivity(Product currentProductInstance) {
        executeQuery("SELECT act FROM grails.plugin.fluxiable.Activity as act WHERE act.id IN (SELECT DISTINCT a.activity.id " +
                "FROM grails.plugin.fluxiable.ActivityLink as a, org.icescrum.core.domain.Story as s " +
                "WHERE a.type='story' " +
                "and s.backlog=:p " +
                "and s.id=a.activityRef " +
                "and not (a.activity.code like 'task') )" +
                "ORDER BY act.dateCreated DESC", [p: currentProductInstance], [max: 15])
    }

    //Not working on ORACLE
    static recentActivity(User user) {
        executeQuery("SELECT DISTINCT a.activity, s.backlog " +
                "FROM grails.plugin.fluxiable.ActivityLink as a, org.icescrum.core.domain.Story as s " +
                "WHERE a.type='story' " +
                "and s.backlog.id in (SELECT DISTINCT p.id " +
                                        "FROM org.icescrum.core.domain.Product as p INNER JOIN p.teams as t " +
                                        "WHERE t.id in" +
                                                "(SELECT DISTINCT t2.id FROM org.icescrum.core.domain.Team as t2 " +
                                                "INNER JOIN t2.members as m " +
                                                "WHERE m.id = :uid)) " +
                "and s.id=a.activityRef " +
                "and not (a.activity.code like 'task') " +
                "ORDER BY a.activity.dateCreated DESC", [uid: user.id], [cache:true,max: 15])
    }

    static findLastUpdatedComment(long storyId) {
        executeQuery("SELECT c.lastUpdated " +
                "FROM org.grails.comments.Comment as c, org.grails.comments.CommentLink as cl, org.icescrum.core.domain.Story as s " +
                "WHERE c = cl.comment " +
                "AND cl.commentRef = s " +
                "AND cl.type = :storyType " +
                "AND s.id = :storyId " +
                "ORDER BY c.lastUpdated DESC",
                [storyId: storyId, storyType: GrailsNameUtils.getPropertyName(Story)],
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

    def getDependences(){
        return Story.findAllByDependsOn(this,[cache:true,sort:"state",order:"asc"])
    }

    def getDeliveredVersion(){
        return this.state == STATE_DONE ? this.parentSprint.deliveredVersion ?: null : null
    }

    def onAddComment = { Comment c ->
        publishEvent new IceScrumStoryEvent(this, c, this.class, (User)c.poster, IceScrumStoryEvent.EVENT_COMMENT_ADDED)
    }

    def onAddAttachment = { Attachment a ->
        publishEvent new IceScrumStoryEvent(this, a, this.class, (User)a.poster, IceScrumStoryEvent.EVENT_FILE_ATTACHED_ADDED)
    }

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumStoryEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE, true))
        }
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumStoryEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE, true))
        }
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
                        ilike 'name', options.term
                        ilike 'textAs', options.term
                        ilike 'textICan', options.term
                        ilike 'textTo', options.term
                        ilike 'description', options.term
                        ilike 'notes', options.term
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
                if (options.story?.effort?.isInteger()){
                    eq 'effort', options.story.effort.toInteger()
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
        } else if(options.term || options.story) {
            stories = Story.createCriteria().list {
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
}