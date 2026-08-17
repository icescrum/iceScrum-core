package org.icescrum.core.services

import org.icescrum.core.domain.Portfolio
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.preferences.ProjectPreferences
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import spock.lang.Specification

class PushSubscriptionSecurityServiceSpec extends Specification {

    def "global channel requires a non-anonymous authentication"() {
        given:
        def service = new StubPushSubscriptionSecurityService()
        def authenticated = new UsernamePasswordAuthenticationToken('user', null, [])
        def anonymous = new AnonymousAuthenticationToken(
                'key',
                'anonymousUser',
                [new SimpleGrantedAuthority('ROLE_ANONYMOUS')]
        )

        expect:
        service.canSubscribe('*', authenticated)
        !service.canSubscribe('*', anonymous)
        !service.canSubscribe('*', null)
    }

    def "public project channel remains readable without authentication"() {
        given:
        def project = new Project(preferences: new ProjectPreferences(hidden: false))
        def service = new StubPushSubscriptionSecurityService(project: project)

        expect:
        service.canSubscribe('project-1', null)
    }

    def "hidden project channel requires workspace read access"() {
        given:
        def project = new Project(preferences: new ProjectPreferences(hidden: true))
        def securityService = Mock(SecurityService)
        def service = new StubPushSubscriptionSecurityService(
                project: project,
                securityService: securityService
        )
        def authentication = new UsernamePasswordAuthenticationToken('user', null, [])

        when:
        def allowed = service.canSubscribe('project-1', authentication)

        then:
        allowed
        1 * securityService.inProject(project, authentication) >> false
        1 * securityService.stakeHolder(project, authentication, true) >> true
    }

    def "portfolio channel requires portfolio read access"() {
        given:
        def portfolio = new Portfolio()
        def securityService = Mock(SecurityService)
        def service = new StubPushSubscriptionSecurityService(
                portfolio: portfolio,
                securityService: securityService
        )
        def authentication = new UsernamePasswordAuthenticationToken('user', null, [])

        when:
        def allowed = service.canSubscribe('portfolio-1', authentication)

        then:
        allowed
        1 * securityService.businessOwner(portfolio, authentication) >> false
        1 * securityService.portfolioStakeHolder(portfolio, authentication) >> true
    }

    def "unknown or unsupported channels are denied"() {
        given:
        def service = new StubPushSubscriptionSecurityService()

        expect:
        !service.canSubscribe(null, null)
        !service.canSubscribe('other-1', null)
        !service.canSubscribe('project-1', null)
        !service.canSubscribe('portfolio-1', null)
    }

    private static class StubPushSubscriptionSecurityService extends PushSubscriptionSecurityService {
        Project project
        Portfolio portfolio

        @Override
        protected Project getProject(Long workspaceId) {
            return project
        }

        @Override
        protected Portfolio getPortfolio(Long workspaceId) {
            return portfolio
        }
    }
}
