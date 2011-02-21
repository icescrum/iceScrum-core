package org.icescrum.core.event

import org.icescrum.core.domain.Product
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Team

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 01:03
 * To change this template use File | Settings | File Templates.
 */
class IceScrumProductEvent extends IceScrumEvent {
  def team = null
  static final String EVENT_TEAM_ADDED = 'TeamAdded'
  static final String EVENT_TEAM_REMOVED = 'TeamRemoved'

  IceScrumProductEvent(Product product, Class generatedBy, User doneBy, def type){
    super(product, generatedBy, doneBy, type)
  }

  IceScrumProductEvent(Product product, Team team, Class generatedBy, User doneBy, def type){
    super(product, generatedBy, doneBy, type)
    this.team = team
  }
}
