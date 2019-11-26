package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereConfig
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.DefaultBroadcaster
import org.icescrum.core.domain.Project
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class IceScrumBroadcaster extends DefaultBroadcaster {

    private static final Logger logger = LoggerFactory.getLogger(IceScrumBroadcaster.class);

    String pkey
    String pname

    int maxUsers = 0
    Date maxUsersDate = new Date()
    List<AtmosphereUser> users = []

    int maxConnections = 0
    Date maxConnectionsDate = new Date()

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

    boolean addUser(AtmosphereUser user) {
        def added = false
        AtmosphereUser existingUser = users.find { it.username == user.username } ?: null
        if (!user.connections.isEmpty() && user.connections.first()) {
            AtmosphereUserConnection connection = user.connections.first()
            def existingConnection = existingUser?.connections?.find { it.uuid == connection.uuid }
            if (existingUser) {
                if (logger.debugEnabled) {
                    logger.debug("[${name}][${user.username}] existing user")
                }
                if (existingConnection) {
                    existingConnection.resource = connection.resource
                } else {
                    existingUser.connections << connection
                    added = true
                }
            } else {
                users << user
                if (logger.debugEnabled) {
                    logger.debug("[${name}][${user.username}] adding user")
                }
                existingUser = user
                added = true
            }
            if (added) {
                if (logger.debugEnabled) {
                    logger.debug("[${name}][${existingUser.username}] adding uuid ${connection.uuid} with transport ${connection.transport}")
                    logger.debug("[${name}][${existingUser.username}] ${existingUser.connections.size()} connections opened")
                }
                if (liveUsers > maxUsers) {
                    maxUsers = liveUsers
                    maxUsersDate = new Date()
                }
                if (liveConnections > maxConnections) {
                    maxConnections = liveConnections
                    maxConnectionsDate = new Date()
                }
            } else {
                if (logger.debugEnabled) {
                    logger.debug("[${name}][${existingUser.username}] exiting uuid ${connection.uuid} with transport ${connection.transport}")
                    logger.debug("[${name}][${existingUser.username}] ${existingUser.connections.size()} connections opened")
                }
            }
            if (logger.debugEnabled) {
                logger.debug("[${name}] users connected: ${liveUsers} - connections: ${liveConnections}")
            }
        }
        existingUser?.cleanUpConnections()
        return added
    }

    boolean removeUser(AtmosphereUser user) {
        def removed = false
        AtmosphereUser existingUser = users.find { it.username == user.username } ?: null
        if (!user.connections.isEmpty() && user.connections.first() && existingUser) {
            AtmosphereUserConnection connection = user.connections.first()
            def existingConnection = existingUser.connections?.find { it.uuid == connection.uuid } ?: null
            if (existingConnection) {
                removed = existingUser.connections.remove(existingConnection)
                if (logger.debugEnabled) {
                    logger.debug("[${name}][${existingUser.username}] removing connection ${existingConnection.uuid} with transport ${existingConnection.transport}")
                    logger.debug("[${name}][${existingUser.username}] ${existingUser.connections.size()} connections opened")
                }
            }
        }
        if (existingUser) {
            existingUser.cleanUpConnections()
            if (!existingUser.connections) {
                if (logger.debugEnabled) {
                    logger.debug("[${name}][${existingUser.username}] removing user")
                }
                users.remove(existingUser)
                removed = true
            }
        }
        if (logger.debugEnabled) {
            logger.debug("[${name}] users connected: ${liveUsers} - connections: ${liveConnections}")
        }
        return removed
    }
}
