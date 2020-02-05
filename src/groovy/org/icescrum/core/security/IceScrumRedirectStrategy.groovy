package org.icescrum.core.security

import org.icescrum.core.support.ApplicationSupport
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.security.web.PortResolver
import org.springframework.security.web.RedirectStrategy
import org.springframework.security.web.util.UrlUtils

import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Based on GrailsRedirectStrategy
 */
class IceScrumRedirectStrategy implements RedirectStrategy {

    protected final Logger log = LoggerFactory.getLogger(getClass())

    protected PortResolver portResolver
    protected boolean useHeaderCheckChannelSecurity

    public void sendRedirect(HttpServletRequest request, HttpServletResponse response, String url) throws IOException {
        String redirectUrl = calculateRedirectUrl(request, url)
        redirectUrl = response.encodeRedirectURL(redirectUrl)
        redirectUrl = redirectUrl.startsWith(ApplicationSupport.serverURL()) ? redirectUrl : ApplicationSupport.serverURL()
        response.sendRedirect(redirectUrl)
    }

    protected String calculateRedirectUrl(HttpServletRequest request, String url) {
        if (UrlUtils.isAbsoluteUrl(url)) {
            return url
        }

        url = request.getContextPath() + url

        if (!useHeaderCheckChannelSecurity) {
            return url
        }

        return UrlUtils.buildFullRequestUrl(request.getScheme(), request.getServerName(),
                portResolver.getServerPort(request), url, null)
    }

    /**
     * Dependency injection for useHeaderCheckChannelSecurity.
     * @param use
     */
    public void setUseHeaderCheckChannelSecurity(boolean use) {
        useHeaderCheckChannelSecurity = use
    }

    /**
     * Dependency injection for the port resolver.
     * @param portResolver the port resolver
     */
    public void setPortResolver(PortResolver portResolver) {
        this.portResolver = portResolver
    }
}
