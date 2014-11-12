package org.icescrum.atmosphere



import grails.converters.JSON
import grails.util.Holders
import org.icescrum.atmosphere.IceScrumAtmosphereEventListener

import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.atmosphere.cpr.AtmosphereResourceEventListenerAdapter
import org.atmosphere.cpr.Broadcaster
import org.atmosphere.cpr.DefaultBroadcaster
import org.atmosphere.cpr.Meteor

class IceScrumMeteorHandler extends HttpServlet {
	def atmosphereMeteor = Holders.applicationContext.getBean("atmosphereMeteor")

	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String mapping = "/stream/app"
		Meteor meteor = Meteor.build(request)
		Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory.lookup(DefaultBroadcaster.class, mapping, true)
		meteor.addListener(new IceScrumAtmosphereEventListener())
		meteor.setBroadcaster(broadcaster)
        meteor.suspend(-1)
	}

	@Override
	public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
		String mapping = "/stream/app"
		def jsonMap = JSON.parse(request.getReader().readLine().trim()) as Map
		Broadcaster broadcaster = atmosphereMeteor.broadcasterFactory.lookup(DefaultBroadcaster.class, mapping)
		broadcaster.broadcast(jsonMap)
	}
}
