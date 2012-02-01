/*
 * Copyright (c) 2011 Kagilum SAS
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
package org.icescrum.core.event

import org.icescrum.core.domain.AcceptanceTest
import org.icescrum.core.domain.User

class IceScrumAcceptanceTestEvent extends IceScrumEvent {

  IceScrumAcceptanceTestEvent(AcceptanceTest acceptanceTest, Class generatedBy, User doneBy, def type){
    super(acceptanceTest, generatedBy, doneBy, type)
  }
}