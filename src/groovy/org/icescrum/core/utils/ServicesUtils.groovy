package org.icescrum.core.utils

import org.eclipse.mylyn.wikitext.core.parser.MarkupParser
import org.eclipse.mylyn.wikitext.textile.core.TextileLanguage

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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

class ServicesUtils {

    // The XML spec limits the unicode character range that is authorized
    // This methods removes the invalid unicode characters
    // It uses Integer code points instead of the Java Char type to take into account emojis: one emoji span several Java chars (surrogate pairs)
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
        if (text) {
            text = text.replaceAll('\\[ *\\]', '<i class="fa fa-square-o"></i>');
            text = text.replaceAll('\\[ *[xX] *\\]', '<i class="fa fa-check-square-o"></i>');
        }
        String html = text ? new MarkupParser(markupLanguage: new TextileLanguage()).parseToHtml(text) : ''
        return html ? html.substring((html.indexOf("<body>") + "<body>".size()), html.indexOf("</body>")) : html
    }
}