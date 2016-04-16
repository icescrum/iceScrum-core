%{--
-
- This file is part of iceScrum.
-
- iceScrum is free software: you can redistribute it and/or modify
- it under the terms of the GNU Affero General Public License as published by
- the Free Software Foundation, either version 3 of the License.
-
- iceScrum is distributed in the hope that it will be useful,
- but WITHOUT ANY WARRANTY; without even the implied warranty of
- MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
- GNU General Public License for more details.
-
- You should have received a copy of the GNU Affero General Public License
- along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
-
- Authors:
-
- Vincent Barrier (vbarrier@kagilum.com)
- Nicolas Noullet (nnoullet@kagilum.com)
--}%
<script type="application/ld+json">
{
  "@context": "http://schema.org",
  "@type": "EmailMessage",
  "potentialAction": {
    "@type": "ViewAction",
    "url": "${permalink}",
    "name": "${g.message(code:'is.template.email.markup.name', locale:locale)}"
  },
  "publisher": {
    "@type": "Organization",
    "name": "iceScrum",
    "url": "${grailsApplication.config.grails.serverURL}"
  }
}
</script>
<%@ page contentType="text/html"%>
<g:message
        locale="${locale}"
        code='is.template.email.story.commentDeleted.text'
        args="[storyName,permalink,by]"/>
<br/><br/>--<br/>
<g:message locale="${locale}" code='is.template.email.footer.reason.follow' args="[link,linkName]"/>
<g:message locale="${locale}" code='is.template.email.footer.unfollow' args="[permalink]"/>
<g:message locale="${locale}" code='is.template.email.footer.preferences.information'/>
<br/>-<br/>
<g:message locale="${locale}" code='is.template.email.footer.website'/>
