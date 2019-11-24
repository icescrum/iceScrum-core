package org.icescrum.atmosphere

class AtmosphereUser {
    long id
    String username
    List<AtmosphereUserConnection> connections = new ArrayList<AtmosphereUserConnection>()

    def cleanUpConnections() {
        connections = connections.findAll { it.resource.isSuspended() }
    }
}
