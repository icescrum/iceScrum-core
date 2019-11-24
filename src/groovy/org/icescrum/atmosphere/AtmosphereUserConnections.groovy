package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereResource

class AtmosphereUserConnection {

    String window
    String ipAddress
    AtmosphereResource resource

    String getUuid(){
        return resource.uuid()
    }

    String getTransport(){
        return resource.transport().toString()
    }

    private String getIpAdress() {
        def request = resource.request
        String ip
        if (request.getHeader("X-Forwarded-For") != null) {
            String xForwardedFor = request.getHeader("X-Forwarded-For")
            if (xForwardedFor.indexOf(",") != -1) {
                ip = xForwardedFor.substring(xForwardedFor.lastIndexOf(",") + 2)
            } else {
                ip = xForwardedFor
            }
        } else {
            ip = request.getRemoteAddr()
        }
        return ip
    }
}
