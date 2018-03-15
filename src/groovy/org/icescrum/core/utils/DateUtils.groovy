/*
 * Copyright (c) 2018 Kagilum SAS
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
 * Colin Bontemps (cbontemps@kagilum.com)
 */

package org.icescrum.core.utils

import java.text.SimpleDateFormat

class DateUtils {

    static boolean isDateWeekend(Date date) {
        Calendar calendar = Calendar.getInstance()
        calendar.setTime(date)
        return calendar.get(Calendar.DAY_OF_WEEK) in [Calendar.SATURDAY, Calendar.SUNDAY]
    }

    static Date getMidnightDate(Date date) {
        return new Date(date.time).clearTime()
    }

    static parseDateISO8601(String date) {
        return new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX").parse(date)
    }

    static Date parseDateFromExport(String date) {
        if (!date) {
            return null
        }
        try {
            return new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(date)
        } catch (Exception e) { // Ugly hack because export is toString and if java.util.Date has been exported instead of a java.sql.Date the format is different
            String utcDate = date.take(20) + 'UTC' + date.drop(23) // Fix date that has been exported with server not UTC
            return new SimpleDateFormat('EEE MMM d HH:mm:ss zzz yyyy').parse(utcDate)
        }
    }
}
