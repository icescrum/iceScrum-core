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

<div id="window-id-${id}"
     tabindex="1"
     data-is-full-screen="${windowActions?.fullScreen?:false}"
     data-is-widgetable="${windowActions?.widgetable?:false}"
     data-is-title="${spaceName ?: 'iceScrum'} - ${title.encodeAsJavaScript()}">
<g:if test="${right}">
    <div class="clearfix">
</g:if>
    %{-- Content --}%
    <div id="window-content-${id}" class="window-content ${right != null ? 'col-md-7 col-lg-8' : ''} scrollable">
    <g:if test="${right != null}">
        <nav fixed="#window-content-${id}" class="navbar navbar-toolbar navbar-default" role="navigation">
            <div class="container-fluid">
                <div class="btn-toolbar" id="${controllerName}-toolbar" role="toolbar">
                    ${toolbar}
                </div>
            </div>
        </nav>
    </g:if>
    <div class="content">
            ${windowContent}
        </div>
    </div>
    <g:if test="${right}">
        <div id="right" class="col-md-5 col-lg-4 scrollable well">
            <div id="contextual-properties" ui-view>
            </div>
        </div>
    </g:if>
<g:if test="${right}">
    </div>
</g:if>
</div>