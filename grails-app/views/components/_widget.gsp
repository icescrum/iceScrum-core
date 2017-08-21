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
<div class="panel panel-light" ${widgetDefinition.ngController ? 'ng-controller="' + widgetDefinition.ngController + '"' : ''}>
    <div class="panel-heading clearfix" as-sortable-item-handle>
        <h3 class="panel-title">
            <i class="fa fa-${widgetDefinition.icon} pull-left"></i>&nbsp;<g:message code="${widgetDefinition.title}"/>
            <div class="btn-settings btn-group visible-on-hover">
                <g:if test="${widget && widgetDefinition.settings}">
                    <button class="btn btn-default btn-sm"
                            ng-click="toggleSettings(widget)"
                            uib-tooltip="${message(code: 'todo.is.ui.setting')}">
                        <i class="fa" ng-class="{ 'fa-cog':!showSettings, 'fa-save':showSettings }"></i>
                    </button>
                </g:if>
                <g:if test="${widget && widgetDefinition.allowRemove}">
                    <button class="btn btn-default btn-sm"
                            ng-click="delete(widget)"
                            uib-tooltip="${message(code: 'is.ui.widget.remove')}">
                        <i class="fa fa-times"></i>
                    </button>
                </g:if>
            </div>
        </h3>
    </div>
    <div class="panel-body"
        ${widgetDefinition.settings ? 'ng-switch="showSettings"' : ''}
         ng-class="showSettings ? 'widget-settings' : 'widget-content'">
        ${widgetDefinition.settings ? '<div ng-switch-default>' : ''}
        ${content}
        ${widgetDefinition.settings ? '</div>' : ''}
        <g:if test="${widgetDefinition.settings}">
            <form ng-switch-when="true"
                  ng-submit="update(widget)"
                  class="form-horizontal">
                <g:render template="/widgets/${widgetDefinition.id}/settings" plugin="${widgetDefinition.pluginName}"/>
            </form>
        </g:if>
    </div>
    <g:if test="${widgetDefinition.footer}">
        <div class="panel-footer">
            <g:render template="/${widgetDefinition.id}/widget/footer" plugin="${widgetDefinition.pluginName}"/>
        </div>
    </g:if>
</div>
