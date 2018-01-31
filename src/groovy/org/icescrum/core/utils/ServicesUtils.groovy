package org.icescrum.core.utils

import org.eclipse.mylyn.wikitext.core.parser.MarkupParser
import org.eclipse.mylyn.wikitext.textile.core.TextileLanguage

import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import java.text.SimpleDateFormat

/*
 * Copyright (c) 2015 Kagilum SAS
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

    public static boolean isDateWeekend(Date date) {
        def c = Calendar.getInstance()
        c.setTime(date)
        switch (c.get(Calendar.DAY_OF_WEEK)) {
            case Calendar.SATURDAY:
            case Calendar.SUNDAY:
                return true
                break
            default:
                return false
        }
    }

    public static parseDateISO8601(String input) {
        SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX")
        return df.parse(input)
    }

    public static byte[] calculateHMACMd5(String data, String key) {
        SecretKey skey = new SecretKeySpec(key.getBytes(), "HmacMD5")
        Mac m = Mac.getInstance("HmacMD5")
        m.init(skey)
        m.update(data.getBytes())
        byte[] mac = m.doFinal()
        return mac
    }

    public static String getHexString(byte[] b) throws Exception {
        String result = "";
        for (int i = 0; i < b.length; i++) {
            result += Integer.toString((b[i] & 0xff) + 0x100, 16).substring(1);
        }
        return result;
    }

    public static String cleanXml(String xmlString) {
        if (!xmlString) {
            return xmlString
        }
        StringBuffer out = new StringBuffer()
        int i, c = 0
        for (i = 0; i < xmlString.length(); i += Character.charCount(c)) {
            c = xmlString.codePointAt(i)
            if (c == 9 ||                     // 0x9
                c == 10 ||                    // 0xA
                c == 13 ||                    // 0xD
                c >= 32 && c <= 55295 ||      // c >= 0x20    && c <= 0xD7FF
                c >= 57344 && c <= 65533 ||   // c >= 0xE000  && c <= 0xFFFD
                c >= 65536 && c <= 1114111) { // c >= 0x10000 && c <= 0x10FFFF
                out.append(Character.toChars(c))
            } else {
                out.append(' ')
            }
        }
        return out.toString()
    }

    public static String textileToHtml(String text) {
        String html = text ? new MarkupParser(markupLanguage: new TextileLanguage()).parseToHtml(text) : ''
        return html ? html.substring((html.indexOf("<body>") + "<body>".size()), html.indexOf("</body>")) : html
    }
}