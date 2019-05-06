package org.icescrum.core.domain.security

import grails.util.Holders

class Client implements Serializable {

    static final long serialVersionUID = 183639045272971606L

    private static final String NO_CLIENT_SECRET = ''

    String clientId = UUID.randomUUID().toString()
    String clientSecret

    Integer accessTokenValiditySeconds
    Integer refreshTokenValiditySeconds

    Map<String, Object> additionalInformation

    static hasMany = [
            authorities         : String,
            authorizedGrantTypes: String,
            resourceIds         : String,
            scopes              : String,
            autoApproveScopes   : String,
            redirectUris        : String
    ]

    static constraints = {
        clientId blank: false, unique: true
        clientSecret nullable: true

        accessTokenValiditySeconds nullable: true
        refreshTokenValiditySeconds nullable: true

        authorities nullable: true
        authorizedGrantTypes nullable: true

        resourceIds nullable: true

        scopes nullable: true
        autoApproveScopes nullable: true

        redirectUris nullable: true
        additionalInformation nullable: true
    }

    def beforeInsert() {
        encodeClientSecret()
    }

    def beforeUpdate() {
        if (isDirty('clientSecret')) {
            encodeClientSecret()
        }
    }

    protected void encodeClientSecret() {
        clientSecret = clientSecret ?: NO_CLIENT_SECRET
        def springSecurityService = Holders.grailsApplication.mainContext.springSecurityService
        clientSecret = springSecurityService?.passwordEncoder ? springSecurityService.encodePassword(clientSecret) : clientSecret
    }
}
