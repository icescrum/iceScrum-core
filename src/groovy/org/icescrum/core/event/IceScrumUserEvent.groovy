package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.Team

class IceScrumUserEvent extends IceScrumEvent {

  def team = null
  def product = null
  def object = null

  static final String EVENT_IS_PRODUCTOWNER = 'IsProductOwner'
  static final String EVENT_IS_SCRUMMASTER = 'IsScrumMaster'
  static final String EVENT_IS_MEMBER = 'IsMember'
  static final String EVENT_IS_OWNER = 'IsOwner'
  static final String EVENT_NOT_PRODUCTOWNER = 'NotProductOwner'
  static final String EVENT_NOT_SCRUMMASTER = 'NotScrumMaster'
  static final String EVENT_NOT_MEMBER = 'NotMember'

  IceScrumUserEvent(User user, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(user, generatedBy, doneBy, type, synchronous)
  }

  IceScrumUserEvent(User user, Team team, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(user, generatedBy, doneBy, type, synchronous)
    this.team = team
  }

  IceScrumUserEvent(User user, Product product, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(user, generatedBy, doneBy, type, synchronous)
    this.product = product
  }

  IceScrumUserEvent(User user, def object, Class generatedBy, User doneBy, def type, boolean synchronous = false){
    super(user, generatedBy, doneBy, type, synchronous)
    this.object = object
  }
}