package org.icescrum.core.domain.security

class RefreshToken {

    String value
    Date expiration
    byte[] authentication

    static constraints = {
        value nullable: false, blank: false, unique: true
        expiration nullable: true
        authentication nullable: false, minSize: 1, maxSize: 1024 * 12
    }

    static mapping = {
        version false
        table 'is_oauth_r_token'
        value column: '`value`' // VALUE is a reserved word in H2 2.x; backticks make Hibernate quote it in the dialect-specific way
    }
}
