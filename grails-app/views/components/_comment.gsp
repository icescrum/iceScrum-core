<%@ page import="org.icescrum.core.domain.BacklogElement; org.icescrum.core.domain.User" %>
%{--
  - Copyright (c) 2010 iceScrum Technologies.
  -
  - This file is part of iceScrum.
  -
  - iceScrum is free software: you can redistribute it and/or modify
  - it under the terms of the GNU Lesser General Public License as published by
  - the Free Software Foundation, either version 3 of the License.
  -
  - iceScrum is distributed in the hope that it will be useful,
  - but WITHOUT ANY WARRANTY; without even the implied warranty of
  - MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  - GNU General Public License for more details.
  -
  - You should have received a copy of the GNU Lesser General Public License
  - along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
  --}%

<li id="comment${comment.id}${commentId ? '-'+commentId : ''}" class="comment" elemid="${comment.id}">

    <div class=commentContent>

      <div class="comment-avatar">
          <g:if test="${!template}">
                <is:avatar user="${comment.poster}" class="ico"/>
          </g:if>
      </div>

      <div class="comment-details">
        <is:scrumLink controller="user" action='profile' id="${comment.poster?.username}"><strong>${comment.poster?.firstName?.encodeAsHTML()} ${comment.poster?.lastName?.encodeAsHTML()}</strong></is:scrumLink>,
        <g:if test="${!template}">
            <g:formatDate date="${comment.dateCreated}" formatName="is.date.format.short.time" class="comment-dateCreated" timeZone="${backlogelement.backlog.preferences.timezone}"/>
        </g:if>
        <g:else>
            <span class="comment-dateCreated">${comment.dateCreated}</span>
        </g:else>
        <g:if test="${moderation && (access || user?.id == comment.poster?.id)}">
          <span class="menu-comment">
              (
              <is:link history="false"
                      class="edit-comment"
                      remote="true"
                      controller="story"
                      action="editCommentEditor"
                      id="${comment.id}"
                      update="commentEditorWrapper${comment.id}"
                      params="[commentable:backlogelement.id]"
                      onSuccess="jQuery('#commentEditorContainer').hide();jQuery('#comment${comment.id} .commentContent').hide();jQuery('#commentEditorWrapper${comment?.id ?: ''}').show();"
                      rendered="${(access || user?.id == comment.poster?.id) ? 'true' : 'false'}">
                ${message(code:'is.ui.backlogelement.comment.edit')}
              </is:link>
              <g:if test="${access}">
                <is:link history="false"
                        class="delete-comment"
                        remote="true"
                        controller="story"
                        action="deleteComment"
                        id="${comment.id}"
                        onSuccess="jQuery.event.trigger('remove_comment',data)"
                        params="[backlogelement:backlogelement.id]">
                - ${message(code:'is.ui.backlogelement.comment.delete')}
                </is:link>
              </g:if>
              )
          </span>
        </g:if>
        <g:if test="${!template && comment.lastUpdated && comment.lastUpdated.time >= (comment.dateCreated.time + 5000)}">
          <em>${message(code:'is.ui.backlogelement.comment.last.update')}
                <g:formatDate date="${comment.lastUpdated}" formatName="is.date.format.short.time" class="comment-lastUpdated" timeZone="${backlogelement.backlog.preferences.timezone}"/>
          </em>
        </g:if>
        <g:if test="${template}">
            <span class="comment-lastUpdated"><em>${message(code:'is.ui.backlogelement.comment.last.update')} ${comment.lastUpdated}</em></span>
        </g:if>
      </div>

      <div class='comment-body'>
        <g:if test="${template}">
            ${comment.body}
        </g:if>
          <g:else>
            <wikitext:renderHtml markup="Textile">${comment.body}</wikitext:renderHtml>
          </g:else>
      </div>

    </div>

    <div id="commentEditorWrapper${comment.id}${commentId ? '-'+commentId : ''}"></div>

</li>
