/*
 * Copyright (c) 2019 Kagilum SAS
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors: Vincent Barrier (vbarrier@kagilum.com)
 *
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.icescrum.core.domain.Hook
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.utils.DateUtils

@Transactional
class HookService extends IceScrumEventPublisher {

    def grailsApplication
    def appService

    def save(Hook hook) {
        manageAndCleanEvents(hook)
        hook.save(flush: true)
    }

    def update(Hook hook) {
        manageAndCleanEvents(hook)
        if (hook.enabled && hook.isDirty("enabled")) {
            hook.lastError = ""
            hook.countErrors = 0
        }
        hook.save(flush: true)
    }

    def delete(Hook hook, boolean forceDelete = false) {
        if (!forceDelete && hook.url.contains("hooks.zapier.com")) { // https://zapier.com/developer/documentation/v2/rest-hooks/#optional-reverse-unsubscribe-a-call-from-your-app-to-zapier
            def http = new HTTPBuilder(hook.url)
            http.getClient().getParams().setParameter("http.connection.timeout", grailsApplication.config.icescrum.hooks.httpTimeout)
            http.getClient().getParams().setParameter("http.socket.timeout", grailsApplication.config.icescrum.hooks.socketTimeout)
            http.request(Method.DELETE) {}
        }
        hook.delete(flush: true)
    }

    def unMarshall(hookXml, options) { // TODO add global export & workspace exports
        Hook.withTransaction(readOnly: !options.save) { transaction ->
            def hook = new Hook(
                    url: hookXml.url.text(),
                    secret: hookXml.secret.text() ?: null,
                    source: hookXml.source.text() ?: null,
                    events: hookXml.events.text().split(','),
                    enabled: hookXml.enabled.text().toBoolean(),
                    lastError: hookXml.lastError.text() ?: null,
                    ignoreSsl: hookXml.ignoreSsl.text().toBoolean(),
                    countErrors: hookXml.countErrors.text().toInteger(),
                    workspaceType: hookXml.workspaceType.text() ?: null,
                    workspaceId: hookXml.workspaceId.text() ? hookXml.workspaceId.text().toLong() : null,
                    lastUpdated: DateUtils.parseDateFromExport(hookXml.lastUpdated.text()),
                    dateCreated: DateUtils.parseDateFromExport(hookXml.dateCreated.text()),
                    dateLastRequest: DateUtils.parseDateFromExport(hookXml.dateLastRequest.text()),
                    eventMessageRendererClass: hookXml.eventMessageRendererClass.text() ?: null
            )
            if (options.save) {
                hook.save()
            }
            return (Hook) importDomainsPlugins(hookXml, hook, options)
        }
    }

    private manageAndCleanEvents(hook) {
        def events = hook.workspaceType ? grailsApplication.config.icescrum.workspaces."${hook.workspaceType}".hooks.events : grailsApplication.config.icescrum.hooks.events
        def eventsToSave = []
        hook.events?.each {
            def event = it.trim().replaceAll(' ', '')
            if (event in events) {
                eventsToSave << event
            }
        }
        hook.events = eventsToSave ?: null // Will throw a grail validation error
        return hook
    }
}