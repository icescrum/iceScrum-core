package org.icescrum.core.utils

import java.text.SimpleDateFormat
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.crypto.Mac

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

    public static byte[] calculateHMACMd5(String data, String key)
    {
        SecretKey skey = new SecretKeySpec(key.getBytes(), "HmacMD5")
        Mac m = Mac.getInstance("HmacMD5")
        m.init(skey)
        m.update(data.getBytes())
        byte[] mac = m.doFinal()
        return mac
    }

	public static String getHexString(byte[] b) throws Exception {
      String result = "";
      for (int i=0; i < b.length; i++) {
        result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
      }
      return result;
    }
}