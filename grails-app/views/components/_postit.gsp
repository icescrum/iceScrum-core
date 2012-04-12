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

<div class="${className} ${styleClass} postit-${type}" id="postit-${type}-${id}" elemId="${id}">

    <div class="postit-layout postit-${color}">

        <g:if test="${miniId}">
            <g:if test="${type == 'story'}">
                <p class="postit-id"><is:scrumLink controller="story" id="${id}">${miniId}</is:scrumLink></p>
            </g:if>
            <g:else>
                <p class="postit-id">${miniId}</p>
            </g:else>
        </g:if>

        <div class="icon-container">

            <g:if test="${comment}">
                <span class="postit-comment icon"
                      title="${message(code: 'is.postit.comment.count', args: [comment, (comment instanceof Integer && comment > 1) ? 's' : ''])}"></span>
            </g:if>

            <g:if test="${attachment}">
                <span class="postit-attachment icon"
                      title="${message(code: 'is.postit.attachment', args: [attachment, (attachment instanceof Integer && attachment > 1) ? 's' : ''])}"></span>
            </g:if>

            <g:if test="${acceptanceTestCount}">
                <span class="postit-acceptance-test icon"
                      title="${message(code: 'is.postit.acceptanceTest.count', args: [acceptanceTestCount, (acceptanceTestCount instanceof Integer && acceptanceTestCount > 1) ? 's' : ''])}"></span>
            </g:if>
        </div>

    %{-- Title --}%
        <g:if test="${sortable}">
            <p class="postit-label postit-sortable break-word">${title.encodeAsHTML()}</p>
        </g:if>
        <g:else>
            <p class="postit-label break-word">${title.encodeAsHTML()}</p>
        </g:else>

        <g:if test="${className != 'postit-rect'}">
            <p class="postit-excerpt">${content.replace('<br>', '')}</p>
        </g:if>

        <g:if test="${typeNumber}">
            <span class="postit-ico ico-${type}-${typeNumber}" title="${typeTitle ?: ''}"></span>
        </g:if>
        <g:else>
            <span class="postit-ico"></span>
        </g:else>

    %{--Status bar of the post-it note--}%
        <div class="state task-state">

        %{--Estimation--}%
            <g:if test="${miniValue != null}">
                <span class=" opacity-70 mini-value ${editableEstimation ? 'editable' : ''}">${miniValue}</span>
            </g:if>
        %{--State label--}%
            <span class="text-state"><is:truncated encodedHTML="true" size="16">${stateText}</is:truncated></span>

        %{--Embedded menu--}%
            <g:if test="${embeddedMenu}">
                <div class="dropmenu-action">${embeddedMenu}</div>
            </g:if>

            <g:if test="${tooltip}">
                ${tooltip}
            </g:if>

        </div>

    </div>
</div>