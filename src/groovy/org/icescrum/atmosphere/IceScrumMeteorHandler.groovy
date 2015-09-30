package org.icescrum.atmosphere

import grails.util.Holders
import javax.servlet.http.HttpServlet
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

import org.atmosphere.cpr.Meteor

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
        meteor.suspend(-1)
	}
}