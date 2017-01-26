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

import grails.transaction.Transactional
import org.icescrum.core.domain.Actor
import org.icescrum.core.domain.Project
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class ActorService extends IceScrumEventPublisher {

    def springSecurityService

    @PreAuthorize('productOwner(#p) and !archivedProject(#p)')
    void save(Actor actor, Project p) {
        actor.name = actor.name?.trim()
        actor.parentProject = p
        actor.uid = Actor.findNextUId(p.id)
        p.addToActors(actor)
        actor.save(flush: true)
        actor.refresh() // required to initialize collections to empty list
        publishSynchronousEvent(IceScrumEventType.CREATE, actor)
    }

    @PreAuthorize('productOwner(#actor.parentProject) and !archivedProject(#actor.parentProject)')
    void delete(Actor actor) {
        Project project = (Project) actor.parentProject
        def hasStories = project.stories.any { it.actor?.id == actor.id }
        if (hasStories) {
            throw new BusinessException(code: 'is.actor.error.still.hasStories')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, actor)
        project.removeFromActors(actor)
        publishSynchronousEvent(IceScrumEventType.DELETE, actor, dirtyProperties)
    }

    @PreAuthorize('productOwner(#actor.parentProject) and !archivedProject(#actor.parentProject)')
    void update(Actor actor) {
        actor.name = actor.name?.trim()
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, actor)
        actor.save(flush: true)
        publishSynchronousEvent(IceScrumEventType.UPDATE, actor, dirtyProperties)
    }

    def unMarshall(def actorXml, def options) {
        Project project = options.project
        Actor.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def actor = new Actor(name: actorXml."${'name'}".text(),
                                      uid: actorXml.@uid.text().toInteger())
                if (project) {
                    project.addToActors(actor)
                }
                if (options.save) {
                    actor.save()
                }
                return (Actor) importDomainsPlugins(actorXml, actor, options)
            } catch (Exception e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
                throw new RuntimeException(e)
            }
        }
    }
}
