package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Sprint

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 21/02/11
 * Time: 00:59
 * To change this template use File | Settings | File Templates.
 */

class IceScrumSprintEvent extends IceScrumEvent {

    static final String EVENT_ACTIVATED = 'Activated'
    static final String EVENT_CLOSED = 'Closed'

    Date oldStartDate
    Date oldEndDate

    IceScrumSprintEvent(Sprint sprint, Class generatedBy, User doneBy, def type, boolean synchronous = false){
        super(sprint, generatedBy, doneBy, type, synchronous)
    }

    IceScrumSprintEvent(Sprint sprint, Date oldStartDate, Date oldEndDate, Class generatedBy, User doneBy, def type, boolean synchronous = false){
        super(sprint, generatedBy, doneBy, type, synchronous)
        this.oldStartDate = oldStartDate
        this.oldEndDate = oldEndDate
    }
}