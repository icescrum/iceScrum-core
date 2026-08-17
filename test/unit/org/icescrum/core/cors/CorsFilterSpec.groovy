package org.icescrum.core.cors

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import spock.lang.Specification

import javax.servlet.FilterChain
import javax.servlet.FilterConfig

class CorsFilterSpec extends Specification {

    def "missing origin allowlist denies credentialed CORS by default"() {
        given:
        def filter = initializedFilter([:])
        def request = new MockHttpServletRequest(method: 'GET')
        request.addHeader('Origin', 'https://client.example')
        def response = new MockHttpServletResponse()
        int chainCalls = 0
        FilterChain chain = { req, resp -> chainCalls++ } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        response.getHeader('Access-Control-Allow-Origin') == null
        response.getHeader('Access-Control-Allow-Credentials') == null
        chainCalls == 1
    }

    def "matching preflight origin receives the restricted method list"() {
        given:
        def filter = initializedFilter(['allow.origin.regex': 'https://client[.]example'])
        def request = new MockHttpServletRequest(method: 'OPTIONS')
        request.addHeader('Origin', 'https://client.example')
        def response = new MockHttpServletResponse()
        int chainCalls = 0
        FilterChain chain = { req, resp -> chainCalls++ } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        response.status == 200
        response.getHeader('Access-Control-Allow-Origin') == 'https://client.example'
        response.getHeader('Access-Control-Allow-Credentials') == 'true'
        response.getHeader('Vary') == 'Origin'
        response.getHeader('Access-Control-Allow-Methods') == 'GET, HEAD, POST, PUT, DELETE, OPTIONS'
        chainCalls == 0
    }

    def "non-matching origin receives no CORS headers"() {
        given:
        def filter = initializedFilter(['allow.origin.regex': 'https://client[.]example'])
        def request = new MockHttpServletRequest(method: 'GET')
        request.addHeader('Origin', 'https://other.example')
        def response = new MockHttpServletResponse()
        int chainCalls = 0
        FilterChain chain = { req, resp -> chainCalls++ } as FilterChain

        when:
        filter.doFilter(request, response, chain)

        then:
        response.getHeader('Access-Control-Allow-Origin') == null
        chainCalls == 1
    }

    private static CorsFilter initializedFilter(Map<String, String> initParameters) {
        FilterConfig config = [
                getInitParameter: { String name -> initParameters[name] }
        ] as FilterConfig
        def filter = new CorsFilter()
        filter.init(config)
        return filter
    }
}
