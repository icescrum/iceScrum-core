package org.icescrum.core.cache

import grails.plugin.cache.web.filter.WebKeyGenerator
import grails.util.Holders
import org.codehaus.groovy.grails.web.util.WebUtils

import javax.servlet.http.HttpServletRequest

class IsControllerWebKeyGenerator implements WebKeyGenerator {

    @Override
    String generate(HttpServletRequest request) {
        def _request = WebUtils.retrieveGrailsWebRequest()
        def springContext =  Holders.grailsApplication.getMainContext()
        springContext.getBean('securityService').decodeKeys(_request.params)
        def params = _request.params.clone()
        String controllerName = params.remove('controller')
        String actionName = params.action instanceof Map ? params.remove('action')[request.getMethod()] : params.remove('action')
        def artefact = Holders.grailsApplication.getArtefactByLogicalPropertyName('Controller', controllerName)
        def controller = springContext.getBean(artefact.clazz.name)
        def key = controller."${actionName}CacheKey"() + params.inject([]) { result, entry -> result << entry.key + '_' + entry.value }.join('_')
        return key
    }
}