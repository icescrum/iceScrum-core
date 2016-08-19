package org.icescrum.core.error

/*
 * Copyright (c) 2016 Kagilum.
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

/**
 * Exception that don't represent a bug but rather incorrect values or behavior from the user
 * @param text Text message to return to the user, don't provide i18n but use directly the "code" shorthand instead
 * @param code i18n message code to be formatted and returned to the user, used only if no text provided
 * @param args arguments for the i18n message
 */
class BusinessException extends RuntimeException {
    String text
    String code
    List<String> args
}