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
 */

package org.icescrum.core.taglib

import org.icescrum.components.UtilsWebComponents

class KanbanTagLib {
    static namespace = 'is'


    def kanban = {attrs, body ->
        pageScope.kanbanHeaders = []
        pageScope.kanbanRows = []
        body()

        def opts
        def maxCols = pageScope.kanbanHeaders.size()

        if (!attrs.onlyRows) {
            // Selectable options
            if (attrs.selectable != null) {
                def selectableOptions = [
                        filter: UtilsWebComponents.wrap(attr: (attrs.selectable.filter), doubleQuote: true),
                        cancel: UtilsWebComponents.wrap(attrs.selectable.cancel),
                        selected: "function(event,ui){${attrs.selectable.selected}}",
                        stop: attrs.selectable.stop,
                ]
                opts = selectableOptions.findAll {k, v -> v}.collect {k, v -> " $k:$v" }.join(',')
            }

            out << '<table border="0" cellpadding="0" cellspacing="0" ' + (attrs.id ? "id=\"${attrs.id}\" " : '') + (attrs.elemid ? " elemid=\"${attrs.elemid}\" " : '') + 'class="table kanban">'
            // Header
            out << "<thead>"
            out << '<tr class="table-legend">'
            pageScope.kanbanHeaders.eachWithIndex { col, index ->
                if (index == 0)
                    out << '<th class="first kanban-col"><div class="table-cell">' << col.name << '</div></th>'
                else if (index == (maxCols - 1))
                    out << '<th class="last kanban-col"><div class="table-cell">' << col.name << '</div></th>'
                else
                    out << '<th class="kanban-col"><div class="table-cell">' << col.name << '</div></th>'
            }
            out << '</tr>'
            out << "</thead>"
        }
        // Rows
        def tbodyGroup = null
        pageScope.kanbanRows.eachWithIndex { row, indexRow ->
            if ((tbodyGroup != row.attrs.type || tbodyGroup == null) && row.attrs.type) {
                if (tbodyGroup) {
                    out << "</tbody>"
                }
                out << '<tbody type="' + row.attrs.type + '">'
                tbodyGroup = row.attrs.type
            }
            if (row) {
                out << '<tr class="table-line ' + (row.attrs?.'class' ? row.attrs?.'class' : '') + ' " ' + (row.attrs.type != null ? 'type="' + row.attrs.type + '"' : '') + ' ' + (row.elemid ? 'elemid="' + row.elemid + '"' : '') + '> '
                row.columns.eachWithIndex { col, indexCol ->
                    if (indexCol == 0)
                        out << '<td class="kanban-col first kanban-cell ' + col.'class' + '" ' + (col.key != null ? 'type="' + col.key + '"' : '') + '"><div class="kanban-label">' + is.nbps(null, col?.body(row.attrs)) + '</div></td>'
                    else if (indexCol == (maxCols - 1))
                        out << '<td class="kanban-col last kanban-cell ' + col.'class' + '" ' + (col.key != null ? 'type="' + col.key + '"' : '') + '">' + is.nbps(null, col?.body(row.attrs)) + '</td>'
                    else
                        out << '<td class="kanban-col kanban-cell ' + col.'class' + '" ' + (col.key != null ? 'type="' + col.key + '"' : '') + '">' + is.nbps(null, col?.body(row.attrs)) + '</td>'
                }
                out << '</tr>'
            }
        }

        def jqCode = ''

        if (!attrs.onlyRows) {
            out << "</tbody>"
            if (opts)
                jqCode += " \$('.kanban').selectable({${opts}}); "
            out << '</table>'
        }

        // Droppable options
        if (attrs.droppable != null && UtilsWebComponents.rendered(attrs.droppable)) {
            def droppableOptions = [
                    drop: attrs.droppable.drop ? "function(event, ui) {${attrs.droppable.drop}}" : null,
                    hoverClass: UtilsWebComponents.wrap(attrs.droppable.hoverClass),
                    activeClass: UtilsWebComponents.wrap(attrs.droppable.activeClass),
                    accept: UtilsWebComponents.wrap(attrs.droppable.accept)
            ]
            opts = droppableOptions.findAll {k, v -> v}.collect {k, v -> " $k:$v"}.join(',')
            jqCode += " \$('${attrs.droppable.selector}').liveDroppable({$opts});"

        }
        out << jq.jquery(null, jqCode);
    }

    /**
     * Helper tag for a Kanban header
     */
    def kanbanHeader = { attrs, body ->
        if (pageScope.kanbanHeaders == null) return

        def options = [
                name: attrs.name,
                key: attrs.key,
                'class': attrs.'class',
        ]

        pageScope.kanbanHeaders << options
    }

    /**
     * Helper tag for the Kanban rows
     */
    def kanbanRows = { attrs, body ->
        attrs.'in'.eachWithIndex { row, indexRow ->
            def attrsCloned = attrs.clone()
            attrsCloned[attrs.var] = row
            pageScope.kanbanColumns = []
            body(attrsCloned)
            def columns = pageScope.kanbanColumns.clone()
            attrsCloned.remove('in')
            def options = [
                    columns: columns,
                    attrs: attrsCloned,
                    elemid: attrs.elemid ? row."${attrs.elemid}" : null
            ]

            pageScope.kanbanRows << options
        }
        if (attrs.emptyRendering && !attrs.'in') {
            def attrsCloned = attrs.clone()
            def options = [
                    attrs: attrsCloned,
            ]
            pageScope.kanbanRows << options
        }

    }

    /**
     * Helper tag for a specific kanban row
     */
    def kanbanRow = { attrs, body ->

        if (!UtilsWebComponents.rendered(attrs))
            return

        pageScope.kanbanColumns = []
        body()
        def options = [
                columns: pageScope.kanbanColumns,
                attrs: attrs,
                elemid: attrs.elemid ? attrs.elemid : null
        ]
        pageScope.kanbanRows << options
    }

    /**
     * Helper tag for the column content
     */
    def kanbanColumn = { attrs, body ->
        if (pageScope.kanbanColumns == null) return

        def options = [
                key: attrs.key,
                'class': attrs.'class' ?: '',
                body: body ?: {->}
        ]
        pageScope.kanbanColumns << options
    }
}