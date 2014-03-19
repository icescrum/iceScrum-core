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

import org.springframework.web.servlet.support.RequestContextUtils as RCU

import grails.converters.JSON
import grails.util.BuildSettingsHolder
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.web.util.StreamCharBuffer
import org.icescrum.components.UtilsWebComponents
import org.icescrum.core.support.ApplicationSupport
import org.springframework.beans.SimpleTypeConverter
import org.springframework.context.MessageSourceResolvable

class FormTagLib {

    static namespace = 'is'

    def grailsApplication
    def grailsAttributes

    static returnObjectForTags = ['languages']

    def languages = { attrs ->
        List locales = []
        def i18n
        if (grailsApplication.warDeployed) {
            i18n = grailsAttributes.getApplicationContext().getResource("WEB-INF/grails-app/i18n/").getFile().toString()
        } else {
            i18n = "$BuildSettingsHolder.settings.baseDir/grails-app/i18n"
        }
        //Default language
        locales << new Locale("en")
        new File(i18n).eachFile {
            def arr = it.name.split("[_.]")
            if (arr[1] != 'svn' && arr[1] != 'properties' && arr[0].startsWith('messages'))
                locales << (arr.length > 3 ? new Locale(arr[1], arr[2]) : arr.length > 2 ? new Locale(arr[1]) : new Locale(""))
        }
        def returnLocales = [:]
        locales.collect{  it ->
            returnLocales[it.language] = it.getDisplayName(it).capitalize()
        }
        return returnLocales
    }

    def options = { attrs ->
        def valueMap
        if (attrs.values instanceof List) {
            valueMap = [:]
            attrs.values.each { valueMap[it] = it }
        } else {
            valueMap = attrs.values
        }
        valueMap.each { key, value ->
            out << "<option value='$key'>$value</option>"
            out.println()
        }
    }

    //todo remove
    def autoCompleteSkin = {attrs, body ->
        assert attrs.id

        def id = attrs.id
        def source = "'"+createLink(controller: attrs.controller, action: attrs.action, params: params.product ? [product: params.product] : null)+"'"

        if (attrs.cache){
                source = """
                function( request, response ){
                    var term = request.term;
                    var cached;
                    if ( term in autoCompleteCache ) {
                        cached = autoCompleteCache[term].filter(function(object){
                            return ${attrs.filter ?: 'true'};
                        });
                        response( cached );
                        return;
                    }
                    autoCompleteLastXhr = \$.getJSON( ${source}, request, function( data, status, xhr ) {
                        autoCompleteCache[ term ] = data;
                        if ( xhr === autoCompleteLastXhr ) {
                            cached = data.filter(function(object){
                                return ${attrs.filter ?: 'true'};
                            });
                            response( cached );
                        }
                    });
                }"""
        }

        def autoParams = [
                minLength: attrs.minLength ?: '2',
                source: "${source}",
                appendTo: "'${attrs.appendTo ?: 'body'}'",
                search: attrs.onSearch ? "function(event,ui){${attrs.onSearch};}" : '',
                select: attrs.onSelect ? "function(event,ui){${attrs.onSelect};}" : '',
                change: attrs.onChange ? "function(event,ui){${attrs.onChange};}" : ''
        ]

        attrs.remove('source')
        attrs.remove('onSearch')
        attrs.remove('onSelect')
        attrs.remove('onChange')
        attrs.remove('minLength')
        attrs.remove('controller')
        attrs.remove('action')

        def renderItem = attrs.remove('renderItem')

        def autoCode = "\$('#${id}').autocomplete({"
        autoCode += autoParams.findAll {k, v -> v != ''}.collect {k, v ->
            " $k:$v"
        }.join(',')
        autoCode += "});"

        if (attrs.disabled == false) {
            attrs.remove('disabled')
        }

        if (renderItem){
           autoCode += """ \$('#${id}').data("ui-autocomplete")._renderItem = function( ul, item ) {
			    return \$( '<li></li>' )
				        .data( "ui-autocomplete-item", item )
				        .append( " ${renderItem} " )
				        .appendTo( ul );
            };
            """
        }
        out << jq.jquery(null, {autoCode})
        out << is.input(attrs, body())
    }

