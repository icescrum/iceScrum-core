package org.icescrum.core.utils

import java.text.SimpleDateFormat

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
 * but WITHOUT ANY WARRANTY without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 */

class ServicesUtils {

  public static boolean isDateWeekend(Date date){
    def c = Calendar.getInstance()
    c.setTime(date)
    switch (c.get(Calendar.DAY_OF_WEEK)){
      case Calendar.SATURDAY:
      case Calendar.SUNDAY:
        return true
        break
      default:
        return false
    }
  }

    public static parseDateISO8601 (String input) {
        //NOTE: SimpleDateFormat uses GMT[-+]hh:mm for the TZ which breaks
        //things a bit.  Before we go on we have to repair this.
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssz")
        //this is zero time so we need to add that TZ indicator for
        if (input.endsWith("Z")) {
            input = input.substring(0, input.length() - 1) + "GMT-00:00"
        } else {
            int inset = 6

            String s0 = input.substring(0, input.length() - inset)
            String s1 = input.substring(input.length() - inset, input.length())

            input = s0 + "GMT" + s1
        }
        return df.parse(input)
    }
}