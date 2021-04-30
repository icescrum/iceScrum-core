package org.icescrum.core.cors

import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import java.util.regex.Pattern

class CorsFilter implements Filter {

    private Pattern allowOriginRegex
    private String allowedHeaders

    void init(FilterConfig cfg) {
        def regexString = cfg.getInitParameter('allow.origin.regex')
        if (regexString) {
            allowOriginRegex = Pattern.compile(regexString, Pattern.CASE_INSENSITIVE)
        }
        allowedHeaders = cfg.getInitParameter('allowedHeaders')
    }

    void doFilter(ServletRequest request, ServletResponse response, FilterChain filterChain) {
        boolean stopProcessing = false;
        if (request instanceof HttpServletRequest && response instanceof HttpServletResponse) {
            HttpServletRequest req = (HttpServletRequest) request
            HttpServletResponse resp = (HttpServletResponse) response
            String origin = req.getHeader('Origin')
            if (origin && (allowOriginRegex == null || allowOriginRegex.matcher(origin).matches())) {
                resp.addHeader('Access-Control-Allow-Origin', origin) // required for Access-Control-Allow-Credentials = true
                resp.addHeader('Access-Control-Allow-Credentials', 'true')
                if (req.method == 'OPTIONS') {
                    resp.addHeader('Access-Control-Allow-Headers', 'origin, authorization, accept, content-type, x-requested-with' + (allowedHeaders ? (', ' + allowedHeaders) : '') )
                    resp.addHeader('Access-Control-Allow-Methods', 'GET, HEAD, POST, PUT, DELETE, TRACE, OPTIONS')
                    resp.addHeader('Access-Control-Max-Age', '3600')
                    response.setStatus(HttpServletResponse.SC_OK)
                    stopProcessing = true
                }
            }
        }
        if (!stopProcessing) {
            filterChain.doFilter(request, response)
        }
    }

    void destroy() {}
}
