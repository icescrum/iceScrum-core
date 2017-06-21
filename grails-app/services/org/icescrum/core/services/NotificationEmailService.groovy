/*
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */
package org.icescrum.core.services

import org.grails.comments.Comment
import org.icescrum.core.domain.*
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.core.utils.ServicesUtils
import org.springframework.web.context.request.RequestContextHolder as RCH

import java.util.concurrent.ThreadPoolExecutor

class NotificationEmailService {

    def mailService
    def grailsApplication
    def messageSource
    def springSecurityService

    static EVENT_LABELS = [(IceScrumEventType.CREATE): 'Created', (IceScrumEventType.UPDATE): 'Updated', (IceScrumEventType.DELETE): 'Deleted']

    @IceScrumListener(domain = 'story')
    void storyCUD(IceScrumEventType type, Story story, Map dirtyProperties) {
        try {
            def user = (User) springSecurityService.currentUser
            switch (type) {
                case IceScrumEventType.CREATE:
                    sendAlertCUD(story, user, type)
                    break
                case IceScrumEventType.UPDATE:
                    if (dirtyProperties.containsKey('state')) { // TODO make it work (here the state isn't considered dirty, probably already flushed before)
                        sendAlertState(story, user, type)
                    } else if (dirtyProperties.containsKey('addedComment')) {
                        sendAlertCommentAdded(story, dirtyProperties.addedComment)
                    } else if (dirtyProperties.containsKey('updatedComment')) {
                        sendAlertCommentUpdated(story)
                    } else {
                        sendAlertCUD(story, user, type)
                    }
                    break
                case IceScrumEventType.DELETE:
                    if (dirtyProperties.containsKey('newObject')) {
                        sendAlertAcceptedAs(dirtyProperties.followers, dirtyProperties.newObject, user)
                    } else {
                        sendAlertCUD(dirtyProperties, user, type)
                    }
            }
        } catch (Exception e) {
            if (log.debugEnabled) {
                e.printStackTrace()
            }
        }
    }

    @IceScrumListener(domain = 'task', eventType = IceScrumEventType.CREATE)
    void taskCreate(Task task, Map dirtyProperties) {
        if (task.type == Task.TYPE_URGENT && task.sprint.state == Sprint.STATE_INPROGRESS) {
            try {
                sendAlertNewUrgentTask(task, (User) springSecurityService.currentUser)
            } catch (Exception e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
            }
        }
    }

