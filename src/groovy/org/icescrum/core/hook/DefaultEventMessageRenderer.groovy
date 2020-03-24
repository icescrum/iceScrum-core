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

import grails.converters.JSON
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class DefaultEventMessageRenderer implements EventMessageRenderer {

    private Logger logger = LoggerFactory.getLogger("org.icescrum.core.hook.DefaultEventMessageRenderer")

    String render(def object, def events, def dirtyProperties) {
        def cachedObject = getCachedJSONObjectInThreadCache(object, events)
        if (cachedObject && logger.isDebugEnabled()) {
            logger.debug('found json object in Thread cache use it')
        }
        return cachedObject ?: (object as JSON).toString()
    }
}
