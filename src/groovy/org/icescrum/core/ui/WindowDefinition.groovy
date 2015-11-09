package org.icescrum.core.ui

import org.slf4j.LoggerFactory

/*
 * Copyright (c) 2012 Kagilum SAS
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 *
 */
class WindowDefinition {

    private final log = LoggerFactory.getLogger(this.class.name)

    String init = 'index'
    String title = ''
    boolean flex = true
    boolean right = false
    boolean printable = true
    boolean fullScreen = true
    def help = null
    Closure before = null

    void init(String init) {
        this.init = init
    }

    void title(String title) {
        this.title = title
    }

    void right(boolean right) {
        this.right = right
    }

    void flex(boolean flex) {
        this.flex = flex
    }

    void help(String help) {
        this.help = help
    }

    void before(Closure before) {
        this.before = before
    }

    void fullScreen(boolean fullScreen) {
        this.fullScreen = fullScreen
    }

    void printable(boolean printable) {
        this.printable = printable
    }

    def methodMissing(String name, args) {
        log.warn("The field $name is unrecognized for window UI definition")
    }
}
