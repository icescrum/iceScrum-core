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

import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Product
import org.icescrum.core.error.BusinessException
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional

@Transactional
class ActorService extends IceScrumEventPublisher {

    def springSecurityService

    @PreAuthorize('productOwner(#p) and !archivedProduct(#p)')
    void save(Actor actor, Product p) {
        actor.name = actor.name?.trim()
        actor.parentProduct = p
        p.addToActors(actor)
        actor.save(flush: true)
        actor.refresh() // required to initialize collections to empty list
        publishSynchronousEvent(IceScrumEventType.CREATE, actor)
    }

    @PreAuthorize('productOwner(#actor.parentProduct) and !archivedProduct(#actor.parentProduct)')
    void delete(Actor actor) {
        Product product = (Product) actor.parentProduct
        def stillHasPbi = product.stories.any { it.actor?.id == actor.id }
        if (stillHasPbi) {
            throw new BusinessException(code: 'is.actor.error.still.hasStories')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, actor)
        product.removeFromActors(actor)
        publishSynchronousEvent(IceScrumEventType.DELETE, actor, dirtyProperties)
    }

    @PreAuthorize('productOwner(#actor.parentProduct) and !archivedProduct(#actor.parentProduct)')
    void update(Actor actor) {
        actor.name = actor.name?.trim()
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, actor)
        actor.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, actor, dirtyProperties)
    }

    @Transactional(readOnly = true)
    def unMarshall(def actor) {
        try {
            def a = new Actor(name: actor."${'name'}".text())
            return a
        } catch (Exception e) {
            if (log.debugEnabled) {
                e.printStackTrace()
            }
            throw new RuntimeException(e)
        }
    }
}