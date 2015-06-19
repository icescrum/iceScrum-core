%{--
-
- Copyright (c) 2015 Kagilum SAS
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
- Nicolas Noullet (nnoullet@kagilum.com)
--}%
<%@ page contentType="text/html"%>
<g:if test="${isProjectInvitation}">
        <g:message locale="${locale}"
                   code='is.template.email.user.invitation.project.text'
                   args="[(inviter.firstName + ' ' + inviter.lastName), inviter.username, invitedIn, role]"/>
</g:if><g:else>
        <g:message locale="${locale}"
                   code='is.template.email.user.invitation.team.text'
                   args="[(inviter.firstName + ' ' + inviter.lastName), inviter.username, invitedIn, role]"/>
</g:else>

<br/><br/>
<g:message locale="${locale}"
           code='is.template.email.user.invitation.text'
           args="[link]"/>
<br/><br/>--<br/>
<g:message locale="${locale}" code='is.template.email.footer.website'/>