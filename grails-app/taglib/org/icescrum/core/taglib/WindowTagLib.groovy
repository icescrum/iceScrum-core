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
    def groovyPageLocator

    def window = { attrs, body ->
        assert attrs.windowDefinition
        out << g.render(template: '/components/window', plugin: 'icescrum-core', model: [windowDefinition: attrs.windowDefinition, content: body()])
    }

    def widget = { attrs, body ->
        assert attrs.widgetDefinition
        out << g.render(template: '/components/widget', plugin: 'icescrum-core', model: [widgetDefinition: attrs.widgetDefinition, content: body()])
    }

    def modal = { attrs, body ->
        out << """<div class="modal-content ${attrs['class']}">"""
        def name = attrs.name ? "name='$attrs.name'" : ''
        def validation = attrs.validate ? 'show-validation' : ''
        if (attrs.form) {
            out << "<form role='form' $validation $name ng-submit='${attrs.form}' ${attrs.autoFillFix ? 'form-autofill-fix' : ''} novalidate>"
        }
        out << """  <div class="modal-header">
                        <button type="button" class="close" ng-click="\$dismiss()" tabindex="-1" aria-hidden="true">&times;</button>
                        <h4 class="modal-title" id="modal${attrs.name?:''}">${attrs.title}</h4>
                    </div>
                    <div class="modal-body">
                        ${body()}"""
        if (attrs.form) {
            out << '<div class="alert alert-danger"></div>'
        }
        out << "</div>"
        if (attrs.footer != false) {
            out << """<div class="modal-footer">"""
            def closeButton = attrs.closeButton ?: message(code: 'is.dialog.close')
            out << """  <button type="button" class="btn btn-default" ng-click="\$close()">$closeButton</button>"""
            if (attrs.button) {
                attrs.button.each { button ->
                    out << "<button type='${button.type ?: 'button'}' " +
                            "ng-click='${button.action ?: 'button'}' " +
                            "class='btn btn-${button.color ?: 'primary'} ${button.class ?: ''}'>${button.text}</button>"
                }
            }
            if (attrs.submitButton) {
                out << "<button type='submit' ${attrs.validate ? 'ng-disabled="' + attrs.name + '.$invalid"' : ''} class='btn btn-${attrs.submitButtonColor ?: 'primary'}'>${attrs.submitButton}</button>"
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