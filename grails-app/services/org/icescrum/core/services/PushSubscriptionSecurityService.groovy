/*
 * Copyright (c) 2026 iceScrum.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */

package org.icescrum.core.services

import grails.transaction.Transactional
import org.icescrum.core.domain.Portfolio
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.WorkspaceType
import org.springframework.security.authentication.AnonymousAuthenticationToken
import org.springframework.security.core.Authentication

@Transactional(readOnly = true)
class PushSubscriptionSecurityService {

    SecurityService securityService

    boolean canSubscribe(String channelKey, Authentication authentication) {
        if (channelKey == '*') {
            return isAuthenticated(authentication)
        }

        def matcher = channelKey =~ /^(project|portfolio)-([0-9]+)$/
        if (!matcher.matches()) {
            return false
        }

        String workspaceType = matcher.group(1)
        Long workspaceId
        try {
            workspaceId = matcher.group(2).toLong()
        } catch (NumberFormatException ignored) {
            return false
        }

        if (workspaceType == WorkspaceType.PROJECT) {
            Project project = getProject(workspaceId)
            if (!project) {
                return false
            }
            // Public projects are intentionally readable without a login.
            if (project.preferences?.hidden == false) {
                return true
            }
            return isAuthenticated(authentication) &&
                    (securityService.inProject(project, authentication) ||
                            securityService.stakeHolder(project, authentication, true))
        }

        Portfolio portfolio = getPortfolio(workspaceId)
        return portfolio && isAuthenticated(authentication) &&
                (securityService.businessOwner(portfolio, authentication) ||
                        securityService.portfolioStakeHolder(portfolio, authentication))
    }

    protected Project getProject(Long workspaceId) {
        return Project.get(workspaceId)
    }

    protected Portfolio getPortfolio(Long workspaceId) {
        return Portfolio.get(workspaceId)
    }

    private static boolean isAuthenticated(Authentication authentication) {
        return authentication?.isAuthenticated() &&
                !(authentication instanceof AnonymousAuthenticationToken)
    }
}
