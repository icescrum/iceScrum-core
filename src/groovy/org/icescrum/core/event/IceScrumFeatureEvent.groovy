package org.icescrum.core.event

import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.User
import org.icescrum.core.domain.Story

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 00:59
 * To change this template use File | Settings | File Templates.
 */
class IceScrumFeatureEvent extends IceScrumEvent {

  static final String EVENT_COPIED_AS_STORY = 'Copied'
  def story

  IceScrumFeatureEvent(Feature feature, Class generatedBy, User doneBy, def type){
    super(feature, generatedBy, doneBy, type)
  }

  IceScrumFeatureEvent(Feature feature, Story story, Class generatedBy, User doneBy, def type){
    super(feature, generatedBy, doneBy, type)
    this.story = story
  }
}
