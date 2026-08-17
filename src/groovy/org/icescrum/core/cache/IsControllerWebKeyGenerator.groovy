package org.icescrum.core.cache

import grails.plugin.cache.web.filter.WebKeyGenerator
import grails.util.Holders
import org.codehaus.groovy.grails.web.util.WebUtils
import org.springframework.security.oauth2.provider.OAuth2Authentication

import javax.servlet.http.HttpServletRequest
import java.security.MessageDigest

class IsControllerWebKeyGenerator implements WebKeyGenerator {

    @Override
    String generate(HttpServletRequest request) {
        def _request = WebUtils.retrieveGrailsWebRequest()
        def springContext = Holders.grailsApplication.getMainContext()
        springContext.getBean('securityService').decodeKeys(_request.params)
        def params = _request.params.clone()
        String controllerName = params.remove('controller')
        String actionName = params.action instanceof Map ? params.remove('action')[request.getMethod()] : params.remove('action')
        def artefact = Holders.grailsApplication.getArtefactByLogicalPropertyName('Controller', controllerName)
        def controller = springContext.getBean(artefact.clazz.name)
        def authentication = springContext.getBean('springSecurityService').authentication
        def principal = authentication?.principal
        def identity = principal?.hasProperty('id') ? principal.id : authentication?.name
        String authorities = authentication?.authorities*.authority?.sort()?.join(',') ?: ''
        String securityContext = "${authentication?.class?.name ?: 'anonymous'}|${identity ?: 'anonymous'}|$authorities"
        if (authentication instanceof OAuth2Authentication) {
            def oauthRequest = authentication.getOAuth2Request()
            securityContext += "|${oauthRequest.clientId}|${oauthRequest.scope?.sort()?.join(',') ?: ''}"
        }
        String requestContext = canonicalize([
                controller: controllerName,
                action: actionName,
                method: request.method,
                custom: controller."${actionName}CacheKey"(),
                params: params
        ])
        return digest(securityContext) + '_' + digest(requestContext)
    }

    private static String canonicalize(value) {
        if (value == null) {
            return 'N'
        }
        if (value instanceof Map) {
            return 'M{' + value.entrySet().sort { left, right ->
                left.key.toString() <=> right.key.toString()
            }.collect { entry ->
                canonicalize(entry.key.toString()) + canonicalize(entry.value)
            }.join() + '}'
        }
        if (value instanceof Collection) {
            return 'L[' + value.collect { canonicalize(it) }.join() + ']'
        }
        if (value?.class?.isArray()) {
            return 'A[' + value.toList().collect { canonicalize(it) }.join() + ']'
        }
        String stringValue = value.toString()
        return "S${stringValue.length()}:$stringValue"
    }

    private static String digest(String value) {
        return MessageDigest.getInstance('SHA-256').digest(value.getBytes('UTF-8')).encodeHex().toString()
    }
}