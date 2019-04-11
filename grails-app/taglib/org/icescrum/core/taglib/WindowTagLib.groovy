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

class WindowTagLib {

    static namespace = 'is'

    def window = { attrs, body ->
        assert attrs.windowDefinition
        out << g.render(template: '/components/window', plugin: 'icescrum-core', model: [windowDefinition: attrs.windowDefinition, content: body(), 'classes': attrs.classes ?: ''])
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
                        <h4 class="modal-title" id="modal${attrs.name ?: ''}">${attrs.icon ? '<i class="fa fa-' + attrs.icon + '"></i> ' : ''}${attrs.title}</h4>
                        <button type="button" class="close" ng-click="\$dismiss()" tabindex="-1" aria-hidden="true"></button>
                    </div>
                    <div class="modal-body">
                        ${body()}"""
        out << "</div>"
        if (attrs.footer != false) {
            out << """<div class="modal-footer">"""
            def closeButton = attrs.closeButton ?: message(code: 'is.dialog.close')
            out << """  <button type="button" class="btn btn-secondary" ng-click="\$close()">$closeButton</button>"""
            if (attrs.button) {
                attrs.button.each { button ->
                    out << "<button type='${button.type ?: 'button'}' " +
                    "ng-click='${button.action ?: 'button'}' " +
                    "class='btn btn-${button.color ?: 'primary'} ${button.class ?: ''}'>${button.text}</button>"
                }
            }
            if (attrs.submitButton) {
                out << "<button type='submit' ng-disabled='application.submitting${attrs.validate ? ' || ' + attrs.name + '.$invalid' : ''}' class='btn btn-${attrs.submitButtonColor ?: 'primary'}'>${attrs.submitButton}</button>"
            }
            out << """  </div>"""
        }
        if (attrs.form) {
            out << "</form>"
        }
        out << """ </div>"""
    }
}