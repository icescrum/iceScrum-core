package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Team

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 01:02
 * To change this template use File | Settings | File Templates.
 */
class IceScrumTeamEvent extends IceScrumEvent {
  def member = null
  static final String EVENT_MEMBER_ADDED = 'MemberAdded'
  static final String EVENT_MEMBER_REMOVED = 'MemberRemoved'

  IceScrumTeamEvent(Team team, Class generatedBy, User doneBy, def type){
    super(team, generatedBy, doneBy, type)
  }

  IceScrumTeamEvent(Team team, User user, Class generatedBy, User doneBy, def type){
    super(team, generatedBy, doneBy, type)
    this.member = user
  }
}
