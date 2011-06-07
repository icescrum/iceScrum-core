/*
 * Copyright (c) 2011 Kagilum.
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
package org.icescrum.atmosphere;

import org.atmosphere.cpr.PerRequestBroadcastFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class StreamFilter implements PerRequestBroadcastFilter {

    @Override
    public BroadcastAction filter(HttpServletRequest request, HttpServletResponse response, Object message) {
        String msg = message.toString();
        msg = msg.length() + ";" + msg + ';';
        return new BroadcastAction(BroadcastAction.ACTION.CONTINUE, msg);
    }

    @Override
    public BroadcastAction filter(Object originalMessage, Object message) {
        return new BroadcastAction(message);
    }
}