    //todo remove
    def autoCompleteSearch = { attrs, body ->
        def elementId = attrs.remove('elementId')
        def update = attrs.remove('update')
        def controller = attrs.remove('controller')
        def action = attrs.remove('action')
        def id = attrs.remove('id')
        def name = attrs.remove('name') ?: elementId
        def withTags = attrs.remove('withTags')
        def searchOnInit = attrs.remove('searchOnInit') == "true"
        def minLength = 0
        def url = createLink(controller: controller, action: action, params: params.product ? [product: params.product] : null , id: id)

        out << """<input class="auto-complete-searchable"
                         id="$elementId"
                         name="$name"
                         data-update="$update"
                         data-url="$url"
                         data-search-on-init="$searchOnInit"
                         ${withTags ? 'data-tag-url="' + createLink(controller: 'finder', action: 'tag', params: [product: params.product, withKeyword: true]) + '"' : ''}
                         data-min-length="$minLength"
                         ${attrs.collect {k, v -> " $k=\"$v\"" }.join('')} />"""

        out << is.shortcut(key: "ctrl+f", callback: "jQuery('#search-ui').mouseover();", scope: controller)
        out << is.shortcut(key: "esc", callback: "jQuery('#search-ui').mouseout();", scope: controller, listenOn: "'#$elementId'")
    }

