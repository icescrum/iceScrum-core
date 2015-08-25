
/*
 * Copyright (c) 2015 Kagilum.
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
 * Marwah Soltani (msoltani@kagilum.com)
 *
 */

package org.icescrum.core.domain


class Mood {
    static final int MOOD_GOOD = 1
    static final int MOOD_MEH = 2
    static final int MOOD_BAD = 3
    Integer feeling
    Date feelingDay
    static belongsTo = [user: User]

    static constraints = {
        feelingDay(unique: 'user')
    }

    def beforeValidate() {
        feelingDay = feelingDay.clearTime()
    }
}
