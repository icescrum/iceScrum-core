/*
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
 */
package org.icescrum.core.event

import org.grails.comments.Comment
import org.icescrum.core.domain.BacklogElement
import org.icescrum.core.domain.User

class IceScrumBacklogElementEvent extends IceScrumEvent {

    static final String EVENT_COMMENT_ADDED = 'CommentAdded'
    static final String EVENT_COMMENT_UPDATED = 'CommentUpdated'
    static final String EVENT_COMMENT_DELETED = 'CommentDeleted'
    static final EVENT_COMMENT_LIST = [EVENT_COMMENT_ADDED, EVENT_COMMENT_UPDATED]
    def comment = null

    IceScrumBacklogElementEvent(BacklogElement element, Class generatedBy, User doneBy, def type, boolean synchronous = false){
        super(element, generatedBy, doneBy, type, synchronous)
    }

    IceScrumBacklogElementEvent(BacklogElement element, Comment comment, Class generatedBy, User doneBy, def type, boolean synchronous = false) {
        super(element, generatedBy, doneBy, type, synchronous)
        this.comment = comment
    }
}
