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
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */


package org.icescrum.core.taglib

import org.icescrum.components.UtilsWebComponents
import org.codehaus.groovy.grails.commons.ApplicationHolder
import grails.plugin.springcache.key.CacheKeyBuilder

class EventlineTagLib {
    static namespace = 'is'
    def springcacheService

    def eventline = { attrs, body ->
        pageScope.events = []
        body()
        def titles = []
        pageScope.events.collect {v -> titles << [title: v.title, elemid: v.elemid]}

        def events = ''
        pageScope.events.eachWithIndex { v, index ->
            events += render(template: '/components/event', plugin: 'icescrum-core', model: [
                    id: attrs.id,
                    header: v.header.content,
                    headerClass: v.header."class",
                    headerAttrs: v.headerAttrs,
                    content: v.content,
                    elemid: v.elemid,
                    contentClass: index % 2 ? 'event-content-list-odd' : '',
                    contentAttrs: v.contentAttrs
            ])
        }

        def params = [
                events: events,
                elemid: attrs.elemid,
                titles: titles
        ]
        if (!attrs.onlyEvents) {
            out << g.render(template: '/components/eventline', plugin: 'icescrum-core', model: params)
            def jsParams = [
                    focus: attrs.focus
            ]
            def opts = jsParams.findAll {k, v -> v}.collect {k, v -> " $k:$v"}.join(',')
            out << jq.jquery(null, "jQuery('${attrs.container}').eventline({${opts}});")
        } else {
            params.events.each {
                    out << it
            }
        }
    }

    def event = { attrs, body ->
        pageScope.event = [
                    header: [],
                    content: '',
                    title: attrs.title,
                    contentAttrs: '',
                    elemid: attrs.elemid
            ]
        if (attrs.cacheable && !attrs.cacheable?.disabled){

            attrs.cacheable.role = attrs.cacheable.role ?: true
            attrs.cacheable.locale = attrs.cacheable.locale ?: true
            def cacheResolver = ApplicationHolder.application.mainContext[attrs.cacheable.cacheResolver ?: 'springcacheDefaultCacheResolver']
            def role = ''

            def key  = new CacheKeyBuilder()
            key.append(attrs.key)

            if (attrs.role){
                if (request.admin) {
                role = 'adm'
                } else {
                    if (request.scrumMaster)  {  role += 'scm'  }
                    if (request.teamMember)   {  role += 'tm'  }
                    if (request.productOwner) {  role += 'po'  }
                    if (!role && request.stakeHolder) {  role += 'sh'  }
                }
                role = role ?: 'anonymous'
                key.append(role)
            }

            if (attrs.locale)
                key.append(RCU.getLocale(request).toString().substring(0, 2))

            pageScope.event = springcacheService.doWithCache(cacheResolver.resolveCacheName(attrs.cacheable.cache), key.toCacheKey()){
                println "Caching tag event "+attrs.cacheable.cache+" "+attrs.cacheable.key
                body()
                return pageScope.event
            }
        }else{
            body()
        }
        pageScope.events << pageScope.event
    }

    def eventHeader = { attrs, body ->
        pageScope.event.header = [
                class: attrs."class",
                content: body()
        ]
        pageScope.event.headerAttrs = attrs.findAll {k, v -> v}.collect {k, v -> "$k=\"$v\""}.join(' ')
    }

    def eventContent = { attrs, body ->
        def jqCode = ''
        pageScope.event.content = body()
        if (attrs.droppable != null && UtilsWebComponents.rendered(attrs.droppable)) {
            def droppableOptions = [
                    drop: attrs.droppable.drop ? "function(event, ui) {${attrs.droppable.drop}}" : null,
                    hoverClass: UtilsWebComponents.wrap(attrs.droppable.hoverClass),
                    activeClass: UtilsWebComponents.wrap(attrs.droppable.activeClass),
                    accept: UtilsWebComponents.wrap(attrs.droppable.accept)
            ]
            def opts = droppableOptions.findAll {k, v -> v}.collect {k, v -> " $k:$v"}.join(',')
            jqCode += "\$('.event-container[elemid=${pageScope.event.elemid}] > .event-content-list').liveDroppable({$opts});"
        }
        attrs.remove('droppable')
        pageScope.event.content += jqCode ? jq.jquery(null, jqCode) : ''
        pageScope.event.contentAttrs = attrs.findAll {k, v -> v}.collect {k, v -> "$k=\"$v\""}.join(' ')
    }
}
