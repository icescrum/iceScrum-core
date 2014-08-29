<%@ page import="grails.converters.JSON" %>
%{--
  - Copyright (c) 2014 Kagilum SAS.
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

<div id="widget-id-${id}"
     data-widget-name="${id}" class="widget ${sortable ? 'widget-sortable' : ''}"
     data-is-windowable="${windowActions?.windowable?:false}"
     data-is-closeable="${windowActions?.closeable?:false}"
     data-is-resizable-options='${resizable ? resizable as JSON :false}'>
    <g:if test="${windowActions?.windowable}">
        <a href="#${id}" class="hidden sidebar-button text-right" tooltip="${title}" tooltip-placement="right" tooltip-append-to-body="true">
            <span class="text-warning ${icon}"></span>
            <g:if test="${tplBadge}">
                <br/>
                <span class="badge">42</span>
            </g:if>
        </a>
    </g:if>
    <div class="panel panel-default">
        <div class="panel-heading clearfix">
            <h3 class="panel-title">
                <span class="drag text-muted">
                    <span class="glyphicon glyphicon-th"></span>
                    <span class="glyphicon glyphicon-th"></span>
                </span> ${title}
                <g:if test="${windowActions?.windowable || windowActions?.closeable}">
                    <small class='pull-right'>
                        <div class="btn-group btn-group-xs">
                            ${toolbar instanceof Boolean ? '' : toolbar ?: ''}
                        </div>
                        <div class="btn-group btn-group-xs">
                            <g:if test="${windowActions?.windowable}">
                                <a class="btn btn-default widget-window" data-toggle="tooltip" data-container="body" title="${message(code:'is.ui.window.windowable')}"><i class="glyphicon glyphicon-resize-full"></i></a>
                            </g:if>
                            <g:if test="${windowActions?.closeable}">
                                <a class="btn btn-default widget-close" data-toggle="tooltip" data-container="body" title="${message(code:'is.ui.window.closeable')}"><i class="glyphicon glyphicon-remove"></i></a>
                            </g:if>
                        </div>
                    </small>
                </g:if>
            </h3>
        </div>
        <div class="panel-body ${resizable ? 'scrollable-shadow' : ''}" style="${resizable ? 'overflow-x:hidden; overflow-y:auto; -webkit-overflow-scrolling: touch;' : ''}">
            ${windowContent}
        </div>
    </div>
</div>