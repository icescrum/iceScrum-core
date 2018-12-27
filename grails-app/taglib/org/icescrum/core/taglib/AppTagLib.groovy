package org.icescrum.core.taglib

class AppTagLib {

    static namespace = 'is'

    def appService

    def ifAppIsEnabledForProject = { attrs, body ->
        assert attrs.app
        if (attrs.project && appService.isEnabledAppForProject(attrs.project, attrs.app)) {
            out << body()
        }
    }
}
