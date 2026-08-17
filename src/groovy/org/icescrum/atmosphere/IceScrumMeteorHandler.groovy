package org.icescrum.atmosphere

import grails.converters.JSON
import grails.util.Holders
import org.atmosphere.cpr.Meteor
import org.icescrum.core.domain.security.Authority
import org.icescrum.core.services.PushService
import org.icescrum.core.services.PushSubscriptionSecurityService
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
        Authentication authentication = getSessionAuthentication(request)
        String channelKey = request.pathInfo?.tokenize('/')?.last()
        PushSubscriptionSecurityService subscriptionSecurityService = Holders.applicationContext.getBean('pushSubscriptionSecurityService')
        if (!subscriptionSecurityService.canSubscribe(channelKey, authentication)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN)
            return
        }
        String channel = IceScrumAtmosphereEventListener.GLOBAL_CONTEXT_NO_STAR + channelKey
        request.setAttribute(IceScrumAtmosphereEventListener.AUTHORIZED_CHANNEL, channel)
        response.setContentType("text/plain;charset=UTF-8")
        Meteor meteor = Meteor.build(request)
        def broadcaster = Holders.applicationContext.getBean('atmosphereMeteor').broadcasterFactory.lookup(channel, true)
        meteor.setBroadcaster(broadcaster)
        meteor.addListener(new IceScrumAtmosphereEventListener())
        meteor.resumeOnBroadcast(meteor.transport() == LONG_POLLING).suspend(-1)
    }

    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Authentication authentication = getSessionAuthentication(request)
        if (authentication && authentication.isAuthenticated() && authentication.authorities?.find { it.authority == Authority.ROLE_ADMIN }) { // Compares GrantedAuthority to Authority, not very clean but the easiest way to check if admin
            def data = JSON.parse(request.reader)
            def message = data.message
            def to = data.to
            if (message && message.namespace && message.eventType && message.object) {
                PushService pushService = Holders.applicationContext.getBean("pushService")
                if (to && to.users) {
                    pushService.broadcastToUsers(message.namespace, message.eventType, message.object, to.users)
                } else if (to && to.project) {
                    pushService.broadcastToProjectRelatedChannels(message.namespace, message.eventType, message.object, to.project)
                } else {
                    pushService.broadcastToChannel(message.namespace, message.eventType, message.object)
                }
            }
        }

    }

    private static Authentication getSessionAuthentication(HttpServletRequest request) {
        return ((SecurityContext) request.getSession(false)?.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY))?.authentication
    }
}
