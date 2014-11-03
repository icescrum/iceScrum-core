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

<div id="window-id-${id}" tabindex="1">
    <div class="clearfix stacks" ng-class="{ 'three-stacks': $state.params.tabId, 'two-stacks': ($state.params.id && !$state.params.tabId)|| (!$state.params.id && !$state.params.tabId) }">
        %{-- Content --}%
        <div id="window-content-${id}" class="window-content">
        <g:if test="${toolbar != false && right != null}">
            <nav fixed="#window-content-${id}" class="navbar navbar-toolbar navbar-default" role="navigation">
                <div class="container-fluid">
                    <div class="btn-toolbar" id="${controllerName}-toolbar" role="toolbar">
                        ${toolbar}
                    </div>
                </div>
            </nav>
        </g:if>
            ${windowContent}
        </div>
        <g:if test="${right}">
            <div class="details" ui-view="details">
            </div>
            <div class="details-list" ui-view="details-list"></div>
        </g:if>
    </div>
</div>