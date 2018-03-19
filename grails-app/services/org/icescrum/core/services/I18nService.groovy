package org.icescrum.core.services

import org.codehaus.groovy.grails.web.util.WebUtils
import org.springframework.web.servlet.i18n.SessionLocaleResolver
import org.springframework.context.MessageSource
import org.springframework.web.servlet.support.RequestContextUtils

class I18nService {

    boolean transactional = false

    SessionLocaleResolver localeResolver
    MessageSource messageSource

    def msg(String msgKey, String defaultMessage = null, List objs = null) {

        def _request = null
        try {
            _request = WebUtils.retrieveGrailsWebRequest()
        } catch (Exception e) {}

        def locale = _request ? RequestContextUtils.getLocale(_request) : localeResolver.defaultLocale
        def msg = messageSource.getMessage(msgKey, objs?.toArray(), defaultMessage, locale)

        if (msg == null || msg == defaultMessage) {
            msg = defaultMessage
        }

        return msg
    }

    def message(Map args) {
        return msg(args.code, args.default, args.attrs)
    }
}