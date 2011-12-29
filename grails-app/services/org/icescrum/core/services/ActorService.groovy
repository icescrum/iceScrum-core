/*
 * Copyright (c) 2010 iceScrum Technologies.
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
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */

package org.icescrum.core.services

import java.text.SimpleDateFormat
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumActorEvent
import org.icescrum.core.event.IceScrumEvent
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional

class ActorService {

    static transactional = true
    def springSecurityService

    @PreAuthorize('productOwner() and !archivedProduct()')
    void save(Actor act, Product p) {
        act.name = act.name?.trim()
        act.uid = Actor.findNextUId(p.id)
        act.backlog = p

        if (!act.save(flush: true))
            throw new RuntimeException()
        p.addToActors(act)
        broadcast(function: 'add', message: act)
        publishEvent(new IceScrumActorEvent(act, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_CREATED))
    }

    @PreAuthorize('productOwner() and !archivedProduct()')
    void delete(Actor act) {
        def id = act.id
        def stillHasPbi = act.backlog.stories.any {it.actor?.id == act.id}
        if (stillHasPbi)
            throw new RuntimeException()
        act.delete()
        broadcast(function: 'delete', message: [class: act.class, id: id])
    }

    @PreAuthorize('productOwner() and !archivedProduct()')
    void update(Actor act) {
        act.name = act.name?.trim()
        if (!act.save(flush: true))
            throw new RuntimeException()

        broadcast(function: 'update', message: act)
        publishEvent(new IceScrumActorEvent(act, this.class, (User) springSecurityService.currentUser, IceScrumEvent.EVENT_UPDATED))
    }

    @Transactional(readOnly = true)
    def unMarshall(def actor) {
        try {
            def a = new Actor(
                    name: actor."${'name'}".text(),
                    description: actor.description.text(),
                    notes: actor.notes.text(),
                    creationDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(actor.creationDate.text()),
                    instances: (actor.instances.text().isNumber()) ? actor.instances.text().toInteger() : 0,
                    useFrequency: (actor.useFrequency.text().isNumber()) ? actor.useFrequency.text().toInteger() : 2,
                    expertnessLevel: (actor.expertnessLevel.text().isNumber()) ? actor.expertnessLevel.text().toInteger() : 1,
                    satisfactionCriteria: actor.satisfactionCriteria.text(),
                    uid: actor.@uid.text()?.isEmpty() ? actor.@id.text().toInteger() : actor.@uid.text().toInteger()
            )
            return a
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }
}