    void sendAlertCUD(story, User user, IceScrumEventType type) {
        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        Project project = (Project) story.backlog
        def subjectArgs = [project.name, story.name]
        def baseUrl = grailsApplication.config.icescrum.serverURL + '/p/' + project.pkey
        def permalink = baseUrl + '-' + story.uid
        def projectLink = baseUrl + '#project'
        def eventLabel = EVENT_LABELS[type]
        def description = type == IceScrumEventType.DELETE ? story.description ?: "" : null
        def listTo = (type == IceScrumEventType.CREATE) ? receiversByLocale(project.allUsers, user?.id, [type: 'onStory', pkey: project.pkey]) : receiversByLocale(story.followers, user?.id)
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event:$eventLabel to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails : group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.' + eventLabel.toLowerCase() + '.subject', (Locale) locale, subjectArgs),
                    view   : '/emails-templates/story' + eventLabel,
                    model  : [locale: locale, storyName: story.name, permalink: permalink, linkName: project.name, link: projectLink, description: description],
                    async  : true
            ])
        }
    }

    private void sendAlertNewUrgentTask(Task task, User user) {
        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        Project project = task.parentProject
        def subjectArgs = [project.name, task.name]
        def baseUrl = grailsApplication.config.icescrum.serverURL + '/p/' + project.pkey
        def permalink = baseUrl + '-T' + task.uid
        def projectLink = baseUrl + '#project'
        def listTo = receiversByLocale(project.allUsers, user?.id, [type: 'onUrgentTask', pkey: project.pkey])
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event urgent task created to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails : group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.task.created.subject', (Locale) locale, subjectArgs),
                    view   : '/emails-templates/taskCreated',
                    model  : [locale: locale, taskName: task.name, permalink: permalink, linkName: project.name, link: projectLink, description: task.description],
                    async  : true
            ])
        }
    }

    private void sendAlertState(Story story, User user, IceScrumEventType type) {
        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        def project = story.backlog
        def subjectArgs = [project.name, story.name]
        def baseUrl = grailsApplication.config.icescrum.serverURL + '/p/' + project.pkey
        def permalink = baseUrl + '-' + story.uid
        def projectLink = baseUrl + '#project'
        def eventLabel = EVENT_LABELS[type]
        def listTo = receiversByLocale(story.followers, user?.id)
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event:$eventLabel to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails : group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.changedState.subject', (Locale) locale, subjectArgs),
                    view   : '/emails-templates/storyChangedState',
                    model  : [state: getMessage(grailsApplication.config.icescrum.resourceBundles.storyStates[story.state], (Locale) locale), locale: locale, storyName: story.name, permalink: permalink, linkName: project.name, link: projectLink],
                    async  : true
            ])
        }
    }

    private void sendAlertCommentAdded(Story story, Comment comment) {
        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        def user = springSecurityService.currentUser
        def project = story.backlog
        def subjectArgs = [project.name, story.name]
        def baseUrl = grailsApplication.config.icescrum.serverURL + '/p/' + project.pkey
        def permalink = baseUrl + '-' + story.uid
        def projectLink = baseUrl + '#project'
        String text = ServicesUtils.textileToHtml(comment.body)
        def listTo = receiversByLocale(story.followers, user?.id)
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event: comment added to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails : group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.commented.subject', (Locale) locale, subjectArgs),
                    view   : '/emails-templates/storyCommented',
                    model  : [by: comment.poster.firstName + " " + comment.poster.lastName, comment: text, locale: locale, storyName: story.name, permalink: permalink, linkName: project.name, link: projectLink],
                    async  : true
            ])
        }
    }

    private void sendAlertCommentUpdated(Story story) {
        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        def user = springSecurityService.currentUser
        def project = story.backlog
        def subjectArgs = [project.name, story.name]
        def baseUrl = grailsApplication.config.icescrum.serverURL + '/p/' + project.pkey
        def permalink = baseUrl + '-' + story.uid
        def projectLink = baseUrl + '#project'
        def listTo = receiversByLocale(story.followers, user?.id)
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event: comment updated to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails : group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.commentEdited.subject', (Locale) locale, subjectArgs),
                    view   : '/emails-templates/storyCommentEdited',
                    model  : [by: user.firstName + " " + user.lastName, locale: locale, storyName: story.name, permalink: permalink, linkName: project.name, link: projectLink],
                    async  : true
            ])
        }
    }

    private void sendAlertAcceptedAs(Collection<User> followers, BacklogElement element, User user) {
        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        def project = element instanceof Feature ? element.backlog : element.backlog.parentRelease.parentProject
        def subjectArgs = [project.name, element.name]
        def projectLink = grailsApplication.config.icescrum.serverURL + '/p/' + project.pkey + '#project'
        def listTo = receiversByLocale(followers, user?.id)
        listTo?.each { locale, group ->
            def acceptedAs = getMessage(element instanceof Feature ? 'is.feature' : 'is.task', (Locale) locale)
            subjectArgs << acceptedAs
            if (log.debugEnabled) {
                log.debug "Send email, event: accepted as to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails : group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.acceptedAs.subject', (Locale) locale, subjectArgs),
                    view   : '/emails-templates/storyAcceptedAs',
                    model  : [acceptedAs: acceptedAs, locale: locale, elementName: element.name, linkName: project.name, link: projectLink],
                    async  : true
            ])
        }
    }

    void sendNewPassword(User user, String password) {
        def request = RCH.currentRequestAttributes().getRequest()
        def link = grailsApplication.config.icescrum.serverURL + '/login'
        if (log.debugEnabled) {
            log.debug "Send email, retrieve password to : ${user.email} (${user.username})"
        }
        send([
                to     : user.email,
                subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.user.retrieve.subject', user.locale, [user.username]),
                view   : "/emails-templates/retrieve",
                model  : [locale: user.locale, user: user, password: password, ip: request.getHeader('X-Forwarded-For') ?: request.getRemoteAddr(), link: link]
        ])
    }

    void sendInvitation(Invitation invitation, User inviter) {
        def link = grailsApplication.config.icescrum.serverURL + '/#/user/register/' + invitation.token
        def isProjectInvitation = invitation.type == Invitation.InvitationType.PROJECT
        def invitedIn = isProjectInvitation ? invitation.project.name.encodeAsHTML() : invitation.team.name.encodeAsHTML()
        def locale = inviter.locale
        def role = getMessage(grailsApplication.config.icescrum.resourceBundles.roles[invitation.futureRole], locale)
        send([
                to     : invitation.email,
                subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.user.invitation.subject', locale),
                view   : "/emails-templates/invitation",
                model  : [inviter: inviter, locale: locale, link: link, isProjectInvitation: isProjectInvitation, invitedIn: invitedIn, role: role]
        ])
        if (log.debugEnabled) {
            log.debug "Send invitation to: $invitation.email"
        }
    }

    void send(def options) {
        assert options.emails || options.to || options.cc
        assert options.view
        assert options.subject
        if (grailsApplication.config.icescrum.alerts.emailPerAccount && options.emails) {
            options.emails.each { toEmail ->
                mailService.sendMail {
                    if (options.async) // Warning : if async then error cannot be caught
                        async true
                    if (options.from)
                        from options.from
                    to toEmail
                    subject options.subject
                    body(
                            view: options.view,
                            plugin: options.plugin ?: "icescrum-core",
                            model: options.model ?: []
                    )
                }
            }
        } else {
            options.bcc = options.emails ?: options.bcc
            if(((ThreadPoolExecutor)mailService.mailExecutorService)?.isTerminated()){
                mailService.afterPropertiesSet()
            }
            mailService.sendMail {
                if (options.async) // Warning : if async then error cannot be caught
                    async true
                if (options.from)
                    from options.from
                if (options.to)
                    to options.to
                if (options.cc)
                    cc options.cc
                if (options.bcc)
                    bcc options.bcc
                subject options.subject
                body(
                        view: options.view,
                        plugin: options.plugin ?: "icescrum-core",
                        model: options.model ?: []
                )
            }
        }
    }

    private String getMessage(String code, Locale locale, args = null, String defaultCode = null) {
        return messageSource.getMessage(code, args ? args.toArray() : null, defaultCode ?: code, locale)
    }

    private static Map receiversByLocale(candidates, Long senderId, Map options = null) {
        candidates?.findAll { User candidate ->
            candidate.enabled && (candidate.id != senderId) && (!options || (options.pkey in candidate.preferences.emailsSettings[options.type]))
        }?.collect { User receiver ->
            [email: receiver.email, locale: receiver.locale]
        }?.unique()?.groupBy { receiver ->
            receiver.locale
        }
    }
}
