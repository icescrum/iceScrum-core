/*
 * Copyright (c) 2015 Kagilum SAS
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

import org.hibernate.ObjectNotFoundException


class Actor implements Serializable, Comparable<Actor> {

    static final long serialVersionUID = 2762136778121132424L

    String name

    static hasMany = [stories: Story]

    static mappedBy = [stories: "actor"]

    static belongsTo = [parentProduct: Product]

    static constraints = {
        name(blank: false, unique: 'parentProduct', maxSize: 100)
    }

    static mapping = {
        cache true
        table 'is_actor'
        name index: 'act_name_index'
        parentProduct index: 'act_name_index'
        stories cascade: "refresh, evict", cache: true
    }

    static namedQueries = {

        getInProduct {p, id ->
            parentProduct {
                eq 'id', p
            }
            and {
                eq 'id', id
            }
            uniqueResult = true
        }
    }

    static Actor withActor(long product, long id){
        Actor actor = (Actor) getInProduct(product, id).list()
        if (!actor) {
            throw new ObjectNotFoundException(id, 'Actor')
        }
        return actor
    }

    static List<Actor> withActors(def params, def id = 'id'){
        def ids = params[id]?.contains(',') ? params[id].split(',')*.toLong() : params.list(id)
        List<Actor> actors = ids ? Actor.getAll(ids).findAll { it } : null
        if (!actors) {
            throw new ObjectNotFoundException(ids, 'Actor')
        }
        return actors
    }

    @Override
    boolean equals(Object obj) {
        if (this.is(obj))
            return true
        if (obj == null)
            return false
        if (getClass() != obj.getClass())
            return false
        final Actor other = (Actor) obj
        if (parentProduct == null) {
            if (other.parentProduct != null)
                return false
        } else if (!parentProduct.equals(other.parentProduct))
            return false
        if (name != other.name)
            return false
        return true
    }

    @Override
    int hashCode() {
        final int prime = 31
        int result = 1
        result = prime * result + ((parentProduct == null) ? 0 : parentProduct.hashCode())
        result = prime * result + (name ? name.hashCode() : 0)
        return result
    }

    int compareTo(Actor cr) {
        return name.compareTo(cr.name)
    }

    static search(product, term){
        return Actor.createCriteria().list {
            parentProduct {
                eq 'id', product
            }
            if (term){
                ilike 'name', '%'+term+'%'
            }
        }
    }

    static searchAllByTermOrTag(productId, term) {
        search(productId, term)
    }

    def xml(def builder){
        builder.actor(){
            builder.name { builder.mkp.yieldUnescaped("<![CDATA[${this.name}]]>") }
            builder.stories(){
                this.stories.each{ _story ->
                    story(uid: _story.uid)
                }
            }
            exportDomainsPlugins(builder)
        }
    }
}
