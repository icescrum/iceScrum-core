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
<div id="commentEditorContainer${comment?.id ?: ''}" class="commentEditorContainer" ${hidden ? 'style="display:none;"' : ''}>
  <g:message code="is.ui.backlogelement.comment.wikisyntax.info"/>
  <g:formRemote name="commentForm${comment?.id ?: ''}" url="[controller:'story',action:'addComment']" update="comments-list">
    <markitup:editor id="commentBody${comment?.id ?: ''}" name="comment.body">
      ${mode && mode == 'edit' && comment?.body ? comment.body : ''}
    </markitup:editor>
    <g:hiddenField id="update${comment?.id ?: ''}" name="update" value="comments"/>
    <g:hiddenField id="commentPageURI${comment?.id ?: ''}" name="commentPageURI" value="${request.forwardURI}"/>
    <g:if test="${comment}">
      <g:hiddenField id="commentId${comment?.id ?: ''}" name="comment.id" value="${comment?.id}"/>
    </g:if>
    <g:if test="${commentable}">
      <g:hiddenField id="commentRef${comment?.id ?: ''}" name="comment.ref" value="${commentable?.id}"/>
    </g:if>
    <p class="comment-button-wrapper">
      <g:if test="${!mode || mode=='add'}">
        <is:button
                id="submitForm" type="submitToRemote"
                url="[controller:'story', action:'saveComment', params:[product:params.product]]"
                onSuccess="jQuery('#commentEditorContainer').hide();jQuery('#commentBody').val('');jQuery('#start-follow').hide();jQuery('#stop-follow').show();jQuery.event.trigger('add_comment',data);"
                value="${message(code:'is.ui.backlogelement.comment.post.button')}"
                history="false"/>
      </g:if>
      <g:elseif test="${mode && mode=='edit'}">
        <is:button
                id="submitCommentForm${comment?.id ?: ''}" type="submitToRemote"
                url="[controller:'story', action:'updateComment', params:[product:params.product]]"
                onSuccess="jQuery('#commentEditorWrapper${comment?.id ?: ''}').hide();jQuery('#comment${comment?.id ?: ''} .commentContent').show();jQuery.event.trigger('update_comment',data);"
                value="${message(code:'is.ui.backlogelement.comment.edit.button')}"
                history="false"/>
      </g:elseif>
    </p>
  </g:formRemote>
</div>