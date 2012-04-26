package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Task

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 00:59
 * To change this template use File | Settings | File Templates.
 */
class IceScrumTaskEvent extends IceScrumEvent {

  static final String EVENT_STATE_WAIT = 'Wait'
  static final String EVENT_STATE_IN_PROGRESS = 'InProgress'
  static final String EVENT_STATE_DONE = 'Done'
  static final String EVENT_STATE_BLOCKED = 'Blocked'

  IceScrumTaskEvent(Task task, Class generatedBy, User doneBy, def type, boolean synchronous = true){
    super(task, generatedBy, doneBy, type, synchronous)
  }
}