/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Grails 7 migration: replaces the atmosphere-meteor plugin's 'atmosphereMeteor'
 * bean. Services and controllers inject it for broadcasterFactory access; with
 * Atmosphere 3 the framework publishes its factory through the Universe holder.
 */
package org.icescrum.atmosphere

import org.atmosphere.cpr.BroadcasterFactory
import org.atmosphere.cpr.Universe

class AtmosphereMeteorCompat {

    BroadcasterFactory getBroadcasterFactory() {
        return Universe.broadcasterFactory()
    }
}
