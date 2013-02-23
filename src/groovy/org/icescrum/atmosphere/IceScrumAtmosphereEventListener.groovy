package org.icescrum.atmosphere

import grails.converters.JSON
import org.apache.commons.logging.LogFactory
import org.atmosphere.cpr.AtmosphereResourceEvent
import org.atmosphere.cpr.AtmosphereResourceEventListener
import org.atmosphere.cpr.AtmosphereResourceFactory
import org.atmosphere.cpr.BroadcasterFactory
import org.icescrum.core.domain.Product
import org.springframework.security.core.context.SecurityContext
import org.springframework.security.web.context.HttpSessionSecurityContextRepository

/**
 * Created with IntelliJ IDEA.
 * User: vbarrier
 * Date: 22/02/13
 * Time: 16:55
 * To change this template use File | Settings | File Templates.
 */
class IceScrumAtmosphereEventListener implements AtmosphereResourceEventListener {

    private static final USER_CONTEXT = 'user_context'
    private static final log = LogFactory.getLog(this)

    @Override
    void onSuspend(AtmosphereResourceEvent event) {
        def request = event.resource.request
        def productID = request.getParameterValues("product") ? request.getParameterValues("product")[0] : null

        def user = getUserFromAtmosphereResource(request, true) ?: [fullName: 'anonymous', id: null, username: 'anonymous']
        request.setAttribute(USER_CONTEXT, user)

        def channel = null

        if (productID && productID.isLong()) {
            channel = Product.load(productID.toLong()) ? "product-${productID}" : null
        }

        channel = channel?.toString()
        if (channel) {
            def broadcaster = BroadcasterFactory.default.lookup(channel) ?: BroadcasterFactory.default.get(channel)
            broadcaster.addAtmosphereResource(event.resource)
            if (log.isDebugEnabled()) {
                log.debug("add user ${user.username} to broadcaster: ${channel}")
            }
            def users = broadcaster.atmosphereResources.collect{
                it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)
            }
            broadcaster.broadcast(([broadcaster:[users:users]] as JSON).toString())
        }
    }

    @Override
    void onResume(AtmosphereResourceEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    void onDisconnect(AtmosphereResourceEvent event) {
        def user = event.resource.request.getAttribute(USER_CONTEXT)?:null
        if (log.isDebugEnabled()) {
            log.debug("user ${user.username} disconnected")
        }
        BroadcasterFactory.default.lookupAll().each {
            if (it.atmosphereResources.contains(event.resource)){
                it.removeAtmosphereResource(event.resource)
                if (it.getID().contains('product-')) {
                    def users = it.atmosphereResources?.collect{ it.request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT) }
                    it.broadcast(([broadcaster:[users:users]] as JSON).toString())
                }
            }
        }
        AtmosphereResourceFactory.getDefault().remove(event.resource.uuid());
    }

    @Override
    void onBroadcast(AtmosphereResourceEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    @Override
    void onThrowable(AtmosphereResourceEvent event) {
        //To change body of implemented methods use File | Settings | File Templates.
    }

    private static def getUserFromAtmosphereResource(def request, def createSession = false) {
        def httpSession = request.getSession(createSession)
        def user = null
        if (httpSession != null) {
            def context = (SecurityContext) httpSession.getAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY);
            if (context?.authentication?.isAuthenticated()) {
                user = [fullName:context.authentication.principal.fullName, id:context.authentication.principal.id, username:context.authentication.principal.username]
            }
        }
        user
    }
}
