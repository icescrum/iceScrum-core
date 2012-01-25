/*
 * Copyright (c) 2011 Kagilum / 2010 iceScrum Technlogies.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.domain

import org.icescrum.core.event.IceScrumAcceptanceTestEvent
import org.springframework.security.core.context.SecurityContextHolder as SCH

class AcceptanceTest implements Serializable {

    String name
    String description
    int uid

    Date dateCreated
    Date lastUpdated

    static belongsTo = [
        creator: User,
        parentStory: Story

    ]

    static constraints = {
        description(nullable: true, maxSize: 1000)
        name(blank: false)
    }

    static namedQueries = {
        findLastUpdated {storyId ->
            parentStory {
                eq 'id', storyId
            }
            projections {
                property 'lastUpdated'
            }
            order("lastUpdated", "desc")
            maxResults(1)
            cache true
        }
    }

    static int findNextUId(Long pid) {
        (executeQuery(
                """SELECT DISTINCT MAX(t.uid)
                   FROM org.icescrum.core.domain.AcceptanceTest as t, org.icescrum.core.domain.Story as s
                   WHERE t.parentStory = s
                   AND s.backlog.id = :pid """, [pid: pid])[0]?:0) + 1
    }

    def beforeDelete() {
        withNewSession {
            publishEvent(new IceScrumAcceptanceTestEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumAcceptanceTestEvent.EVENT_BEFORE_DELETE))
        }
    }

    def afterDelete() {
        withNewSession {
            publishEvent(new IceScrumAcceptanceTestEvent(this, this.class, User.get(SCH.context?.authentication?.principal?.id), IceScrumAcceptanceTestEvent.EVENT_AFTER_DELETE))
        }
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
}
