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

<div class="${className} ${styleClass} postit-${type}" id="postit-${type}-${id}" data-elemid="${id}" ${dependsOn ? 'data-dependsOn="'+dependsOn.id+'"' : ''}>

    <div class="postit-layout postit-${color}">

        <g:if test="${miniId}">
            <g:if test="${type == 'story'}">
                <p class="postit-id">
                    <is:scrumLink controller="story" id="${id}">${miniId}</is:scrumLink>
                    <g:if test="${dependsOn}">
                        <span class="dependsOn" data-elemid="${dependsOn.id}">(<is:scrumLink controller="story" id="${dependsOn.id}">${dependsOn.uid}</is:scrumLink>)</span>
                    </g:if>
                </p>
            </g:if>
            <g:elseif test="${type == 'task'}">
                <p class="postit-id"><is:scrumLink controller="task" id="${id}">${miniId}</is:scrumLink></p>
            </g:elseif>
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

        <g:if test="${content}">
            <div class="postit-excerpt">${content.replace('<br>', '')}</div>
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

            <g:if test="${menu?.rendered != null ? menu.rendered : menu ? true : false}">
                <div class="dropmenu-action">
                    <div data-dropmenu="true" class="dropmenu" data-top="13" data-offset="0" data-noWindows="false" id="menu-postit-${menu.id}">
                        <span class="dropmenu-arrow">!</span>
                        <div class="dropmenu-content ui-corner-all">
                            <ul class="small">
                                <g:render template="${menu.template}" model="${menu.params}"/>
                            </ul>
                        </div>
                    </div>
                </div>
            </g:if>
        </div>

    </div>
</div>