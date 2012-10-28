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
import org.icescrum.core.ui.UiDefinition

class MenuTagLib {

    static namespace = 'is'
    
    def uiDefinitionService
    def menuBarSupport

    /**
     * Generate iceScrum menu bar (only show up when a project is opened)
     */
    def menuBar = { attrs, body ->
        def menuElements = []
        def menuElementsHiddden = []
        uiDefinitionService.getDefinitions().each {String id, UiDefinition uiDefinition ->
            def menuBar = uiDefinition.menuBar
            if(menuBar?.productDynamicBar) {
                menuBar.show = menuBarSupport.productDynamicBar(id, menuBar.defaultVisibility, menuBar.defaultPosition)
            }
            def show = menuBar?.show
            if (show in Closure) {
                show.delegate = delegate
                show = show()
            }
            if (show && show.visible) {
                menuElements << [title: menuBar.title,
                        id: id,
                        selected: id == session.currentWindow,
                        position: show.pos.toInteger() ?: 1,
                        widgetable: uiDefinition.widget ? true : false,
                ]
            } else if (show) {
                menuElementsHiddden << [title: menuBar.title,
                        id: id,
                        selected: id == session.currentWindow,
                        position: show.pos ?: 1,
                        widgetable: uiDefinition.widget ? true : false,
                ]
            }
        }
        menuElements = menuElements.sort {it.position}
        menuElementsHiddden = menuElementsHiddden.sort {it.position}
        out << g.render(template: '/components/menuBar', plugin: 'icescrum-core', model: [menuElements: menuElements, menuElementsHiddden: menuElementsHiddden])
    }

    /**
     * Generate a project menu element
     */
    def menuElement = { attrs, body ->

        out << "<li class='menubar ${ attrs.separator ? 'separator' : ''} navigation-line li ${attrs.widgetable ? 'widgetable' : ''} ${attrs.draggable ? 'draggable-to-desktop' : ''}' ${attrs.hidden ? 'hidden=\'true\'' : ''} id='elem_${attrs.id}'>"
        out << "<a class='button-s clearfix' href='#${attrs.id}'><span class='start'></span><span class='content'>${message(code: attrs.title)}</span><span class='end'></span></a>"
        out << "</li>"
    }

    /**
     * Generate a project menu element
     */
    def menuElementHidden = { attrs, body ->

        out << "<li>"
        out << "<a class='button-s clearfix href='#${attrs.id}'><span class='start'></span><span class='content'>${message(code: attrs.title)}</span><span class='end'></span></a>"
        out << "</li>"
    }
}
