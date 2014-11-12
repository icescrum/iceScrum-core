package org.icescrum.atmosphere



import grails.converters.JSON
import grails.util.Holders
import org.atmosphere.cpr.AtmosphereResource
import org.atmosphere.cpr.AtmosphereResourceFactory
import org.atmosphere.cpr.BroadcasterFactory
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.DefaultBroadcaster
import org.atmosphere.cpr.Meteor

class IceScrumMeteorHandler extends HttpServlet {

    private static final DEFAULT_CHANNEL = "/stream/app"
    def atmosphereMeteor = Holders.applicationContext.getBean("atmosphereMeteor")

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        def conf = Holders.grailsApplication.config.icescrum.push
        if (!conf.enable) {
            return;
        }
        response.setContentType("text/plain;charset=UTF-8")

		Meteor meteor = Meteor.build(request)
		Broadcaster broadcaster = broadcasterLookup(request.pathInfo)
		meteor.addListener(new IceScrumAtmosphereEventListener())
		meteor.setBroadcaster(broadcaster)
        meteor.suspend(-1)
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        Meteor meteor = Meteor.build(request)
        if (meteor.atmosphereResource){
            def user = request.getAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT)
            if (request.getParameterValues("window") && user) {
                user.window = request.getParameterValues("window") ? request.getParameterValues("window")[0] : null
                request.setAttribute(IceScrumAtmosphereEventListener.USER_CONTEXT, user)
            }
            else if (request.getParameterValues("command") && request.getParameterValues("data") && request.getParameterValues("to")){
                Broadcaster broadcaster = broadcasterLookup(request.pathInfo)
                AtmosphereResource resourceTo = atmosphereMeteor.framework.atmosphereFactory().find(request.getParameterValues("to")[0])
                if (resourceTo){
                    broadcaster.broadcast(([command:request.getParameterValues("command")[0],data:request.getParameterValues("data")[0], from:meteor.atmosphereResource.uuid()] as JSON).toString(),resourceTo)
                }
            }
        }

    }

    public Broadcaster broadcasterLookup(String pathInfo) {
        String[] decodedPath = pathInfo ? pathInfo.split("/") : [];
        Broadcaster b;
        if (decodedPath.length > 0) {
            b = atmosphereMeteor.broadcasterFactory.lookup(DEFAULT_CHANNEL +"/"+ decodedPath[decodedPath.length - 1], true);
        } else {
            b = atmosphereMeteor.broadcasterFactory.lookup(DEFAULT_CHANNEL, true);
        }
        return b;
    }
}
