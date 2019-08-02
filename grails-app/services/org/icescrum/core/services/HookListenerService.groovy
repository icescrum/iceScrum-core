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

import org.icescrum.core.domain.Hook
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.event.IceScrumListener
import org.icescrum.core.support.ApplicationSupport

class HookListenerService {

    def grailsApplication
    def hookService
    def commentService

    @IceScrumListener(domains = ['*'])
    void hook(IceScrumEventType type, Object hookableObject, Map dirtyProperties) {
        def allHooks = []
        def events = []
        if (type in [IceScrumEventType.CREATE, IceScrumEventType.UPDATE, IceScrumEventType.DELETE]) {
            events << getEventName(hookableObject, type)
            if (type == IceScrumEventType.UPDATE && hookableObject.hasProperty("state") && dirtyProperties.state != null && dirtyProperties.state != hookableObject.state) {
                events << getEventName(hookableObject, "state")  //the only case where there is 2 events possible for an action
            } else if (type == IceScrumEventType.UPDATE && dirtyProperties.addedComment) {
                events = [getEventName(hookableObject, "addedComment")]
            } else if (type == IceScrumEventType.UPDATE && dirtyProperties.updatedComment) {
                events = [getEventName(hookableObject, "updatedComment")]
            } else if (type == IceScrumEventType.UPDATE && dirtyProperties.removedComment) {
                events = [getEventName(hookableObject, "removedComment")]
            }
            String workspaceType = grailsApplication.config.icescrum.workspaces.find {
                workspace -> workspace.value.hooks?.events?.find { hook -> hook == events[0] }
            }?.value?.type ?: null
            if (!workspaceType && !grailsApplication.config.icescrum.hooks.events.find { hook -> hook == events[0] }) {
                return
            } else if (workspaceType) {
                Long workspaceId = findWorkspaceId(type == IceScrumEventType.DELETE ? dirtyProperties : hookableObject, workspaceType)
                if (workspaceId) {
                    if (log.debugEnabled) {
                        log.debug("hook event fired on $workspaceType $workspaceId: $events")
                    }
                    allHooks = Hook.queryFindAllByWorkspaceTypeAndWorkspaceIdAndEventsFromList(workspaceType, workspaceId, events)
                }
            } else if (grailsApplication.config.icescrum.hooks.enable) {
                if (log.debugEnabled) {
                    log.debug("hook event fired out of workspace: $events")
                }
                allHooks = Hook.queryFindAllByWorkspaceTypeNullAndWorkspaceIdNullAndEventsFromList(events)
            }
        }
        if (allHooks) {
            if (log.debugEnabled) {
                log.debug("hooks found ids: ${allHooks.collect { it.id }.join(', ')}")
            }
            allHooks.groupBy { it.eventMessageRendererClass }.each { eventMessageRendererClass, hooks ->
                def objectToRender
                //very special case for comment
                if (events[0].endsWith("Comment")) {
                    def comment = dirtyProperties."addedComment" ?: (dirtyProperties."updatedComment" ?: dirtyProperties."removedComment")
                    objectToRender = commentService.getRenderableComment(comment, hookableObject)
                } else {
                    //remove all hibernate collection stuff to prevent exception
                    objectToRender = IceScrumEventType.DELETE == type ? dirtyProperties.findAll { prop -> return !(prop.value instanceof Collection) } : hookableObject
                }
                //find the renderer
                def payload = Class.forName("${eventMessageRendererClass ?: 'org.icescrum.core.hook.DefaultEventMessageRenderer'}").newInstance().render(objectToRender, events)
                hooks.each { hook ->
                    Hook.async.task {
                        def http = new HTTPBuilder(hook.url)
                        if (hook.ignoreSsl) {
                            http.ignoreSSLIssues()
                        }
                        http.getClient().getParams().setParameter("http.connection.timeout", grailsApplication.config.icescrum.hooks.httpTimeout)
                        http.getClient().getParams().setParameter("http.socket.timeout", grailsApplication.config.icescrum.hooks.socketTimeout)
                        http.request(Method.POST, ContentType.JSON) {
                            def eventToDisplay = events.size() > 1 ? hook.events.contains(events[1]) ? events[1] : events[0] : events //if we have update and state send the correct one
                            headers.'x-icescrum-event' = eventToDisplay
                            if (hook.secret) {
                                def signature = ApplicationSupport.hmac(payload, hook.secret)
                                if (log.debugEnabled) {
                                    log.debug("hook (id:$hook.id) - secret set with ${signature}")
                                }
                                headers.'x-icescrum-signature' = signature
                            }
                            if (log.debugEnabled) {
                                log.debug("hook (id:$hook.id) - request sent ${hook.url} for $eventToDisplay")
                            }
                            body = payload
                            response.failure = { resp ->
                                withTransaction {
                                    def hookToUpdate = Hook.get(hook.id)
                                    if (resp.status == 410) { //case zapier or other restWebhook https://zapier.com/developer/documentation/v2/rest-hooks/#step-2-sending-hooks-a-call-from-your-app-to-zapier
                                        if (log.debugEnabled) {
                                            log.debug("hook (id:$hook.id) - doesn't exist, delete it")
                                        }
                                        hookService.delete(hookToUpdate, true)
                                    } else {
                                        hookToUpdate.countErrors += 1
                                        hookToUpdate.enabled = grailsApplication.config.icescrum.hooks.disableAfterErrors > 0 ? !(hookToUpdate.countErrors >= grailsApplication.config.icescrum.hooks.disableAfterErrors) : true
                                        hookToUpdate.lastError = "StatusCode: $resp.status - statusLine: $resp.statusLine - data: $resp.data"
                                        if (log.debugEnabled) {
                                            log.debug("hook (id:$hook.id) - request failure $hookToUpdate.lastError")
                                        }
                                        hookToUpdate.save(flush: true)
                                    }
                                }
                            }
                            response.success = {
                                withTransaction {
                                    def hookToUpdate = Hook.get(hook.id)
                                    if (log.debugEnabled) {
                                        log.debug("hook (id:$hook.id) - request success")
                                    }
                                    hookToUpdate.dateLastRequest = new Date()
                                    if (hookToUpdate.countErrors) {
                                        hookToUpdate.countErrors = 0
                                        hookToUpdate.lastError = null
                                        hookToUpdate.save(flush: true)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private static String getEventName(Object object, IceScrumEventType type) {
        return getEventName(object, type.toString().toLowerCase())
    }

    private static String getEventName(Object object, String type) {
        return ApplicationSupport.getCamelCaseShortName(object.class) + '.' + type
    }

    private static Long findWorkspaceId(object, workspaceType) {
        if (object instanceof Map) {
            return object."$workspaceType"?.id ?: object."parent${workspaceType.capitalize()}"?.id
        } else {
            return object.hasProperty("$workspaceType") ? object."$workspaceType"?.id : (object.hasProperty("parent${workspaceType.capitalize()}") ? object."parent${workspaceType.capitalize()}"?.id : null)
        }
    }
}
