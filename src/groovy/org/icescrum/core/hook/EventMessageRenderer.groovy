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

package org.icescrum.core.hook

import grails.util.Holders
import org.icescrum.core.services.PushService;

trait EventMessageRenderer {
    abstract String render(def object, def events)

    //use the JSON generate from push service if found to speed up things
    static String getCachedJSONObjectInThreadCache(object, events) {
        if (!Holders.grailsApplication.isDomainClass(object.getClass())) {
            return null
        }
        def event = events.size() > 1 ? events[0] : events[0]
        String eventPush = (event.split(/\./)[1]).toUpperCase()
        def threadId = Thread.currentThread().getId()
        def messageId = PushService.generatedMessageId(object, eventPush)
        return Holders.grailsApplication.mainContext.pushService.bufferedThreads?.get(threadId)*.value*.find {
            messageId == it.messageId
        }?.content ?: null
    }
}