package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Actor

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 00:58
 * To change this template use File | Settings | File Templates.
 */
class IceScrumActorEvent extends IceScrumEvent {

  IceScrumActorEvent(Actor actor, Class generatedBy, User doneBy, def type){
    super(actor, generatedBy, doneBy, type)
  }
}