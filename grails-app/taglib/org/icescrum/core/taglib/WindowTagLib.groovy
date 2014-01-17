/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 * Damien Vitrac (damien@oocube.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 * Stephane Maldini (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.taglib

import org.codehaus.groovy.grails.plugins.jasper.JasperExportFormat
import org.icescrum.components.UtilsWebComponents
import org.codehaus.groovy.grails.web.servlet.GrailsApplicationAttributes
import org.codehaus.groovy.grails.web.metaclass.ControllerDynamicMethods
import org.codehaus.groovy.grails.web.mapping.ForwardUrlMappingInfo
import org.codehaus.groovy.grails.web.util.WebUtils

class WindowTagLib {
    static namespace = 'is'

    def springSecurityService
    def grailsApplication
    def securityService

    /**
     * Generate a window
     * The attribute "id" is obligatory
     */
    def window = { attrs, body ->
        def windowId = attrs.window ?: controllerName
        attrs.type = attrs.type ?: 'window'
        def includeParams = ['type':attrs.type]
        params.each{ if (!(it.key in ["controller", "action"])) { includeParams << it} }

        // Check for content window
        def windowContent
        if (attrs.init){
            def result = includeContent([controller: windowId, action: attrs.init, params:includeParams])
            if (result.contentType == 'application/json;charset=utf-8'){
                response.setStatus(400)
                response.setContentType(result.contentType)
                out << result.content
                return
            }else{
                windowContent = result.content
            }
        }
        else {
            windowContent = body()
        }

        // Check for toolbar existence
        if (attrs.toolbar) {
            if (attrs.type == 'widget') {
                attrs.toolbar = include(controller: windowId, action: 'toolbarWidget', params: includeParams) ?: true
            } else {
                attrs.toolbar = include(controller: windowId, action: 'toolbar', params: includeParams) ?: true
            }
        }

        // Check for right content
        if (attrs.right) {
            attrs.right = include(controller: windowId, action: 'right', params: includeParams)
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
                spaceName: attrs.spaceName,
                type: attrs.type,
                title: attrs.title ?: null,
                windowActions: attrs.windowActions ?: [
                        help: attrs.help ?: null,
                        widgetable: attrs.widgetable ? true : false,
                        fullScreen: attrs.fullScreen ?: false,
                        printable:attrs.printable
                ],
                id: windowId,
                toolbar: attrs.toolbar,
                resizable: attrs.resizable ?: false,
                sortable: attrs.sortable ?: false,
                right:attrs.right,
                contentClass: attrs.contentClass,
                windowContent: windowContent
        ]
        if (windowContent && !webRequest?.params?.returnError){
            out << g.render(template: '/components/window', plugin: 'icescrum-core', model: params)
        }
    }

    def buttonNavigation = { attrs, body ->
        attrs."class" = attrs."class" ? attrs."class" : ""
        attrs."class" += attrs.button ? attrs.button : " button-n"
        attrs.remove("button");

        def str = "<span class='start'></span><span class='content'>"

        if (attrs.icon) {
            attrs."class" += " button-ico button-" + attrs.icon
            str += "<span class='ico'></span>"
            attrs.remove('icon')
        }

        if (!attrs.text)
            attrs.text = body()?.trim()

        str += "${attrs.text}</span><span class='end'>"
        if (attrs.dropmenu == 'true')
            str += "<span class='arrow'></span>"

        str += "</span>"
        attrs.remove("text");

        out << is.link(attrs, str).trim();

    }

    /**
     * Generate a widget using the is:window tag
     */
    def widget = { attrs, body ->
        attrs = attrs.attrs ?: attrs
        def params = [
                type: 'widget',
                title: attrs.title,
                windowActions: [
                        help: attrs.help ?: null,
                        closeable: attrs.closeable ?: false,
                        windowable: attrs.windowable ?: false
                ],
                window: attrs.id,
                sortable: attrs.sortable,
                resizable: attrs.resizable?:false,
                toolbar: attrs.toolbar,
                init: attrs.init
        ]
        out << is.window(params, {})
    }

    //TODO refactor
    def dialog = { attrs, body ->
        attrs.id = attrs.id ?: 'dialog'
        out << "<div id='${attrs.id}'>${body()}</div>"
        out << dialogMethod(attrs)
    }

    private dialogMethod(attrs) {

        if (!UtilsWebComponents.rendered(attrs)) {
            return
        }

        def space = params.product ? 'product' : (params.team ? 'team' : null)
        if (space && !attrs.noprefix) {
            if (!attrs.params) {
                attrs.params = [(space): params.long(space)]
            } else if (Map.isAssignableFrom(attrs.params.getClass())) {
                attrs.params << [(space): params.long(space)]
            } else {
                attrs.params = "'product=${params.long('product')}&'+" + attrs.params
            }
        }

        if (attrs.title) {
            attrs.title = message(code: attrs.title)
        }

        def function = "jQuery('#${attrs.id} form').submit();"
        if (attrs.valid && attrs.valid.action) {
            function = remoteFunction(
                    action: attrs.valid.action,
                    controller: attrs.valid.controller,
                    onSuccess: "${attrs.valid.onSuccess ? attrs.valid.onSuccess + ';' + 'jQuery(\'#'+attrs.id+'\').dialog(\'close\');' : 'jQuery(\'#'+attrs.id+'\').dialog(\'close\');'}  ",
                    before: attrs.valid.before,
                    id: attrs.valid.id,
                    update: attrs.valid.update,
                    params: "${attrs.valid.params ? attrs.valid.params + '+\'&\'+' : ''}jQuery('#${attrs.id} form:first').serialize()")
        }

        def function2 = "jQuery(this).dialog('close');"
        if (attrs.cancel) {
            function2 = remoteFunction(
                    action: attrs.cancel.action,
                    controller: attrs.cancel.controller,
                    onSuccess: "${attrs.cancel.onSuccess ? attrs.cancel.onSuccess + ';' : ''} jQuery('#${attrs.id}').dialog('close'); ",
                    before: attrs.cancel.before,
                    id: attrs.cancel.id,
                    update: attrs.cancel.update,
                    params: "${attrs.cancel.params ? attrs.cancel.params + '+\'&\'+' : ''}jQuery('#${attrs.id} form:first').serialize()")
        }

        def buttonOk = message(code: "is.button.update")
        if (attrs.valid?.button) {
            buttonOk = message(code: attrs.valid.button)
        }

        def params = [
                closeOnEscape: attrs.closeOnEscape ?: true,
                closeText: attrs.closeText ?: "\'${message(code: 'is.dialog.close')}\'",
                dialogClass: attrs.className ?: null,
                draggable: attrs.draggable ?: false,
                height: attrs.height ?: null,
                hide: attrs.hideEffect ?: null,
                show: attrs.showEffect ?: null,
                maxHeight: attrs.maxHeight ?: null,
                maxWidth: attrs.maxWidth ?: null,
                minHeight: attrs.minHeight ?: null,
                minWidth: attrs.minWidth ?: null,
                modal: attrs.modal ?: true,
                position: attrs.position ?: "'top'",
                resizable: attrs.resizable ?: false,
                title: attrs.title ? "\'${attrs.title}\'" : null,
                width: attrs.width ?: 300,
                close: """function(ev, ui) { if(ev.keyCode && ev.keyCode === jQuery.ui.keyCode.ESCAPE){ ${attrs.cancel ? function2 : ''} } """ + (attrs.onClose ? attrs.onClose + ';' : '') + " jQuery(this).remove(); jQuery('.box-window').focus();}",
                buttons: attrs.buttons ? "{" + attrs.buttons + "}" : null
        ]

        def dialogCode = "jQuery('#${attrs.id}').dialog({"

        if (attrs.onSuccess) {
            dialogCode += "${attrs.onSuccess};"
        }

        if (attrs.valid || attrs.cancel) {
            params.buttons = "{'${message(code: "is.button.cancel")}': function(){${function2}},'${buttonOk}': function(){${function}}}"
        }
        attrs.remove('valid')
        attrs.remove('cancel')

        attrs.withTitlebar = attrs.withTitlebar ? attrs.withTitlebar.toBoolean() : false

        if (!attrs.withTitlebar) {
            if(attrs.dialogClass) {
                attrs.dialogClass += " no-titlebar"
            } else {
                attrs.dialogClass = "no-titlebar"
            }
        }
        dialogCode += "dialogClass: '${attrs.dialogClass}',"

        attrs.remove('withTitlebar')

        dialogCode += params.findAll {k, v -> v != null}.collect {k, v -> "$k:$v"}.join(',')

        params.each {key, value ->
            attrs.remove(key)
        }
        attrs.remove('onSuccess')

        dialogCode += "});"

        if (attrs.focusable == null || attrs.focusable) {
            attrs.remove('focusable')
            dialogCode += "jQuery(\'.ui-dialog-buttonpane button:eq(1)\').focus();"
        } else {
            dialogCode += "jQuery(\'.ui-dialog-buttonpane button:eq(0)\').blur();"
        }



        if (attrs.onOpen) {
            dialogCode += attrs.onOpen
        }

        attrs.remove('onOpen');

        return jq.jquery(null, dialogCode)
    }

    /**
     * Display an spinner on ajax call on the selected div id
     */
    def spinner = {attrs ->
        out << jq.jquery(attrs, """
    jQuery(document).ajaxSend(function() { jQuery.icescrum.loading(); jQuery(document.body).css('cursor','progress'); });
    jQuery(document).ajaxError(function(data) { jQuery.icescrum.loadingError(); jQuery(document.body).css('cursor','default'); });
    jQuery(document).ajaxComplete(function(e,xhr,settings){  jQuery.icescrum.loading(false); if(xhr.status == 403){ $attrs.on403;}else if(xhr.status == 401){ $attrs.on401; }else if(xhr.status == 400){ $attrs.on400; }else if(xhr.status == 500){ $attrs.on500; } });
    jQuery(document).ajaxStop(function() { jQuery.icescrum.loading(false); jQuery(document.body).css('cursor','default'); });
    """)
    }

    def shortcut = {attrs ->
        if (request.readOnly){
            return
        }
        assert attrs.key
        assert attrs.callback

        if (attrs.scope)
            attrs.scope = "keydown.${attrs.scope}"
        else
            attrs.scope = "keydown"
        if (!attrs.listenOn) {
            attrs.listenOn = "document"
        }
        def escapedKey = attrs.key.replace('+', '').replace('.', '')
        def jqCode = "jQuery(${attrs.listenOn}).unbind('${attrs.scope}.${escapedKey}');"
        jqCode += "jQuery(${attrs.listenOn}).bind('${attrs.scope}.${escapedKey}','${attrs.key}',function(e){${attrs.callback}e.preventDefault();});"
        out << jq.jquery(null, jqCode)
    }

    /**
     * Implements the drag & drop import feature
     */
    def dropImport = { attrs, body ->
        if (request.readOnly){
            return
        }
        assert attrs.id
        def jqCode = """jQuery('#window-content-${attrs.id}').dnd({
        dropHelper:'#${attrs.id}-drophelper',
        drop:function(event){
          var dt = event.dataTransfer;
          ${
            remoteFunction(controller: attrs.controller ?: controllerName,
                    action: attrs.action ?: 'dropImport',
                    params: "'product=${params.product}&data='+dt.text()",
                    onSuccess: attrs.success)
        }
        }
      });"""
        out << g.render(template: '/components/dropHelper', plugin: 'icescrum-core', model: [id: attrs.id, description: message(code: attrs.description)])
        out << jq.jquery(null, jqCode)
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
            return WebUtils.includeForUrlMappingInfo(request, response, mapping, attrs.model ?: [:])
        }
    }
}