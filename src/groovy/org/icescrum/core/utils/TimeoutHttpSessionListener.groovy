package org.icescrum.core.utils

import javax.servlet.http.HttpSessionEvent
import javax.servlet.http.HttpSessionListener

/*
 * Copyright (c) 2017 Kagilum SAS
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
class TimeoutHttpSessionListener implements HttpSessionListener {
    def config
    @Override

    void sessionCreated(HttpSessionEvent httpSessionEvent) {
        httpSessionEvent.session.maxInactiveInterval = config.icescrum.sessionTimeoutSeconds
    }

    @Override
    void sessionDestroyed(HttpSessionEvent httpSessionEvent) { /* nothing to implement */ }
}