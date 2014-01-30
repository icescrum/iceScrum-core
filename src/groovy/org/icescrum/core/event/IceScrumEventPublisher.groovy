/*
 * Copyright (c) 2014 Kagilum.
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
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
package org.icescrum.core.event

import org.icescrum.core.event.IceScrumSynchronousEvent.EventType

abstract class IceScrumEventPublisher {

    private Map<EventType, List<Closure>> listenersByEventType = [:]

    synchronized void registerListener(EventType eventType, Closure listener) {
        def listeners = listenersByEventType[eventType]
        if (listeners == null) {
            def emptyListeners = []
            listenersByEventType[eventType] = emptyListeners
            listeners = emptyListeners
        }
        listener.delegate = this
        listeners.add(listener)
    }

    synchronized void registerListener(Closure listener) {
        EventType.values().each { EventType type ->
            if (type != EventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                registerListener(type, listener)
            }
        }
    }

    synchronized void publishSynchronousEvent(IceScrumSynchronousEvent event) {
        listenersByEventType[event.type]?.each { it(event) }
    }
}
