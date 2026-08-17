package org.icescrum.core.security.rest

import grails.test.mixin.TestMixin
import grails.test.mixin.support.GrailsUnitTestMixin
import org.springframework.mock.web.MockHttpServletRequest
import spock.lang.Specification

@TestMixin(GrailsUnitTestMixin)
class TokenExtractorSpec extends Specification {

    def setup() {
        mockConfig('''
            icescrum.security {
                allowTokenParameter = false
                authorizedTokenHeaders = []
            }
        ''')
    }

    def "standard token header is accepted"() {
        given:
        def request = new MockHttpServletRequest()
        request.addHeader(TokenExtractor.TOKEN_HEADER, 'test-credential')

        expect:
        TokenExtractor.getToken(request) == 'test-credential'
    }

    def "URL token parameter is disabled by default"() {
        given:
        def request = new MockHttpServletRequest()
        request.addParameter('icescrum-token', 'test-credential')

        expect:
        TokenExtractor.getToken(request) == null
    }

    def "URL token parameter requires explicit legacy opt-in"() {
        given:
        mockConfig('''
            icescrum.security {
                allowTokenParameter = true
                authorizedTokenHeaders = []
            }
        ''')
        def request = new MockHttpServletRequest()
        request.addParameter('icescrum-token', 'test-credential')

        expect:
        TokenExtractor.getToken(request) == 'test-credential'
    }

    def "configured application token header remains supported"() {
        given:
        mockConfig('''
            icescrum.security {
                allowTokenParameter = false
                authorizedTokenHeaders = ['x-application-token']
            }
        ''')
        def request = new MockHttpServletRequest()
        request.addHeader('x-application-token', 'test-credential')

        expect:
        TokenExtractor.getToken(request) == 'test-credential'
    }
}
