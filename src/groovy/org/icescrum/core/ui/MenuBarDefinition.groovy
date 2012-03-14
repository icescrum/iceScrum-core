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
class MenuBarDefinition {

    private final log = LoggerFactory.getLogger(this.class.name)

    private String title = ''
    private show // no type because it can be a closure
    private boolean defaultVisibility
    private int  defaultPosition
    private boolean productDynamicBar = false

    void title(String title) {
        this.title = title
    }

    void show(show) {
        this.show = show
    }

    void defaultVisibility(boolean defaultVisibility) {
        this.defaultVisibility = defaultVisibility
    }

    void defaultPosition(int defaultPosition) {
        this.defaultPosition = defaultPosition
    }

    void productDynamicBar(boolean productDynamicBar) {
        this.productDynamicBar = productDynamicBar
    }

    def methodMissing(String name, args) {
        log.warn("The field $name is unrecognized for menu bar UI definition")
    }
}
