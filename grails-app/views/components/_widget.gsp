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
<div class="card hover-container" ${widgetDefinition.ngController ? 'ng-controller="' + widgetDefinition.ngController + '"' : ''}>
    <h3 class="card-header d-flex justify-content-between" as-sortable-item-handle>
        <span>${message(code: widgetDefinition.title)}</span>
        <span class="hover-visible">
            <g:if test="${widget && widgetDefinition.settings}">
                <button class="btn btn-secondary btn-sm"
                        ng-if="authorizedWidget('update', widget)"
                        ng-click="toggleSettings(widget)"
                        defer-tooltip="${message(code: 'todo.is.ui.setting')}">
                    <i class="fa" ng-class="{ 'fa-cog':!showSettings, 'fa-save':showSettings }"></i>
                </button>
            </g:if>
            <g:if test="${widget && widgetDefinition.allowRemove}">
                <button class="btn btn-secondary btn-sm"
                        ng-if="authorizedWidget('delete', widget)"
                        ng-click="delete(widget)"
                        defer-tooltip="${message(code: 'is.ui.widget.remove')}">
                    <i class="fa fa-times"></i>
                </button>
            </g:if>
        </span>
    </h3>
    <div class="card-body"
        ${widgetDefinition.settings ? 'ng-switch="showSettings"' : ''}
         ng-class="showSettings ? 'widget-settings' : 'widget-content'">
        ${widgetDefinition.settings ? '<div ng-switch-default>' : ''}
        ${content}
        ${widgetDefinition.settings ? '</div>' : ''}
        <g:if test="${widgetDefinition.settings}">
            <form ng-switch-when="true"
                  ng-if="authorizedWidget('update', widget)"
                  ng-submit="update(widget)">
                <g:render template="/widgets/${widgetDefinition.id}/settings" plugin="${widgetDefinition.pluginName}"/>
            </form>
        </g:if>
    </div>
    <g:if test="${widgetDefinition.footer}">
        <div class="card-footer">
            <g:render template="/${widgetDefinition.id}/widget/footer" plugin="${widgetDefinition.pluginName}"/>
        </div>
    </g:if>
</div>
