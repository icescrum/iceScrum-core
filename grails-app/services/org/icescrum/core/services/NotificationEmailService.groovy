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
 */
package org.icescrum.core.services

import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Task
import org.icescrum.core.event.IceScrumBacklogElementEvent
import org.icescrum.core.event.IceScrumTaskEvent
import org.springframework.context.ApplicationListener
import org.icescrum.core.event.IceScrumStoryEvent
import org.icescrum.core.domain.Story
import org.grails.comments.Comment
import org.icescrum.core.domain.BacklogElement
import org.icescrum.core.domain.User
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.icescrum.core.event.IceScrumEvent
import org.icescrum.core.domain.Feature
import org.icescrum.core.support.ApplicationSupport
import org.eclipse.mylyn.wikitext.core.util.ServiceLocator
import org.eclipse.mylyn.wikitext.core.parser.builder.HtmlDocumentBuilder
import org.eclipse.mylyn.wikitext.core.parser.MarkupParser


class NotificationEmailService implements ApplicationListener<IceScrumEvent> {

    def mailService
    def grailsApplication
    def messageSource

    void onApplicationEvent(IceScrumEvent e) {
        try {
            if (e instanceof IceScrumStoryEvent && e.type != IceScrumStoryEvent.EVENT_BEFORE_DELETE && e.type in IceScrumStoryEvent.EVENT_CUD ) {
                sendAlertCUD((Story) e.source, (User) e.doneBy, e.type)
            } else if (e instanceof IceScrumStoryEvent && e.type in IceScrumStoryEvent.EVENT_STATE_LIST) {
                sendAlertState((Story) e.source, (User) e.doneBy, e.type)
            } else if (e instanceof IceScrumBacklogElementEvent && e.source instanceof Story && e.type in IceScrumBacklogElementEvent.EVENT_COMMENT_LIST) {
                sendAlertComment((Story) e.source, (User) e.doneBy, e.type, e.comment)
            } else if (e instanceof IceScrumStoryEvent && e.type in IceScrumStoryEvent.EVENT_ACCEPTED_AS_LIST) {
                sendAlertAcceptedAs((BacklogElement) e.source, (User) e.doneBy, e.type)
            } else if (e instanceof IceScrumTaskEvent && e.type in IceScrumTaskEvent.EVENT_CREATED && ((Task )e.source).type == Task.TYPE_URGENT && ((Task )e.source).sprint.state == Sprint.STATE_INPROGRESS){
                sendAlertNewUrgentTask((Task) e.source, (User) e.doneBy, e.type)
            }
        } catch (Exception expt) {
            if (log.debugEnabled) expt.printStackTrace()
        }

    }

    void sendAlertCUD(Story story, User user, String type) {

        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }

        Product product = (Product)story.backlog
        def subjectArgs = [product.name, story.name]
        def permalink = grailsApplication.config.grails.serverURL + '/p/' + product.pkey + '-' + story.uid
        def projectLink = grailsApplication.config.grails.serverURL + '/p/' + product.pkey + '#project'
        def event = (IceScrumEvent.EVENT_CREATED == type) ? 'Created' : (IceScrumEvent.EVENT_UPDATED == type ? 'Updated' : 'Deleted')

