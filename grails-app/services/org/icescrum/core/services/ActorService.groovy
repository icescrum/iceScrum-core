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
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Stéphane Maldini (stephane.maldini@icescrum.com)
 * Manuarii Stein (manuarii.stein@icescrum.com)
 */

package org.icescrum.core.services

import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

import java.text.SimpleDateFormat
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Product
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional

@Transactional
class ActorService extends IceScrumEventPublisher {

    def springSecurityService

    @PreAuthorize('productOwner(#p) and !archivedProduct(#p)')
    void save(Actor actor, Product p) {
        actor.name = actor.name?.trim()
        actor.uid = Actor.findNextUId(p.id)
        actor.backlog = p
        p.addToActors(actor)
        if (!actor.save(flush: true)) {
            throw new RuntimeException()
        }
        actor.refresh() // required to initialize collections to empty list
        publishSynchronousEvent(IceScrumEventType.CREATE, actor)
    }

    @PreAuthorize('productOwner(#actor.backlog) and !archivedProduct(#actor.backlog)')
    void delete(Actor actor) {
        Product product = (Product) actor.backlog
        def stillHasPbi = product.stories.any { it.actor?.id == actor.id }
        if (stillHasPbi) {
            throw new RuntimeException('is.actor.error.still.hasStories')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, actor)
        product.removeFromActors(actor)
        publishSynchronousEvent(IceScrumEventType.DELETE, actor, dirtyProperties)
    }

    @PreAuthorize('productOwner(#actor.backlog) and !archivedProduct(#actor.backlog)')
    void update(Actor actor) {
        actor.name = actor.name?.trim()
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, actor)
        if (!actor.save(flush: true)) {
            throw new RuntimeException()
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, actor, dirtyProperties)
    }

    @Transactional(readOnly = true)
    def unMarshall(def actor) {
        try {
            def a = new Actor(
                    name: actor."${'name'}".text(),
                    description: actor.description.text(),
                    notes: actor.notes.text(),
                    todoDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(actor.todoDate.text()),
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