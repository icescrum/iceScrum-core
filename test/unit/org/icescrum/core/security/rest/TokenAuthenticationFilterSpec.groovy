package org.icescrum.core.security.rest

import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.context.SecurityContextHolder
import spock.lang.Specification

import javax.servlet.FilterChain

class TokenAuthenticationFilterSpec extends Specification {

    def cleanup() {
        SecurityContextHolder.clearContext()
    }

    def "successful token authentication populates context and continues"() {
        given:
        def authenticated = new RestAuthenticationToken('user', null, [], 'test-credential')
        AuthenticationManager manager = Mock()
        FilterChain chain = Mock()
        def filter = new TokenAuthenticationFilter(authenticationManager: manager)
        def request = requestWithToken()
        def response = new MockHttpServletResponse()

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * manager.authenticate(_ as RestAuthenticationToken) >> authenticated
        1 * chain.doFilter(request, response)
        SecurityContextHolder.context.authentication.is(authenticated)
    }

    def "unauthenticated provider result stops the filter chain"() {
        given:
        AuthenticationManager manager = Mock()
        FilterChain chain = Mock()
        def filter = new TokenAuthenticationFilter(authenticationManager: manager)
        def request = requestWithToken()
        def response = new MockHttpServletResponse()

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * manager.authenticate(_ as RestAuthenticationToken) >> new RestAuthenticationToken('test-credential')
        0 * chain.doFilter(_, _)
        response.status == 401
        SecurityContextHolder.context.authentication == null
    }

    def "authentication exception clears context and stops the filter chain"() {
        given:
        AuthenticationManager manager = Mock()
        FilterChain chain = Mock()
        def filter = new TokenAuthenticationFilter(authenticationManager: manager)
        def request = requestWithToken()
        def response = new MockHttpServletResponse()

        when:
        filter.doFilter(request, response, chain)

        then:
        1 * manager.authenticate(_ as RestAuthenticationToken) >> {
            throw new BadCredentialsException('Authentication rejected')
        }
        0 * chain.doFilter(_, _)
        response.status == 403
        SecurityContextHolder.context.authentication == null
    }

    private static MockHttpServletRequest requestWithToken() {
        def request = new MockHttpServletRequest()
        request.addHeader(TokenExtractor.TOKEN_HEADER, 'test-credential')
        return request
    }
}
