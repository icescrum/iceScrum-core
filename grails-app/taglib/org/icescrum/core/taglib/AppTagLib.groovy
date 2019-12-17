package org.icescrum.core.taglib

class AppTagLib {

    static namespace = 'is'

    def appService

    // Use only with Apps that need reload
    def ifAppIsEnabledForProject = { attrs, body ->
        assert attrs.app
        if (attrs.project && appService.isEnabledAppForProject(attrs.project, attrs.app)) {
            out << body()
        }
    }

    def ifAppIsAvailableForProject = { attrs, body ->
        assert attrs.app
        if (appService.isAvailableAppForProject(attrs.app)) {
            out << body()
        }
    }
}
