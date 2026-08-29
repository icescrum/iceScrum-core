package org.icescrum.atmosphere

import org.atmosphere.cpr.MeteorServlet
import org.atmosphere.handler.ReflectorServletProcessor

import jakarta.servlet.ServletConfig
import jakarta.servlet.ServletException

/**
 * Grails 7 migration: the atmosphere-meteor plugin (and its
 * AtmosphereConfigurationHolder) is gone. The servlet is now registered by the
 * application as a Spring Boot ServletRegistrationBean; mapping and handler
 * are fixed instead of being looked up in the plugin configuration.
 */
class IceScrumMeteorServlet extends MeteorServlet {

    static final String MAPPING = '/stream/app/*'

    @Override
    void init(ServletConfig sc) throws ServletException {
        super.init(sc)
        ReflectorServletProcessor rsp = new ReflectorServletProcessor()
        rsp.setServletClassName(IceScrumMeteorHandler.name)
        framework().addAtmosphereHandler(MAPPING, rsp)
        logger.info "Added AtmosphereHandler: ${IceScrumMeteorHandler.name} mapped to ${MAPPING}"
    }
}
