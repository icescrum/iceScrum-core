/*
 * Copyright (c) 2019 Kagilum SAS
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
 *
 */
package org.icescrum.core.services

import grails.transaction.Transactional
import grails.util.GrailsNameUtils
import org.grails.comments.Comment
import org.grails.comments.CommentLink
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.*
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.utils.ServicesUtils

@Transactional
class CommentService extends IceScrumEventPublisher {

    def grailsApplication
    def activityService

    Comment save(Object commentable, User poster, props) {
        commentable.addComment(poster, props.body)
        activityService.addActivity(commentable, poster, 'comment', commentable.name);
        Comment comment = commentable.comments.sort { it.dateCreated }?.last() // Hack
        if (commentable instanceof Story) {
            ((Story) commentable).addToFollowers(poster)
        }
        if (commentable.hasProperty('comments_count')) {
            commentable.comments_count = commentable.getTotalComments()
            commentable.save(flush: true)
        }
        publishCommentableEvent(comment, commentable, 'addedComment')
        publishSynchronousEvent(IceScrumEventType.CREATE, comment)
        return comment
    }

    void update(Comment comment, props) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, comment)
        comment.body = props.body
        comment.save(flush: true)
        def commentable = getCommentable(comment)
        publishCommentableEvent(comment, commentable, 'updatedComment')
        publishSynchronousEvent(IceScrumEventType.UPDATE, comment, dirtyProperties)
    }

    void delete(Comment comment) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, comment)
        dirtyProperties.project = getProject(comment)
        def commentable = getCommentable(comment)
        commentable.removeComment(comment)
        if (commentable.hasProperty('comments_count')) {
            commentable.comments_count = commentable.getTotalComments()
        }
        publishCommentableEvent(comment, commentable, 'removedComment')
        publishSynchronousEvent(IceScrumEventType.DELETE, comment, dirtyProperties)
    }

    void importComment(Object commentable, User poster, String body, Date dateCreated) {
        def comment = new Comment(body: body, posterId: poster.id, posterClass: ApplicationSupport.getUnproxiedClassName(poster.class.name))
        comment.save()
        def link = new CommentLink(comment: comment, commentRef: commentable.id, type: GrailsNameUtils.getPropertyName(commentable.class))
        link.save()
        comment.dateCreated = dateCreated
    }

    Map getRenderableComment(Comment comment, Object commentable = null) {
        def commentLink = commentable ? [commentRef: commentable.id, type: getCommentableType(commentable)] : CommentLink.findByComment(comment)
        return [
                class      : 'Comment',
                id         : comment.id,
                body       : comment.body,
                body_html  : ServicesUtils.textileToHtml(comment.body),
                poster     : comment.poster,
                dateCreated: comment.dateCreated,
                lastUpdated: comment.lastUpdated,
                commentable: [
                        class: commentLink.type.capitalize(),
                        id   : commentLink.commentRef
                ]
        ]
    }

    Comment withComment(long projectId, long id) {
        Comment comment = Comment.get(id)
        if (!comment) {
            throw new ObjectNotFoundException(id, 'Comment')
        }
        CommentLink commentLink = CommentLink.findByComment(comment)
        withCommentable(projectId, commentLink.commentRef, commentLink.type) // Important security check that the commentable exists and in the right project
        return comment
    }

    Object withCommentable(long projectId, long commentableId, String type) {
        if (type == 'story') {
            return Story.withStory(projectId, commentableId)
        } else if (type == 'task') {
            return Task.withTask(projectId, commentableId)
        } else if (type == 'feature') {
            return Feature.withFeature(projectId, commentableId)
        } else {
            throw new ObjectNotFoundException(commentableId, type)
        }
    }

    Project getProject(Comment comment) {
        CommentLink commentLink = CommentLink.findByComment(comment)
        if (commentLink.type == 'story') {
            return (Project) Story.get(commentLink.commentRef).backlog
        } else if (commentLink.type == 'task') {
            return Task.get(commentLink.commentRef).parentProject
        } else if (commentLink.type == 'feature') {
            return (Project) Feature.get(commentLink.commentRef).backlog
        } else {
            return null
        }
    }

    private Object getCommentable(Comment comment) {
        CommentLink commentLink = CommentLink.findByComment(comment)
        if (commentLink.type == 'story') {
            return Story.get(commentLink.commentRef)
        } else if (commentLink.type == 'task') {
            return Task.get(commentLink.commentRef)
        } else if (commentLink.type == 'feature') {
            return Feature.get(commentLink.commentRef)
        } else {
            return null
        }
    }

    private void publishCommentableEvent(comment, commentable, event) {
        grailsApplication.mainContext[getCommentableType(commentable) + 'Service'].publishSynchronousEvent(IceScrumEventType.UPDATE, commentable, [(event): comment])
    }

    private String getCommentableType(Object commentable) {
        return ApplicationSupport.getUnproxiedClassName(GrailsNameUtils.getPropertyName(commentable.class))
    }
}