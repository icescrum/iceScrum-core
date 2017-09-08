%{--
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
-
- Authors:
-
- Vincent Barrier (vbarrier@kagilum.com)
--}%
<%@ page contentType="text/html" %>
Bonjour,<br/>
<br/>
Nouveau rapport d'erreur V7 :<br/>

------------------------------------------------------------------------------<br/>

IP        : ${ip}<br/>
Server ID : ${appID}<br/>
Version   : ${version}<br/>
User      : ${user}<br/>

------------------------------------------------------------------------------<br/>

URI : ${uri}<br/>
Params :<br/>
${uriParams}<br/>

------------------------------------------------------------------------------<br/>

Message :<br/>
${message}<br/>

------------------------------------------------------------------------------<br/>

StackTrace :<br/>
${stackTrace}<br/>


