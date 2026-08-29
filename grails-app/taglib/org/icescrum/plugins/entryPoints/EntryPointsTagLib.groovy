/*
* Copyright (c) 2011 Vincent Barrier.
*
*
* entry-points is free software: you can redistribute it and/or modify
* it under the terms of the GNU Lesser General Public License as published by
* the Free Software Foundation, either version 3 of the License.
*
* entry-points is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
* GNU General Public License for more details.
*
* You should have received a copy of the GNU Lesser General Public License
* along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
*
*/
package org.icescrum.plugins.entryPoints

import grails.util.Environment

class EntryPointsTagLib {

    static namespace = 'entry'

    def entryPointsService
    def webInvocationPrivilegeEvaluator

    def hook = { attrs ->
        assert attrs.id
        if (!attrs.model) {
            attrs.model = [:]
        }
        entryPointsService.getEntries(attrs.id)?.each { entry ->
            def controllerClass = grailsApplication.controllerClasses.find { it.name.toLowerCase() == entry.controller }
            grailsApplication.mainContext.getBean(controllerClass.fullName)?."${entry.action}"(params, attrs.model)
        }
    }

    def point = { attrs ->
        assert attrs.id
        if (!attrs.model) {
            attrs.model = [:]
        }
        //Hashmap to prevent stackoverflow error WTF grails...
        attrs.model.requestParams = params as HashMap
        if (Environment.current != Environment.PRODUCTION) {
            if (grailsApplication.config.getProperty('grails.entryPoints.debug', Boolean, false) || params._showEntryPoints) {
                out << """<span class='entry-point' title='[model/params: ${attrs.model*.key?.join(',')}]'>
                            entry-point id: ${attrs.id}
                        </span>"""
            }
        }
        def entries = entryPointsService.getEntries(attrs.id)
        entries?.eachWithIndex { entry, i ->
            def content
            if (entry.template) {
                content = g.render(template: entry.template, model: attrs.model, plugin: entry.plugin)
            } else {
                def url = createLink(controller: entry?.controller, action: entry?.action).toString() - request.contextPath
                def access = true
                if (webInvocationPrivilegeEvaluator != null) {
                    access = webInvocationPrivilegeEvaluator.isAllowed(grails.plugin.springsecurity.web.SecurityRequestHolder.request.contextPath, url, 'GET', org.springframework.security.core.context.SecurityContextHolder.context?.authentication)
                }
                if (access) {
                    content = g.include(action: entry?.action, controller: entry?.controller, params: attrs.model)
                }
            }
            if (content.trim()) {
                out << content
                if (attrs.join && i < entries.size() - 1) {
                    out << attrs.join
                }
            }
        }
    }
}
