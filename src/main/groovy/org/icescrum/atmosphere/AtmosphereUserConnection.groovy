package org.icescrum.atmosphere

import org.atmosphere.cpr.AtmosphereResource

class AtmosphereUserConnection {

    String uuid
    String window
    String ipAddress
    String transport

    AtmosphereUserConnection(AtmosphereResource resource) {
        uuid = resource.uuid()
        transport = resource.transport().toString()
        ipAddress = getIpAdress(resource)
        window = resource.request?.getParameterValues("window") ? resource.request.getParameterValues("window")[0] : null
    }

    private static String getIpAdress(AtmosphereResource resource) {
        def request = resource.request
        String ip
        if (request?.getHeader("X-Forwarded-For") != null) {
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
