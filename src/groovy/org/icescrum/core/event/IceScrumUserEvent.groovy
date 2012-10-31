package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.core.domain.Product

class IceScrumUserEvent extends IceScrumEvent {

    def product = null
    def role = null

    static final String EVENT_ADDED_TO_PRODUCT = "AddedToProduct"
    static final String EVENT_CHANGED_ROLE_IN_PRODUCT = "ChangedRoleInProduct"
    static final String EVENT_REMOVED_FROM_PRODUCT = "RemovedFromProduct"

    IceScrumUserEvent(User user, Class generatedBy, User doneBy, def type, boolean synchronous = false){
        super(user, generatedBy, doneBy, type, synchronous)
    }

    IceScrumUserEvent(User user, Product product, int role, Class generatedBy, User doneBy, def type, boolean synchronous = false){
        super(user, generatedBy, doneBy, type, synchronous)
        this.product = product
        this.role = role
    }

    IceScrumUserEvent(User user, Product product, Class generatedBy, User doneBy, def type, boolean synchronous = false){
        super(user, generatedBy, doneBy, type, synchronous)
        this.product = product
    }
}