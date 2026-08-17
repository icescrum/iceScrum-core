package org.icescrum.core.security.rest

import org.springframework.security.authentication.AccountExpiredException
import org.springframework.security.authentication.CredentialsExpiredException
import org.springframework.security.authentication.DisabledException
import org.springframework.security.authentication.LockedException
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User as SpringUser
import org.springframework.security.core.userdetails.UserDetails
import spock.lang.Specification

class TokenAuthenticationProviderSpec extends Specification {

    def "valid token authentication does not retain the password hash"() {
        given:
        def userDetails = userDetails()
        def provider = providerFor(userDetails)

        when:
        def authentication = provider.authenticate(new RestAuthenticationToken('test-credential'))

        then:
        authentication.authenticated
        authentication.principal.is(userDetails)
        authentication.credentials == null
    }

    def "token authentication enforces account state"() {
        given:
        def provider = providerFor(userDetails(enabled, accountNonExpired, credentialsNonExpired, accountNonLocked))

        when:
        provider.authenticate(new RestAuthenticationToken('test-credential'))

        then:
        thrown(expectedException)

        where:
        enabled | accountNonExpired | credentialsNonExpired | accountNonLocked || expectedException
        true    | true              | true                  | false            || LockedException
        false   | true              | true                  | true             || DisabledException
        true    | false             | true                  | true             || AccountExpiredException
        true    | true              | false                 | true             || CredentialsExpiredException
    }

    private static TokenAuthenticationProvider providerFor(UserDetails userDetails) {
        return new TokenAuthenticationProvider(
                tokenStorageService: new StubTokenStorageService(userDetails: userDetails)
        )
    }

    private static UserDetails userDetails(boolean enabled = true,
                                           boolean accountNonExpired = true,
                                           boolean credentialsNonExpired = true,
                                           boolean accountNonLocked = true) {
        return new SpringUser(
                'user',
                'stored-password-hash',
                enabled,
                accountNonExpired,
                credentialsNonExpired,
                accountNonLocked,
                [new SimpleGrantedAuthority('ROLE_USER')]
        )
    }

    private static class StubTokenStorageService extends TokenStorageService {
        UserDetails userDetails

        @Override
        UserDetails loadUserByToken(String tokenValue) {
            return userDetails
        }
    }
}
