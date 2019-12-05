package org.icescrum.core.domain.security

class AccessToken implements Serializable {

    static final long serialVersionUID = 813639045272971606L

    String authenticationKey
    byte[] authentication

    String username
    String clientId

    String value
    String tokenType

    Date expiration
    Map<String, Object> additionalInformation

    static hasOne = [refreshToken: String]
    static hasMany = [scope: String]

    static constraints = {
        username nullable: true
        clientId nullable: false, blank: false
        value nullable: false, blank: false, unique: true
        tokenType nullable: false, blank: false
        expiration nullable: false
        scope nullable: false
        refreshToken nullable: true
        authenticationKey nullable: false, blank: false, unique: true
        authentication nullable: false, minSize: 1, maxSize: 1024 * 4
        additionalInformation nullable: true
    }

    static mapping = {
        version false
        scope lazy: false
        table 'is_oauth_a_token'
        if (System.properties['icescrum.oracle']) {
            additionalInformation joinTable: [name: 'is_oauth_a_token_information']
        }
    }
}
