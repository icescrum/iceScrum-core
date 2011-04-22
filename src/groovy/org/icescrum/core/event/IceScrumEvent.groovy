/*
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
 */
package org.icescrum.core.event

import org.springframework.context.ApplicationEvent

import org.icescrum.core.domain.User
import grails.util.GrailsNameUtils

class IceScrumEvent extends ApplicationEvent {

  static final String EVENT_CREATED = 'Created'
  static final String EVENT_UPDATED = 'Updated'
  static final String EVENT_BEFORE_DELETE = 'beforeDelete'
  static final String EVENT_AFTER_DELETE = 'Deleted'

  static final EVENT_CUD = [EVENT_CREATED,EVENT_UPDATED]

  Class generatedBy
  def type
  def doneBy

  IceScrumEvent(def source, Class generatedBy, User doneBy, def type){
    super(source)
    this.generatedBy = generatedBy
    this.type = type
    this.doneBy = doneBy
  }

  public getFullType(){
    return GrailsNameUtils.getShortName(this.class)+type
  }
}