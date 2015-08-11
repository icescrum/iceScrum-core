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
    <div class="clearfix stacks" ng-class="{ 'one-stack': !$state.current.data.stack, 'two-stacks': $state.current.data.stack == 2 , 'three-stacks': $state.current.data.stack == 3 }">
        %{-- Content --}%
        <div id="window-content-${id}" class="window-content">
        <g:if test="${toolbar != false}">
            <nav fixed="#window-content-${id}" class="navbar navbar-toolbar navbar-default" role="navigation">
                <div class="container-fluid">
                    <div class="btn-toolbar" id="${controllerName}-toolbar" role="toolbar">
                        ${toolbar}
                    </div>
                </div>
            </nav>
        </g:if>
            ${windowContent}
        <g:if test="${bottombar != false}">
            <nav fixed="#window-content-${id}" fixed-bottom="true" class="navbar navbar-toolbar bottombar navbar-default hidden" fixed-offset-bottom="11" role="navigation">
                <div class="container-fluid">
                    <div class="btn-toolbar" id="${controllerName}-bottombar" role="toolbar">
                        ${bottombar}
                    </div>
                </div>
            </nav>
        </g:if>
    </div>
    <g:if test="${right}">
        <div class="details" ui-view="details"></div>
        <div class="details-list" ui-view="details-list"></div>
        <div class="details-list-form" ui-view="details-list-form"></div>
    </g:if>
    </div>
</div>