    def localeTimeZone = { attrs ->
        def thelist = TimeZone.getAvailableIDs().sort().findAll {it.matches("^(Africa|America|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.*")}
        attrs.from = thelist
        attrs.value = (attrs.value ? attrs.value : TimeZone.getDefault())
        attrs.optionValue = {
            TimeZone tz = TimeZone.getTimeZone(it);
            def offset = tz.rawOffset
            def offsetSign = offset < 0 ? '-' : '+'
            Integer hour = Math.abs(offset / (60 * 60 * 1000));
            Integer min = Math.abs(offset / (60 * 1000)) % 60;
            def c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, min)
            return "UTC$offsetSign${String.format('%tR', c)}, $tz.ID"
        }
        out << is.select(attrs)
    }

    def select = { attrs ->

        if (!UtilsWebComponents.rendered(attrs)) {
            return
        }

        def noSelection = attrs.remove('noSelection')
        if (noSelection){
            attrs.'data-placeholder' = noSelection.entrySet().iterator().next().value
            attrs.'data-allow-clear' = true
            attrs.'data-width' = "element"
        }

        if (attrs.width) {
            attrs.'data-width' = attrs.remove('width')
        }

        def messageSource = grailsAttributes.getApplicationContext().getBean("messageSource")
        def locale = RCU.getLocale(request)
        def writer = out
        attrs.id = attrs.id ? attrs.id : attrs.name
        def from = attrs.remove('from')
        def keys = attrs.remove('keys')
        def optionKey = attrs.remove('optionKey')
        def optionValue = attrs.remove('optionValue')
        def ids = attrs.remove('optionId')
        def value = attrs.remove('value')
        if (value instanceof Collection && attrs.multiple == null) {
            attrs.multiple = 'multiple'
        }
        if (value instanceof StreamCharBuffer) {
            value = value.toString()
        }
        def valueMessagePrefix = attrs.remove('valueMessagePrefix')

        def disabled = attrs.remove('disabled')
        if ((disabled && Boolean.valueOf(disabled)) || disabled == 'disabled') {
            attrs.disabled = 'disabled'
        }

        writer << "<select name=\"${attrs.remove('name')?.encodeAsHTML()}\" "
        // process remaining attributes
        outputAttributes(attrs)

        writer << '>'
        writer.println()

        if (noSelection) {
            renderNoSelectionOptionImpl(writer, "", "", value)
            writer.println()
        }

        // create options from list
        if (from) {
            from.eachWithIndex {el, i ->
                def keyValue = null
                writer << '<option '
                if (keys) {
                    keyValue = keys[i]
                    writeValueAndCheckIfSelected(keyValue, value, writer)
                }
                else if (optionKey) {
                    def keyValueObject = null
                    if (optionKey instanceof Closure) {
                        keyValue = optionKey(el)
                    }
                    else if (el != null && optionKey == 'id' && grailsApplication.getArtefact(DomainClassArtefactHandler.TYPE, el.getClass().name)) {
                        keyValue = el.ident()
                        keyValueObject = el
                    }
                    else {
                        keyValue = el[optionKey]
                        keyValueObject = el
                    }
                    writeValueAndCheckIfSelected(keyValue, value, writer, keyValueObject)
                }
                else {
                    keyValue = el
                    writeValueAndCheckIfSelected(keyValue, value, writer)
                }
                if (ids) {
                    def idValue = ids[i]
                    writer << ' id="' + idValue + '" '
                }
                writer << '>'
                if (optionValue) {
                    if (optionValue instanceof Closure) {
                        writer << optionValue(el).toString().encodeAsHTML()
                    }
                    else {
                        writer << el[optionValue].toString().encodeAsHTML()
                    }
                }
                else if (el instanceof MessageSourceResolvable) {
                    writer << messageSource.getMessage(el, locale)
                }
                else if (valueMessagePrefix) {
                    def message = messageSource.getMessage("${valueMessagePrefix}.${keyValue}", null, null, locale)
                    if (message != null) {
                        writer << message.encodeAsHTML()
                    }
                    else if (keyValue) {
                        writer << keyValue.encodeAsHTML()
                    }
                    else {
                        def s = el.toString()
                        if (s) writer << s.encodeAsHTML()
                    }
                }
                else {
                    def s = el.toString()
                    if (s) writer << s.encodeAsHTML()
                }
                writer << '</option>'
                writer.println()
            }
        }
        // close tag
        writer << '</select>'
    }

    def typed = { attrs ->

        if (attrs.onlyletters) {
            attrs.ichars = (attrs.ichars ?: '') + "ÀÁÂÃÄÅÇÈÉÊËÌÍÎÏÑÒÓÔÕÖÙÚÛÜÝàáâãäåçèéêëìíîïñòóôõöùúûüýÿ!@#\$%^&€*()+=[]\\\';,/{}|\":<>?~`.- "
        }

        def param = [
                allow: attrs.allow ? '\'' + attrs.allow + '\'' : null,
                nocaps: attrs.nocaps ?: null,
                allcaps: attrs.allcaps ?: null,
                ichars: attrs.ichars ? '\'' + attrs.ichars + '\'' : null,
        ]

        def jqTyped = '{' + param.findAll {k, v -> v != null}.collect {k, v -> " $k:$v"}.join(',') + '}'
        def jqCode = "\$('${attrs.elementId}').${attrs.type}(${jqTyped});"
        out << jqCode
    }

    //todo remove
    def input = {attrs, body ->
        assert attrs.id

        attrs."class" = attrs."class" ? attrs."class" + ' input' : 'input'

        if (attrs.autofocus != null) {
            attrs."class" += " input-focus"
        }

        def typedAttrs = attrs.typed ?: null
        attrs.remove('typed')

        attrs.disabled ?: attrs.remove('disabled')

        out << "<span id=\"${attrs.id}-field\" class=\"${attrs."class"}\">"
        out << "<span class=\"start\"></span>"
        out << "<span class=\"content\">"

        attrs.remove('class')
        out << textField(attrs, body())
        out << "</span>"
        out << "<span class=\"end\"></span>"
        out << "</span>"

        def jqCode = ''
        if (typedAttrs) {
            typedAttrs.elementId = "#${attrs.id}"
            jqCode = is.typed(typedAttrs)
        }
        jqCode += "\$('#${attrs.id}-field').input();"
        out << jq.jquery(null, jqCode)
    }

    //todo remove
    def multiFilesUpload = {attrs ->
        assert attrs.name
        assert attrs.progress

        def enabled = UtilsWebComponents.enabled(attrs)

        out << """<div id=\"${attrs.elementId ?: attrs.name}-field\" class=\"${attrs."class" ?: ''} inputfile\">"""
        if (attrs.bean)
            out << is.attachedFiles(bean: attrs.bean, name: attrs.name, deletable: enabled, controller: attrs.controller ?: null, action: attrs.action ?: null, params: attrs.params)
        out << """</div>"""

        if (enabled) {

            def i18nP = [
                    fileNotAccepted: '\'' + message(code: 'is.upload.error.fileNotAccepted') + '\'',
                    fileAlReadyAdded: '\'' + message(code: 'is.upload.error.fileAlReadyAdded') + '\'',
                    fileUploaded: '\'' + message(code: 'is.upload.complete') + '\'',
            ]
            def i18n = '{' + i18nP.findAll {k, v -> v != null}.collect {k, v -> " $k:$v"}.join(',') + '}'

            def paramP = [
                    animated: attrs.progress.animated ?: null,
                    timer: attrs.progress.timer ?: null,
                    label: attrs.progress.label ? '\'' + attrs.progress.label + '\'' : null,
                    showOnCreate: attrs.progress.showOnCreate ?: null,
                    className: attrs.progress.className ? '\'' + attrs.progress.className + '\'' : null,
                    url: '\'' + attrs.progress.url + '\'',
                    startOn: attrs.progress.startOn ? '\'' + attrs.progress.startOn + '\'' : null,
                    startOnWhen: attrs.progress.startOnWhen ? '\'' + attrs.progress.startOnWhen + '\'' : null,
                    onComplete: attrs.progress.onComplete ? 'function(ui,data){' + attrs.progress.onComplete + '}' : null
            ]
            def progress = '{' + paramP.findAll {k, v -> v != null}.collect {k, v -> " $k:$v"}.join(',') + '}'

            def buttonLocale = RCU.getLocale(request).toString().toLowerCase().contains('fr') ? 'fr' : 'en'
            def paramM = [
                    name: '\'' + attrs.name + '\'',
                    accept: attrs.accept as JSON,
                    image: '\'' + grailsApplication.config.grails.serverURL + '/' + is.currentThemeImage() + 'buttons/choose-' + buttonLocale + '.png\'',
                    size: attrs.size ?: null,
                    multi: attrs.multi ?: null,
                    onUploadComplete: attrs.onUploadComplete ? 'function(fileID){' + attrs.onUploadComplete + '}' : null,
                    onSelect: attrs.onSelect ? 'function(input,form){' + attrs.onSelect + '}' : null,
                    progress: progress ?: null,
                    urlUpload: '\'' + attrs.urlUpload + '\'',
                    i18n: i18n
            ]

            def multiFilesUploadP = '{' + paramM.findAll {k, v -> v != null}.collect {k, v -> " $k:$v"}.join(',') + '}'
            out << jq.jquery(null, "\$('#${attrs.elementId ?: attrs.name}-field').multiFilesUpload(${multiFilesUploadP});")
        }
    }


    def color = {attrs, body ->
        assert attrs.id

        attrs."class" = attrs."class" ? attrs."class" + ' color {hash:true}' : 'color {hash:true}'
        def value = attrs.value ?: "#FFF"
        def id = attrs.id
        out << "<span class=\"input\" id=\"${attrs.id}-field\">"
        out << "<span class=\"start\"></span>"
        out << "<span class=\"content\">"
        out << textField(attrs, body())
        out << "<input class=\"reset\" type=\"button\" onClick=\"\$('#colorinput').val('${value}'); updateColor(\$('#${id}').val()); \$('#${id}').css('color', '#000000');\" value=\"${message(code: 'is.button.reset')}\"/>"
        out << "</span>"
        out << "<span class=\"end\"></span>"
        out << "</span>"

        out << jq.jquery(null, "\$('#${attrs["id"]}-field').input();")
    }

    //todo remove
    def fieldDatePicker = {attrs, body ->
        if (attrs."for") attrs."for" = "datepicker-" + attrs."for"
        out << is.fieldInput(attrs, body)
    }

    //todo remove
    def fieldTimePicker = {attrs, body ->
        if (attrs."for") attrs."for" = "timepicker-" + attrs."for"
        out << is.fieldInput(attrs, body)
    }

    //todo remove
    def fieldColor = {attrs, body ->
        attrs."class" = attrs."class" ? attrs."class" + ' field-color clearfix' : "field-color clearfix"
        attrs.help = attrs.help ? message(code:attrs.help, default:null) : null
        if (attrs.remove("noborder") == "true")
            attrs."class" += " field-noseparator"
        out << "<p class=\"${attrs."class"}\">"
        out << "<label for=\"${attrs."for"}\">${message(code: attrs.label)}${attrs.optional ? '<span class="optional"> (' + message(code: 'is.optional') + ')</span>' : ''}${attrs.help ? '<span class="help" title="' + attrs.help + '"> (?)</span>' : ''}</label>"
        out << "<span class=\"color\">" + body() + "</span>"
        out << "</p>"
    }

    //todo remove
    def button = {attrs, body ->
        def content
        def str = ""
        attrs."class" = attrs."class" ? attrs."class" : ""
        attrs.button = attrs.button ? attrs.button : "button-s"

        def the_body = attrs.value ? attrs.remove("value") : body()

        def history = attrs.history ? attrs.history.toBoolean() : true

        if (attrs.type == "submit") {
            attrs.remove('history')
            def generateId = "button" + new Date().time
            def onClick = '$(\'#' + generateId + '\').click();'
            attrs.id = generateId
            attrs.name = generateId
            content = "<span class=\"${attrs.button} clearfix\">"
            content += "<span class=\"start\" onClick=\"" + onClick + "\"></span>"
            content += "<span class=\"content\" onClick=\"" + onClick + "\">" + the_body + "</span>"
            content += "<span class=\"end\" onClick=\"" + onClick + "\"></span>"
            content += "<span class=\"mask-submit\">"

            if (history) {
                def fragment
                if (attrs.targetLocation) {
                    fragment = "${attrs.targetLocation}"
                } else {
                    fragment = "${controllerName}${params.id ? '/' + params.id : ''}"
                }
                attrs.onSuccess = "${attrs.onSuccess ?: ''}; \$.icescrum.addHistory('${fragment}')";
                attrs.onClick = "${attrs.onclick ?: ''}; return false;"
            }

            content += g.submitToRemote(attrs)
            content += "</span>"
            content += "</span>"
            str += content
        } else if (attrs.type == "link") {
            content = "<span class=\"start\"></span><span class=\"content\">${the_body}</span><span class=\"end\"></span>"
            attrs.remove("type");
            attrs."class" += " " + attrs.button + " clearfix"
            attrs.remove("button")
            str += is.link(attrs, content)
        } else if (attrs.type == "submitToRemote") {
            def onClick = '$(this).parent().find(\'input\').click();'
            content = "<span class=\"${attrs.button} clearfix\">"
            attrs.remove("type");
            attrs.remove("button");
            content += "<span class=\"start\" onClick=\"" + onClick + "\"></span>"
            content += "<span class=\"content\" onClick=\"" + onClick + "\">" + the_body + "</span>"
            content += "<span class=\"end\" onClick=\"" + onClick + "\"></span>"
            content += "<span class=\"mask-submit\">"
            content += g.submitToRemote(attrs)
            content += "</span>"
            content += "</span>"
            str += content
        }

        try {
            if (pageScope.parent)
                str = "<td>" + str + "</td>"
        } catch (e) {}

        out << str
    }

    //todo remove
    def browser = {attrs, body ->
        out << g.render(template: '/components/browser', plugin: 'icescrum-core', model: [
                actionButton: body(),
                initContent: attrs.remove('initContent'),
                controller: attrs.remove('controller'),
                noFinder: attrs.remove('noFinder') == 'true',
                actionColumn: attrs.actionColumn,
                name: attrs.name,
                titleLabel: attrs.titleLabel,
                browserLabel: attrs.browserLabel,
                detailsLabel: attrs.detailsLabel
        ])
    }

    //todo remove
    def datePicker = { attrs, body ->
        def jqCode = ''
        def wrapDateFormat = {value ->
            if (value == null) return null
            if (value instanceof Date) {
                return 'new Date(' + formatDate(date: value, format: 'yyyy,M-1,d') + ')'
            } else if ((value instanceof String || value instanceof GString) && (value.isNumber() || value.indexOf('new Date') != -1))
                return value
            else
                return UtilsWebComponents.wrap(value)
        }

        if (attrs.onSelect)
            attrs.onSelect = "function(dateText, inst) {${attrs.onSelect}}"

        def args = [
                minDate: wrapDateFormat(attrs.minDate),
                maxDate: wrapDateFormat(attrs.maxDate),
                defaultDate: wrapDateFormat(attrs.defaultDate),
                dateFormat: UtilsWebComponents.wrap(attrs.dateFormat),
                changeMonth: attrs.changeMonth,
                changeYear: attrs.changeYear,
                onSelect: attrs.onSelect,
                firstDay: 1
        ]
        if (attrs.mode && attrs.mode == 'inline') {
            out << "<input type=\"hidden\" id=\"datepicker-input-${attrs.id}\" name=\"${attrs.name}\" class=\"datePicker\" />"
            out << "<div id=\"datepicker-${attrs.id}\" class=\"datePicker\"></div>"
            args.altField = UtilsWebComponents.wrap("#datepicker-input-${attrs.id}")
            args.altFormat = UtilsWebComponents.wrap("yy-mm-dd")
        } else if (attrs.mode && attrs.mode == 'read-input') {

            def argsInput = [
                    id: "datepicker-" + attrs.id,
                    name: attrs.name,
                    class: "datePicker"
            ]
            out << is.input(argsInput, "")
            // out << "<input type=\"text\" id=\"datepicker-${attrs.id}\" name=\"${attrs.name}\" class=\"datePicker\" value=\"\"/>"
            jqCode += "\$('#datepicker-${attrs.id}').attr('readonly', true);"
        } else {
            out << "<input type=\"text\" id=\"datepicker-${attrs.id}\" name=\"${attrs.name}\" class=\"datePicker\" />"
        }
        def opts = args.findAll {k, v -> v != null}.collect {k, v -> " $k:$v"}.join(',')
        jqCode += "\$('#datepicker-${attrs.id}').datepicker({${opts}});"
        jqCode += "\$('#datepicker-${attrs.id}').datepicker('setDate', ${args.defaultDate});"
        if (attrs.disabled && (attrs.disabled == 'true' || attrs.disabled == true))
            jqCode += "\$('#datepicker-${attrs.id}').datepicker('disable');"
        out << jq.jquery(null, jqCode)
    }

    //todo remove
    def timePicker = { attrs, body ->

        assert attrs.id

        def jqCode = ''

        if (attrs.onSelect)
            attrs.onSelect = "function(dateText, inst) {${attrs.onSelect}}"

        def args = [
                ampm: attrs.ampm ?: null,
                showHour: attrs.showHour ?: null,
                showMinute: attrs.showMinute ?: null,
                showSecond: attrs.showSecond ?: null,
                stepHour: attrs.stepHour ?: null,
                stepMinute: attrs.stepMinute ?: null,
                stepSecond: attrs.stepSecond ?: null,
                hour: attrs.hour ?: null,
                minute: attrs.minute ?: null,
                second: attrs.second ?: null,
                hourGrid: attrs.hourGrid ?: null,
                minuteGrid: attrs.minuteGrid ?: null,
                secondGrid: attrs.secondGrid ?: null,
                timeFormat: attrs.timeFormat ? UtilsWebComponents.wrap(attrs.timeFormat) : null
        ]

        def argsInput = [
                id: "timepicker-" + attrs.id,
                name: attrs.name,
                value: attrs.value,
                class: "datePicker"
        ]

        out << is.input(argsInput, "")

        if (attrs.mode && attrs.mode == 'read-input') {
            jqCode += "\$('#timepicker-${attrs.id}').attr('readonly', true);"
        }

        def opts = args.findAll {k, v -> v != null}.collect {k, v -> " $k:$v"}.join(',')
        jqCode += "\$('#timepicker-${attrs.id}').timepicker({${opts}});"

        if (attrs.disabled && (attrs.disabled == 'true' || attrs.disabled == true))
            jqCode += "\$('#timepicker-${attrs.id}').timepicker('disable');"
        out << jq.jquery(null, jqCode)
    }

    /**
     * Dump out attributes in HTML compliant fashion
     */
    void outputAttributes(attrs) {
        attrs.remove('tagName') // Just in case one is left
        def writer = getOut()
        attrs.each {k, v ->
            writer << "$k=\"${v.encodeAsHTML()}\" "
        }
    }

    def renderNoSelectionOption = {noSelectionKey, noSelectionValue, value ->
        renderNoSelectionOptionImpl(out, noSelectionKey, noSelectionValue, value)
    }

    def renderNoSelectionOptionImpl(out, noSelectionKey, noSelectionValue, value) {
        // If a label for the '--Please choose--' first item is supplied, write it out
        out << "<option value=\"${(noSelectionKey == null ? '' : noSelectionKey)}\"${noSelectionKey == value ? ' selected="selected"' : ''}>${noSelectionValue.encodeAsHTML()}</option>"
    }

    def typeConverter = new SimpleTypeConverter()

    private writeValueAndCheckIfSelected(keyValue, value, writer) {
        writeValueAndCheckIfSelected(keyValue, value, writer, null)
    }

    private writeValueAndCheckIfSelected(keyValue, value, writer, el) {

        boolean selected = false
        def keyClass = keyValue?.getClass()
        if (keyClass.isInstance(value)) {
            selected = (keyValue == value)
        }
        else if (value instanceof Collection) {
            // first try keyValue
            selected = value.contains(keyValue)
            if (!selected && el != null) {
                selected = value.contains(el)
            }
        }
        else if (keyClass && value) {
            try {
                value = typeConverter.convertIfNecessary(value, keyClass)
                selected = (keyValue == value)
            }
            catch (Exception) {
                // ignore
            }
        }
        writer << "value=\"${keyValue}\" "
        if (selected) {
            writer << 'selected="selected" '
        }
    }
}
