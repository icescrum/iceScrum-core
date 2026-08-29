/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Replacement for org.springframework.security.oauth2.provider.expression
 * .OAuth2ExpressionUtils (spring-security-oauth is dead and its Grails 2
 * provider plugin was dropped in the Grails 7 migration). iceScrum no longer
 * acts as an OAuth2 authorization server: the REST API keeps its own access
 * tokens (TokenAuthenticationFilter). With no OAuth2 authentications possible,
 * isOAuth is always false and no scopes are ever granted, which preserves the
 * semantics of the *Web security expressions (web endpoints allowed for
 * non-OAuth authentications).
 */
package org.icescrum.core.security;

import org.springframework.security.core.Authentication;

public final class OAuth2Support {

    private OAuth2Support() {
    }

    public static boolean isOAuth(Authentication authentication) {
        return false;
    }

    public static boolean hasAnyScope(Authentication authentication, String[] scopes) {
        return false;
    }
}
