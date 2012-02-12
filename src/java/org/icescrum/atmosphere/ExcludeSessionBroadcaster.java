/*
* Copyright 2012 Kagilum
*
* Licensed under the Apache License, Version 2.0 (the "License"); you may not
* use this file except in compliance with the License. You may obtain a copy of
* the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
* WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
* License for the specific language governing permissions and limitations under
* the License.
*
* Author : Vincent Barrier - vbarrier@kagilum.com
*
*/
package org.icescrum.atmosphere;

import org.atmosphere.cpr.AtmosphereResource;
import org.atmosphere.cpr.AtmosphereServlet;
import org.atmosphere.cpr.BroadcasterFuture;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;

public class ExcludeSessionBroadcaster extends org.atmosphere.util.ExcludeSessionBroadcaster {

    static final String SESSION_ID_ATTRIBUTE = "session_id_atmo";

    public ExcludeSessionBroadcaster(String name, AtmosphereServlet.AtmosphereConfig config) {
        super(name, config);
    }

    @Override
    public <T> Future<T> broadcast(T msg, HttpSession s) {

        if (destroyed.get()) {
            throw new IllegalStateException("This Broadcaster has been destroyed and cannot be used");
        }

        Set<AtmosphereResource<?, ?>> subset = new HashSet<AtmosphereResource<?, ?>>();
        subset.addAll(resources);

        for (AtmosphereResource<?, ?> r : resources) {
                String sid = (String)((HttpServletRequest) r.getRequest()).getAttribute(SESSION_ID_ATTRIBUTE);
                if (s != null){
                    if (!r.getAtmosphereResourceEvent().isCancelled() && s.getId().equals(sid)) {
                        subset.remove(r);
                    }
                }
        }

        start();
        Object newMsg = filter(msg);
        if (newMsg == null) {
            return null;
        }

        BroadcasterFuture<Object> f = new BroadcasterFuture<Object>(newMsg);
        messages.offer(new Entry(newMsg, subset, f, msg));
        return f;
    }

    @Override
    public AtmosphereResource<?,?> addAtmosphereResource(AtmosphereResource<?,?> r){
        ((HttpServletRequest) r.getRequest()).setAttribute(SESSION_ID_ATTRIBUTE, ((HttpServletRequest) r.getRequest()).getSession().getId());
        super.addAtmosphereResource(r);
        return r;
    }
}