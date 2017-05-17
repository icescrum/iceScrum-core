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

class ScrumTagLib {

    def springSecurityService

    static namespace = 'is'
    static returnObjectForTags = ['storyDescription']

    def generateStoryTemplate = { attrs ->
        def i18n = { g.message(code: "is.story.template." + it) }
        def newLine = attrs.newLine ?: "\n"
        out << ['as', 'ican', 'to'].collect { i18n(it) + " " }.join(newLine)
    }

    def storyDescription = { attrs ->
        def storyDescription = ""
        if (attrs.story?.description) {
            storyDescription = attrs.story.description.replaceAll(/A\[.+?-(.*?)\]/) { matched, capture1 -> capture1 }
            storyDescription = storyDescription.encodeAsHTML()
        }
        attrs.displayBR ? storyDescription.encodeAsNL2BR() : storyDescription
    }
}