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

class MenuDefinition {

    private final log = LoggerFactory.getLogger(this.class.name)

    def title // Can be Closure or String
    boolean defaultVisibility
    int defaultPosition

    void title(title) {
        this.title = title
    }

    void defaultVisibility(boolean defaultVisibility) {
        this.defaultVisibility = defaultVisibility
    }

    void defaultPosition(int defaultPosition) {
        this.defaultPosition = defaultPosition
    }

    def methodMissing(String name, args) {
        log.warn("The field $name is unrecognized for menu bar UI definition")
    }
}
