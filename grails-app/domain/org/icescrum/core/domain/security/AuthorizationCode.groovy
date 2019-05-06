package org.icescrum.core.domain.security

class AuthorizationCode implements Serializable {

    static final long serialVersionUID = 813639405272971606L

    byte[] authentication
    String code

    static constraints = {
        code nullable: false, blank: false, unique: true
        authentication nullable: false, minSize: 1, maxSize: 1024 * 4
    }

    static mapping = {
        version false
    }
}
