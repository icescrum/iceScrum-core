package org.icescrum.atmosphere

import org.atmosphere.cpr.MeteorServlet
import org.atmosphere.handler.ReflectorServletProcessor
import org.grails.plugins.atmosphere_meteor.AtmosphereConfigurationHolder

import javax.servlet.ServletConfig
import javax.servlet.ServletException

class IceScrumMeteorServlet extends MeteorServlet {

    @Override
    public void init(ServletConfig sc) throws ServletException {
        super.init(sc)

        def servletConfig = AtmosphereConfigurationHolder.atmosphereMeteorConfig.servlets.get(sc.servletName)
        def mapping = servletConfig.mapping
        def handler = servletConfig.handler.newInstance()
        def handlerClass = handler.class.getName()

        ReflectorServletProcessor rsp = new ReflectorServletProcessor()
        rsp.setServletClassName(handlerClass)
        framework().addAtmosphereHandler(mapping, rsp)
        logger.info "Added AtmosphereHandler: $handlerClass mapped to $mapping"
    }
}