        def listTo = (type == IceScrumEvent.EVENT_CREATED) ? receiversByLocale(product.allUsers, user.id, [type:'onStory',pkey:product.pkey]) : receiversByLocale(story.followers, user.id)
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event:${type} to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails: group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.' + event.toLowerCase() + '.subject', (Locale) locale, subjectArgs),
                    view: '/emails-templates/story' + event,
                    model: [locale: locale, storyName: story.name, permalink: permalink, linkName: product.name, link: projectLink, description:IceScrumEvent.EVENT_BEFORE_DELETE ? story.description?:"" : null]
            ])
        }
    }

    private void sendAlertNewUrgentTask(Task task, User user, String type) {

        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }
        Product product = task.parentProduct
        def subjectArgs = [product.name, task.name]
        def permalink = grailsApplication.config.grails.serverURL + '/p/' + product.pkey + '-T' + task.uid
        def projectLink = grailsApplication.config.grails.serverURL + '/p/' + product.pkey + '#project'

        def listTo = receiversByLocale(product.allUsers, user.id, [type:'onUrgentTask',pkey:product.pkey])
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event:${type} to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails: group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.task.created.subject', (Locale) locale, subjectArgs),
                    view: '/emails-templates/taskCreated',
                    model: [locale: locale, taskName: task.name, permalink: permalink, linkName: product.name, link: projectLink, description:IceScrumEvent.EVENT_BEFORE_DELETE ? task.description?:null : null]
            ])
        }
    }

    private void sendAlertState(Story story, User user, String type) {

        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }

        def subjectArgs = [story.backlog.name, story.name]
        def permalink = grailsApplication.config.grails.serverURL + '/p/' + story.backlog.pkey + '-' + story.uid
        def projectLink = grailsApplication.config.grails.serverURL + '/p/' + story.backlog.pkey + '#project'

        def listTo = receiversByLocale(story.followers, user.id)
        listTo?.each { locale, group ->
            if (log.debugEnabled) {
                log.debug "Send email, event:${type} to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails: group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.changedState.subject', (Locale) locale, subjectArgs),
                    view: '/emails-templates/storyChangedState',
                    model: [state: getMessage('is.template.email.story.changedState.' + type.toLowerCase(), (Locale) locale), locale: locale, storyName: story.name, permalink: permalink, linkName: story.backlog.name, link: projectLink]
            ])
        }

    }

    private void sendAlertComment(Story story, User user, String type, Comment comment) {

        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }

        def subjectArgs = [story.backlog.name, story.name]
        def permalink = grailsApplication.config.grails.serverURL + '/p/' + story.backlog.pkey + '-' + story.uid
        def projectLink = grailsApplication.config.grails.serverURL + '/p/' + story.backlog.pkey + '#project'

        if (type == IceScrumBacklogElementEvent.EVENT_COMMENT_ADDED) {

            StringWriter text = new StringWriter()
            HtmlDocumentBuilder builder = new HtmlDocumentBuilder(text)
            builder.emitAsDocument = false
            MarkupParser parser = new MarkupParser()
            parser.markupLanguage = ServiceLocator.instance.getMarkupLanguage('Textile')
            parser.builder = builder
            parser.parse(comment.body).encodeAsHTML()

            def listTo = receiversByLocale(story.followers, user.id)
            listTo?.each { locale, group ->
                if (log.debugEnabled) {
                    log.debug "Send email, event:${type} to : ${group*.email.toArray()} with locale : ${locale}"
                }
                send([
                        emails: group*.email.toArray(),
                        subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.commented.subject', (Locale) locale, subjectArgs),
                        view: '/emails-templates/storyCommented',
                        model: [by: comment.poster.firstName + " " + comment.poster.lastName, comment: text, locale: locale, storyName: story.name, permalink: permalink, linkName: story.backlog.name, link: projectLink]
                ])
            }
        } else if (type == IceScrumBacklogElementEvent.EVENT_COMMENT_UPDATED) {
            def listTo = receiversByLocale(story.followers, user.id)
            listTo?.each { locale, group ->
                if (log.debugEnabled) {
                    log.debug "Send email, event:${type} to : ${group*.email.toArray()} with locale : ${locale}"
                }
                send([
                        emails: group*.email.toArray(),
                        subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.commentEdited.subject', (Locale) locale, subjectArgs),
                        view: '/emails-templates/storyCommentEdited',
                        model: [by: user.firstName + " " + user.lastName, locale: locale, storyName: story.name, permalink: permalink, linkName: story.backlog.name, link: projectLink]
                ])
            }
        }
    }

    private void sendAlertAcceptedAs(BacklogElement element, User user, String type) {

        if (!ApplicationSupport.booleanValue(grailsApplication.config.icescrum.alerts.enable)) {
            return
        }

        def product = element instanceof Feature ? element.backlog : element.backlog.parentRelease.parentProduct
        def subjectArgs = [product.name, element.name]
        def projectLink = grailsApplication.config.grails.serverURL + '/p/' + product.pkey + '#project'

        def listTo = receiversByLocale(element.followers, user.id)
        listTo?.each { locale, group ->
            def acceptedAs = getMessage(element instanceof Feature ? 'is.feature' : 'is.task', (Locale) locale)
            subjectArgs << acceptedAs
            if (log.debugEnabled) {
                log.debug "Send email, event:${type} to : ${group*.email.toArray()} with locale : ${locale}"
            }
            send([
                    emails: group*.email.toArray(),
                    subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.story.acceptedAs.subject', (Locale) locale, subjectArgs),
                    view: '/emails-templates/storyAcceptedAs',
                    model: [acceptedAs: acceptedAs, locale: locale, elementName: element.name, linkName: product.name, link: projectLink]
            ])
        }
    }

    void sendNewPassword(User user, String password) {
        def link = grailsApplication.config.grails.serverURL + '/login'
        def request = RCH.currentRequestAttributes().getRequest()
        if (log.debugEnabled) {
            log.debug "Send email, retrieve password to : ${user.email} (${user.username})"
        }
        send([
                to: user.email,
                subject: grailsApplication.config.icescrum.alerts.subject_prefix + getMessage('is.template.email.user.retrieve.subject', user.locale, [user.username]),
                view: "/emails-templates/retrieve",
                model: [locale: user.locale, user: user, password: password, ip: request.getHeader('X-Forwarded-For') ?: request.getRemoteAddr(), link: link]
        ])
    }

    void send(def options) {

        assert options.emails || options.to || options.cc
        assert options.view
        assert options.subject

        if (grailsApplication.config.icescrum.alerts.emailPerAccount && options.emails){
            options.emails.each{ def toEmail ->
                mailService.sendMail {
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
        }else{
            options.bcc = options.emails?:options.bcc
            mailService.sendMail {
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

    String getMessage(String code, Locale locale, args = null, String defaultCode = null) {
        return messageSource.getMessage(code, args ? args.toArray() : null, defaultCode ?: code, locale)
    }

    private Map receiversByLocale(candidates, long sender, Map options = null) {
        candidates?.findAll { User candidate ->
            candidate.enabled && (candidate.id != sender) && (!options || (options.pkey in candidate.preferences.emailsSettings?."$options.type"))
        }?.collect { User receiver ->
            [email: receiver.email, locale: receiver.locale]
        }?.unique()?.groupBy { receiver ->
            receiver.locale
        }
    }
}
