<%@ page import="grails.converters.JSON" %>
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

%{-- tabindex to active shortcuts on div --}%
<div id="${type}-id-${id}"
     class="is-${type} box-${type}${sortable ? '-sortable' : ''} box"
     tabindex="0"
    <g:if test="${type == 'window'}">
        data-fullScreen="${windowActions?.fullScreen?:false}" data-widgetable="${windowActions?.widgetable?:false}" data-title="${projectName ?: 'iceScrum'} - ${title.encodeAsJavaScript()}"
    </g:if>
    <g:else>
        data-windowable="${windowActions?.windowable?:false}" data-closeable="${windowActions?.closeable?:false}" data-resizable-options='${resizable ? resizable as JSON :false}'
    </g:else>
>

%{-- Toolbar --}%
<g:if test="${toolbar}">
    <div class="box-navigation">
        <ul class='${type}-toolbar'>
            <g:if test="${type == 'widget'}">
                <li class="title">${title}</li>
            </g:if>
            ${toolbar instanceof Boolean ? '' : toolbar ?: ''}
        </ul>
        <ul class='${type}-actions'>
            <g:if test="${windowActions?.help}">
                <li>
                    <a class="ui-icon-help ui-icon" title="${message(code:'is.ui.window.help')}" href="${createLink(controller:'scrumOS',action:'help',params:[window:id])}" data-ajax="true"></a>
                </li>
            </g:if>
            <g:if test="${windowActions?.printable}">
                <li>
                    <a class="ui-icon-print ui-icon"
                       title="${message(code:'is.ui.window.print')}"
                       data-ajax="true"
                       data-shortcut="ctrl+p"
                       href="${createLink(controller:id,action:'print', params:[product:params.product?:null, format:'PDF'])}"></a>
                </li>
            </g:if>
            <g:if test="${windowActions?.widgetable}">
                <li>
                    <a class="${type}-minimize ui-icon-transferthick-e-w ui-icon" title="${message(code:'is.ui.window.widgetable')}"></a>
                </li>
            </g:if>
            <g:if test="${windowActions?.windowable}">
                <li>
                    <a class="${type}-window ui-icon-transferthick-e-w ui-icon" title="${message(code:'is.ui.window.windowable')}"></a>
                </li>
            </g:if>
            <g:if test="${windowActions?.maximizeable}">
                <li>
                    <a class="${type}-maximize ui-icon-arrowthick-2-ne-sw ui-icon" title="${message(code:'is.ui.window.fullscreen')}"></a>
                </li>
            </g:if>
            <g:if test="${windowActions?.closeable}">
                <li>
                    <a class="${type}-close ui-icon-close ui-icon" title="${message(code:'is.ui.window.closeable')}"></a>
                </li>
            </g:if>
        </ul>
    </div>
</g:if>

%{-- Content --}%
<div id="${type}-content-${id}"
     class="box-content ${type}-content ${!toolbar ? type + '-content-without-toolbar' : ''}" style="${resizable ? 'overflow-x:hidden; overflow-y:auto;' : ''}">
    ${windowContent}
</div>

<g:if test="${type == 'window'}">
    <div id="right" class="right-resizable ${!toolbar ? type + '-right-without-toolbar' : ''}" data-resizable="true" data-containment="parent" data-event-on-width="600" data-min-width="400" data-empty-hide="${right ? "false" : "true"}">
        ${right}
    </div>
</g:if>
</div>