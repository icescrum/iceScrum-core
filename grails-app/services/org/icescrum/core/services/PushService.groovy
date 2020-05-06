/*
 * Copyright (c) 2015 Kagilum SAS.
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

import grails.converters.JSON
import grails.transaction.Transactional
import grails.util.GrailsNameUtils
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.Broadcaster
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener
import org.icescrum.atmosphere.IceScrumBroadcaster
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.User
import org.icescrum.core.domain.WorkspaceType
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ProfilingSupport

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

@Transactional(readOnly = true)
class PushService {

    def atmosphereMeteor
    def disabledThreads = new CopyOnWriteArrayList<>()
    def bufferedThreads = new ConcurrentHashMap<Long, ConcurrentHashMap<String, ArrayList>>()

    private static final String BUFFER_MESSAGE_DELIMITER = "#-|-#"

    void broadcastToWorkspaceChannel(String namespace, String eventType, object, long workspaceId, String workspaceType) {
        broadcastToChannel(namespace, eventType, object, "/stream/app/$workspaceType-$workspaceId")
    }

    void broadcastToWorkspaceChannel(IceScrumEventType eventType, object, long workspaceId, String workspaceType) {
        broadcastToWorkspaceChannel(getNamespaceFromDomain(object), eventType.name(), object, workspaceId, workspaceType)
    }

    void broadcastToProjectRelatedChannels(IceScrumEventType eventType, object, long projectId) {
        broadcastToWorkspaceChannel(eventType, object, projectId, WorkspaceType.PROJECT)
        Long portfolioId = Project.getPortfolioId(projectId)
        if (portfolioId) {
            broadcastToWorkspaceChannel(eventType, object, portfolioId, WorkspaceType.PORTFOLIO)
        }
    }

    void broadcastToProjectRelatedChannels(String namespace, String eventType, object, long projectId) {
        broadcastToWorkspaceChannel(namespace, eventType, object, projectId, WorkspaceType.PROJECT)
        Long portfolioId = Project.getPortfolioId(projectId)
        if (portfolioId) {
            broadcastToWorkspaceChannel(namespace, eventType, object, portfolioId, WorkspaceType.PORTFOLIO)
        }
    }

    void broadcastToPortfolioChannel(IceScrumEventType eventType, object, long portfolioId) {
        broadcastToWorkspaceChannel(eventType, object, portfolioId, WorkspaceType.PORTFOLIO)
    }

    void broadcastToPortfolioChannel(String namespace, String eventType, object, long portfolioId) {
        broadcastToWorkspaceChannel(namespace, eventType, object, portfolioId, WorkspaceType.PORTFOLIO)
    }

    void broadcastToChannel(String namespace, String eventType, object, String channel = '/stream/app/*') {
        if (!isDisabledPushThread()) {
            def message = buildMessage(namespace, eventType, object)
            ProfilingSupport.startProfiling("broadcastToChannel-$message.messageId", "broadcastToChannel")
            if (!isBufferedThread()) {
                Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
                if (broadcaster) {
                    if (log.debugEnabled) {
                        log.debug("Broadcast to everybody on channel $channel - $namespace - $eventType")
                    }
                    broadcaster.broadcast(message as JSON)
                }
            } else {
                if (log.debugEnabled) {
                    log.debug("Buffered broadcast for channel $channel - $namespace - $eventType")
                }
                bufferMessage(channel, message)
            }
            ProfilingSupport.endProfiling("broadcastToChannel-$message.messageId", "broadcastToChannel")
        }
    }

    void broadcastToUsers(String namespace, String eventType, object, Collection<String> usernames) {
        def channel = '/stream/app/*'
        Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
        if (broadcaster) {
            try {
                Set<AtmosphereResource> resources = broadcaster.atmosphereResources?.findAll { AtmosphereResource resource ->
                    resource.request?.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)?.username in usernames
                }
                if (resources) {
                    if (log.debugEnabled) {
                        log.debug('Broadcast to ' + resources*.uuid().join(', ') + ' on channel ' + channel)
                    }
                    broadcaster.broadcast(buildMessage(namespace, eventType, object) as JSON, resources)
                }
            } catch (Exception e) {
                // Request object no longer valid.  This object has been cancelled, see https://github.com/Atmosphere/atmosphere/issues/1052
            }
        }
    }

    void broadcastToUsers(IceScrumEventType eventType, object, Collection<User> users) {
        if (!isDisabledPushThread()) {
            broadcastToUsers(getNamespaceFromDomain(object), eventType.name(), object, users*.username)
        }
    }

    void bufferMessage(channel, message) {
        def threadId = Thread.currentThread().getId()
        def messages = bufferedThreads.get(threadId)."$channel"
        if (!messages) {
            bufferedThreads.get(threadId)."$channel" = [] as LinkedHashSet
            messages = bufferedThreads.get(threadId)."$channel"
        }
        def existingMessage = messages.find {
            it.messageId == message.messageId
        }
        if (!existingMessage) {
            messages << message
        } else { //replace with new message but keep order of change in the request
            messages.remove(existingMessage)
            messages.add(message)
            if (log.debugEnabled) {
                log.debug('replace with latest content message (' + message.messageId + ') on channel ' + channel + ' ' + message.content)
            }
        }
    }

    void disablePushForThisThread() {
        if (!isDisabledPushThread()) {
            disabledThreads.add(Thread.currentThread().getId())
        }
    }

    void enablePushForThisThread() {
        if (isDisabledPushThread()) {
            disabledThreads.remove(Thread.currentThread().getId())
        }
    }

    boolean isDisabledPushThread() {
        return disabledThreads.contains(Thread.currentThread().getId())
    }

    void bufferPushForThisThread() {
        bufferedThreads.putIfAbsent(Thread.currentThread().getId(), new ConcurrentHashMap<String, ArrayList>())
    }

    void resumePushForThisThread() {
        if (isBufferedThread()) {
            def messagesPerChannels = bufferedThreads.remove(Thread.currentThread().getId())
            messagesPerChannels?.each { channel, messages ->
                Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory?.lookup(IceScrumBroadcaster.class, channel)
                if (broadcaster) {
                    if (log.debugEnabled) {
                        log.debug("broadcast " + messages.size() + " buffered messages on channel $channel")
                    }
                    broadcaster.broadcast(messages.collect({ it as JSON }).join(BUFFER_MESSAGE_DELIMITER))
                }
            }
        }
    }

    boolean isBufferedThread() {
        return bufferedThreads.containsKey(Thread.currentThread().getId())
    }

    private static getNamespaceFromDomain(domain) {
        return GrailsNameUtils.getShortName(domain.class).toLowerCase()
    }

    public static generatedMessageId(object, eventType) {
        return object instanceof Map && object.messageId ? object.messageId : (object.class ? getNamespaceFromDomain(object) : UUID.randomUUID().toString()) + '-' + eventType + '-' + object.id
    }

    public static def buildMessage(String namespace, String eventType, object) {
        def message = [
                messageId: generatedMessageId(object, eventType),
                namespace: namespace,
                content  : (object as JSON).toString().encodeAsBase64(),
                eventType: eventType]
        return message
    }
}