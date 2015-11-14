/*
 * Copyright (c) 2014 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.taglib

import org.codehaus.groovy.grails.web.mapping.ForwardUrlMappingInfo
import org.codehaus.groovy.grails.web.mapping.UrlMappingUtils
import org.codehaus.groovy.grails.web.metaclass.ControllerDynamicMethods
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes

class WindowTagLib {
    static namespace = 'is'

    def springSecurityService
    def grailsApplication
    def securityService

    def window = { attrs, body ->
        def id = attrs.window ?: controllerName
        attrs.type = attrs.type ?: 'window'
        def includeParams = [:]
        params.each {
            if (!(it.key in ["controller", "action"])) {
                includeParams << it
            }
        }
        attrs.each {
            if (!(it.key in ["controller", "action"])) {
                includeParams << it
            }
        }
        // Check for content window
        def content
        if (attrs.init) {
            def result = includeContent([controller: id, action: attrs.init, params: includeParams])
            if (result.contentType == 'application/json;charset=utf-8') {
                response.setStatus(400)
                response.setContentType(result.contentType)
                out << result.content
                return
            } else {
                content = result.content
            }
        } else {
            content = body()
        }
        // Check for shortcuts
        if (attrs.shortcuts) {
            attrs.help = attrs.help ?: ""
            attrs.help += "<span class='help-shortcut-title'>${message(code: 'is.ui.shortcut.title')}</span>"
            attrs.shortcuts.each {
                attrs.help += "<p class='keyboard-mappings'>"
                attrs.help += "<span class='code box-simple ui-corner-all'>${message(code: it.code)}</span>"
                attrs.help += "${message(code: it.text)}"
                attrs.help += "</p>"
            }
        }
        def params = [
                type: attrs.type,
                id: id,
                flex: attrs.flex,
                content: content,
                details:attrs.details,
                icon: attrs.icon ?: null,
                spaceName: attrs.spaceName,
                title: attrs.title ?: null,
                contentClass: attrs.contentClass,
                windowActions: attrs.windowActions ?: [
                        help: attrs.help ?: null,
                        fullScreen: attrs.fullScreen,
                        printable:attrs.printable
                ],
        ]
        if (content && !webRequest?.params?.returnError) {
            out << g.render(template: '/components/' + attrs.type, plugin: 'icescrum-core', model: params)
        }
    }

    def modal = { attrs, body ->
        out << """<div class="modal-content ${attrs['class']}">"""
        def name = attrs.name ? "name='$attrs.name'" : ''
        def validation = attrs.validate ? 'show-validation' : ''
        if (attrs.form) {
            out << "<form role='form' $validation $name ng-submit='${attrs.form}' ${attrs.autoFillFix ? 'form-autofill-fix' : ''} novalidate>"
        }
        out << """  <div class="modal-header">
                        <button type="button" class="close" ng-click="\$dismiss()" aria-hidden="true">&times;</button>
                        <h4 class="modal-title" id="modal${attrs.name}">${attrs.title}</h4>
                    </div>
                    <div class="modal-body">
                        ${body()}"""
        if (attrs.form) {
            out << '<div class="alert alert-danger"></div>'
        }
        out << "</div>"
        if (attrs.footer != false) {
            out << """<div class="modal-footer">"""
            if (attrs.button) {
                attrs.button.each { button ->
                    out << "<button type='${button.type ?: 'button'}' " +
                            "class='btn btn-${button.color ?: 'primary'} ${button.class ?: ''}'>${button.text}</button>"
                }
            }
            def closeButton = attrs.closeButton ?: message(code: 'is.dialog.close')
            out << """  <button type="button" class="btn btn-default" tooltip-append-to-body="true" uib-tooltip="$closeButton" ng-click="\$close()">$closeButton</button>"""
            if (attrs.submitButton) {
                out << "<button type='submit' ${attrs.validate ? 'ng-disabled="' + attrs.name + '.$invalid"' : ''} class='btn btn-primary'>${attrs.submitButton}</button>"
            }
            out << """  </div>"""
        }
        if (attrs.form) {
            out << "</form>"
        }
        out << """ </div>"""
    }

    def includeContent(attrs) {
        if (attrs.action && !attrs.controller) {
            def controller = request?.getAttribute(GrailsApplicationAttributes.CONTROLLER)
            def controllerName = controller?.getProperty(ControllerDynamicMethods.CONTROLLER_NAME_PROPERTY)
            attrs.controller = controllerName
        }
        if (attrs.controller || attrs.view) {
            def mapping = new ForwardUrlMappingInfo(controller: attrs.controller,
                                                    action: attrs.action,
                                                    view: attrs.view,
                                                    id: attrs.id,
                                                    params: attrs.params)
            return UrlMappingUtils.includeForUrlMappingInfo(request, response, mapping, attrs.model ?: [:])
        }
    }
}