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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */




package org.icescrum.core.domain

import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.event.IceScrumFeatureEvent
import org.springframework.security.core.context.SecurityContextHolder as SCH

class Feature extends BacklogElement implements Serializable {
    static final long serialVersionUID = 7072515028109185168L

    static final int TYPE_FUNCTIONAL = 0
    static final int TYPE_ARCHITECTURAL = 1
    static final int STATE_WAIT = 0
    static final int STATE_BUSY = 1
    static final int STATE_DONE = 2

    String color = "blue"

    Integer value = null
    int type = Feature.TYPE_FUNCTIONAL
    int rank

    static transients = ['countDoneStories', 'state', 'effort']

    static belongsTo = [
            parentDomain: Domain,
            parentRelease: Release
    ]

    static hasMany = [stories: Story]

    static mappedBy = [stories: "feature"]

    static mapping = {
        cache true
        table 'icescrum2_feature'
        stories cascade: "refresh", sort: 'rank', 'name': 'asc', cache: true
        sort "id"
    }

    static constraints = {
        parentDomain(nullable: true)
        parentRelease(nullable: true)
        value(nullable: true)
    }

    static namedQueries = {

        // TODO delete unused method
        findAllByProductAndTerm {p, term ->
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
                }
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
    }

    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((!name) ? 0 : name.hashCode())
        result = prime * result + ((!backlog) ? 0 : backlog.hashCode())
        return result
    }

    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        final Feature other = (Feature) obj
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
        return true
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT MAX(f.uid)
                   FROM org.icescrum.core.domain.Feature as f, org.icescrum.core.domain.Product as p
                   WHERE f.backlog = p
                   AND p.id = :pid """, [pid: pid])[0]?:0) + 1
    }

    def getCountDoneStories(){
        return stories?.sum {(it.state == Story.STATE_DONE) ? 1 : 0}?:0
    }

    def getState(){
        if (!stories || stories.find{ it.state > Story.STATE_INPROGRESS} == null ) {
            return STATE_WAIT
        }
        if (stories.collect{it.state}.count(Story.STATE_DONE) == stories.size()){
            return STATE_DONE
        }else{
            return STATE_BUSY
        }
    }

    def getEffort(){
        return stories?.sum {it.effort ?: 0}?:0
    }

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumFeatureEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_BEFORE_DELETE, true))
        }
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumFeatureEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumEvent.EVENT_AFTER_DELETE, true))
        }
    }

    static search(product, options){
        def criteria = {
            backlog {
                eq 'id', product
            }
            if (options.term || options.feature){
                if (options.term){
                    or {
                        if (options.term?.isInteger()){
                            eq 'uid', options.term.toInteger()
                        }else{
                            ilike 'name', '%'+options.term+'%'
                            ilike 'description', '%'+options.term+'%'
                            ilike 'notes', '%'+options.term+'%'
                        }
                    }
                }
                if (options.feature?.type?.isInteger()){
                    eq 'type', options.feature.type.toInteger()
                }
            }
        }
        if (options.tag){
            return Feature.findAllByTagWithCriteria(options.tag) {
                criteria.delegate = delegate
                criteria.call()
            }
        } else if(options.term || options.feature)  {
            return Feature.createCriteria().list {
                criteria.delegate = delegate
                criteria.call()
            }
        } else {
            return Collections.EMPTY_LIST
        }
    }

    static searchByTermOrTag(productId, searchOptions, term) {
        search(productId, addTermOrTagToSearch(searchOptions, term))
    }

    static searchAllByTermOrTag(productId, term) {
        def searchOptions = [feature: [empty:'']] // TODO FIX
        searchByTermOrTag(productId, searchOptions, term)
    }

}
