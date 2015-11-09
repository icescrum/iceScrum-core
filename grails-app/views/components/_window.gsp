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
 %{-- view --}%
<div id="view-${id}" class="view ${flex?'':'no-flex'}">
    <div class="${right ? 'col-md-8' : 'col-md-12' }">
        ${content}
    </div>
    <g:if test="${right}">
        <div class="details col-md-4"  ui-view="details"></div>
        <div class="details-list"      ui-view="details-list"></div>
        <div class="details-list-form" ui-view="details-list-form"></div>
    </g:if>
</div>