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
            returnLocales[it.toString()] = it.getDisplayName(it).capitalize()
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

    def localeTimeZone = { attrs ->
        def thelist = TimeZone.getAvailableIDs().sort().findAll {it.matches("^(Africa|America|Asia|Atlantic|Australia|Europe|Indian|Pacific)/.*")}
        attrs.from = thelist
        attrs.value = (attrs.value ? attrs.value : thelist.first())
        attrs.optionValue = {
            TimeZone tz = TimeZone.getTimeZone(it);
            def offset = tz.rawOffset
            def offsetSign = offset < 0 ? '-' : '+'
            Integer hour = Math.abs(offset / (60 * 60 * 1000));
            Integer min = Math.abs(offset / (60 * 1000)) % 60;
            def c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, hour)
            c.set(Calendar.MINUTE, min)
            return "$tz.ID (UTC$offsetSign${String.format('%tR', c)})"
        }
        out << g.select(attrs)
    }
}
