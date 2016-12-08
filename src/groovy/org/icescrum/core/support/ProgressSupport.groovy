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

package org.icescrum.core.support

class ProgressSupport implements Serializable{
  def buffer
  float value = 0.0f
  String label = '%'
  int multiple = 1
  Boolean error = false
  Boolean complete = false

  def updateProgress(value,label = null){
    this.value = value
    this.label = label?:this.label
  }

  def progressError(label = null){
    this.value = 100
    this.label = label?:this.label
    this.error = true
  }

  def completeProgress(label = null){
    this.value = 100
    this.label = label?:this.label
    this.complete = true
  }
}
