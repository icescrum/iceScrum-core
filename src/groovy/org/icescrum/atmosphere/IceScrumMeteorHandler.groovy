package org.icescrum.atmosphere

import grails.converters.JSON
import grails.util.Holders
import org.atmosphere.cpr.Meteor
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.services.PushService
import org.springframework.security.core.Authentication
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import static org.atmosphere.cpr.AtmosphereResource.TRANSPORT.LONG_POLLING

class IceScrumMeteorHandler extends HttpServlet {

    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        def conf = Holders.grailsApplication.config.icescrum.push
        if (!conf.enable) {
            return
        }
        response.setContentType("text/plain;charset=UTF-8")
        Meteor meteor = Meteor.build(request)
        meteor.addListener(new IceScrumAtmosphereEventListener())
        meteor.resumeOnBroadcast(meteor.transport() == LONG_POLLING).suspend(-1)
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Authentication authentication = ((SecurityContext) request.session?.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))?.authentication;
        if (authentication && authentication.isAuthenticated() && authentication.authorities?.find { it.authority == Authority.ROLE_ADMIN }) { // Compares GrantedAuthority to Authority, not very clean but the easiest way to check if admin
            def data = JSON.parse(request.reader)
            def message = data.message
            def to = data.to
            if (message && message.namespace && message.eventType && message.object) {
                PushService pushService = Holders.applicationContext.getBean("pushService")
                if (to && to.users) {
                    pushService.broadcastToUsers(message.namespace, message.eventType, message.object, to.users)
                } else if (to && to.project) {
                    pushService.broadcastToProjectChannel(message.namespace, message.eventType, message.object, to.project)
                } else {
                    pushService.broadcastToChannel(message.namespace, message.eventType, message.object)
                }
            }
        }

    }
}
