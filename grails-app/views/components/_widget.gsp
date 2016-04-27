<%@ page import="grails.converters.JSON" %>
%{--
  - Copyright (c) 2016 Kagilum SAS.
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
%{-- widget --}%
<div ng-controller="widgetCtrl" class="panel panel-light">
    <div id="widget-${widgetDefinition.id}" class="widget widget-${widgetDefinition.id}">
        <div class="panel-heading clearfix" as-sortable-item-handle>
            <h3 class="panel-title">
                <i class="fa fa-${widgetDefinition.icon}"></i> <g:message code="${widgetDefinition.title}"/>
                <div class="pull-right btn-group visible-on-hover">
                    <g:if test="${widgetDefinition.settings}">
                        <button class="btn btn-default btn-sm"
                                ng-click="toggleSettings()"
                                uib-tooltip="${message(code: 'todo.is.ui.setting')}">
                            <i class="fa fa-cog"></i>
                        </button>
                    </g:if>
                    <g:if test="${widgetDefinition.allowRemove}">
                        <button class="btn btn-default btn-sm"
                                ng-click="remove()"
                                uib-tooltip="${message(code: 'todo.is.ui.setting')}">
                            <i class="fa fa-times"></i>
                        </button>
                    </g:if>
                </div>
            </h3>
        </div>
        <div class="panel-body" ${widgetDefinition.settings ? 'ng-switch="showSettings"' : ''}>
            ${widgetDefinition.settings ? '<div ng-switch-default>' : ''}
                ${content}
            ${widgetDefinition.settings ? '</div>' : ''}
        <g:if test="${widgetDefinition.settings}">
                <g:render template="/${widgetDefinition.id}/widget/settings" plugin="${widgetDefinition.pluginName}"/>
            </g:if>
        </div>
        <g:if test="${widgetDefinition.footer}">
            <div class="panel-footer">
                <g:render template="/${widgetDefinition.id}/widget/footer" plugin="${widgetDefinition.pluginName}"/>
            </div>
        </g:if>
    </div>
</div>