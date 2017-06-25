/*
 * Copyright (c) 2017 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 */
package org.icescrum.core.security.rest

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.GenericFilterBean

import javax.servlet.FilterChain
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class TokenAuthenticationFilter extends GenericFilterBean {

	AuthenticationManager authenticationManager
	@Override
	 void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
		if(this.logger.isDebugEnabled()) {
			this.logger.debug("Begin **** TokenAuthenticationFilter")
		}
		HttpServletRequest httpRequest = (HttpServletRequest) request
		String token = TokenExtractor.getToken(httpRequest)
		if (token) {
			if(this.logger.isDebugEnabled()) {
				this.logger.debug("Token Authentication Authorization header found: '" + token + "'")
			}
			try {
				RestAuthenticationToken authToken = new RestAuthenticationToken(token)
				Authentication authResult = authenticationManager.authenticate(authToken)
				if (authResult.isAuthenticated()) {
					if(this.logger.isDebugEnabled()) {
						this.logger.debug("Authentication success: " + authResult)
					}
					SecurityContextHolder.getContext().setAuthentication(authResult)
				} else {
					System.out.println("Invalid " + TokenExtractor.TOKEN_HEADER + ' ' + token + " in request sendError ${HttpServletResponse.SC_UNAUTHORIZED}")
					((HttpServletResponse) response).setStatus(HttpServletResponse.SC_UNAUTHORIZED)
				}
				if(this.logger.isDebugEnabled()) {
					this.logger.debug("End TokenAuthenticationFilter ****")
				}
				chain.doFilter(request, response)
			}
			catch (AuthenticationException e) {
				SecurityContextHolder.clearContext();
				if(this.logger.isDebugEnabled()){
					this.logger.debug("Authentication request for failed: " + e)
					this.logger.debug "No authorized "+TokenExtractor.TOKEN_HEADER + ' ' + token +" in request sendError ${HttpServletResponse.SC_FORBIDDEN}"
				}
				((HttpServletResponse) response).setStatus(HttpServletResponse.SC_FORBIDDEN)
			}
		} else {
			((HttpServletResponse) response).sendError(HttpServletResponse.SC_UNAUTHORIZED)
			if(this.logger.isDebugEnabled()){
				this.logger.debug "No "+TokenExtractor.TOKEN_HEADER+" in request sendError ${HttpServletResponse.SC_UNAUTHORIZED}"
				this.logger.debug"End TokenAuthenticationFilter ****"
			}
		}
	}
}