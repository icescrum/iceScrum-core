package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereConfig
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.DefaultBroadcaster
import org.icescrum.core.domain.Project

class IceScrumBroadcaster extends DefaultBroadcaster {

    String pkey
    String pname

    int maxUsers = 0
    Date maxUsersDate = new Date()

    int maxConnections = 0
    Date maxConnectionsDate = new Date()
    List<Map> users = []

    public IceScrumBroadcaster() {}

    public Broadcaster initialize(String name, AtmosphereConfig config) {
        initValues()
        return super.initialize(name, config)
    }

    private void initValues() {
        if (name.contains("project-")) {
            def props = Project.createCriteria().get {
                eq 'id', name.split('-')[1].toLong()
                projections {
                    property 'pkey'
                    property 'name'
                }
                cache true
            }
            pkey = props[0]
            pname = props[1]
        } else {
            pkey = ""
            pname = "Global"
        }
    }

    int getLiveUsers() {
        return users.size() ?: 0
    }

    int getLiveConnections() {
        return resources.size() ?: 0
    }

    boolean addUser(def user) {
        if (!users.find { it.username == user.username }) {
            users.add(user)
            if (liveUsers > maxUsers) {
                maxUsers = liveUsers
                maxUsersDate = new Date()
            }
            if (liveConnections > maxConnections) {
                maxConnections = liveConnections
                maxConnectionsDate = new Date()
            }
            return true
        } else {
            return false
        }
    }

    boolean removeUser(def _user) {
        def user = users.find{ it.uuid == _user.uuid }
        if (user) {
            return users.remove(user)
        } else {
            return false
        }
    }
}
