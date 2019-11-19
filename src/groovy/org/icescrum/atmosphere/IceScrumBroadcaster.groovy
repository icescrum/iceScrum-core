package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereConfig
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.DefaultBroadcaster
import org.icescrum.core.domain.Project

class IceScrumBroadcaster extends DefaultBroadcaster {

    String pkey
    String pname

    int maxUsers = 0
    Date maxUsersDate = 0

    int maxConnections = new Date()
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
        return users.size()?:0
    }

    int getLiveConnections() {
        return resources.size() ?: 0
    }

    boolean addUser(def user) {
        if (user.username == 'anonymous' || !users.find { it.username == user.username }) {
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

    boolean removeUser(def user) {
        def userToRemove = (user.id ? (users.find { it.id == user.id } ?: null) : (users.find { it.ip ? (it.ip == user.ip && it.username == 'anonymous') : (it.username == 'anonymous') } ?: null))
        if (userToRemove) {
            return users.remove(userToRemove)
        } else {
            return false
        }
    }
}
