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

import org.icescrum.components.UtilsWebComponents

class EventlineTagLib {
    static namespace = 'is'

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
            out << jq.jquery(null, "jQuery('${attrs.container}').eventline({${opts}});jQuery.doTimeout(200,function(){jQuery(window).trigger('resize');});")
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
        body()
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
        if (attrs.droppable != null && UtilsWebComponents.rendered(attrs.droppable) && !request.readOnly) {
            def droppableOptions = [
                    drop: attrs.droppable.drop ? "function(event, ui) {${attrs.droppable.drop}}" : null,
                    hoverClass: UtilsWebComponents.wrap(attrs.droppable.hoverClass),
                    activeClass: UtilsWebComponents.wrap(attrs.droppable.activeClass),
                    accept: UtilsWebComponents.wrap(attrs.droppable.accept)
            ]
            def opts = droppableOptions.findAll {k, v -> v}.collect {k, v -> " $k:$v"}.join(',')
            //jqCode += "\$('.event-container[data-elemid=${pageScope.event.elemid}] > .event-content-list').liveDroppable({$opts});"
        }
        attrs.remove('droppable')
        pageScope.event.content += jqCode ? jq.jquery(null, jqCode) : ''
        pageScope.event.contentAttrs = attrs.findAll {k, v -> v}.collect {k, v -> "$k=\"$v\""}.join(' ')
    }
}
