/*
 * Copyright (c) 2014 Kagilum.
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
 */

package org.icescrum.core.services

import grails.util.Holders
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

import java.text.SimpleDateFormat
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport

@Transactional
class TaskService extends IceScrumEventPublisher {

    def clicheService
    def springSecurityService
    def securityService
    def activityService

    @PreAuthorize('(inProduct(#task.backlog?.parentProduct) or inProduct(#task.parentStory?.parentProduct)) and (!archivedProduct(#task.backlog?.parentProduct) or !archivedProduct(#task.parentStory?.parentProduct))')
    void save(Task task, User user) {
        if (task.parentStory?.parentSprint && !task.backlog) {
            task.backlog = task.parentStory.parentSprint
        }
        Sprint sprint = task.sprint
        if (!task.id && sprint?.state == Sprint.STATE_DONE) {
            throw new IllegalStateException('is.task.error.not.saved')
        }
        if (task.estimation == 0 && task.state != Task.STATE_DONE) {
            task.estimation = null
        }
        Product product = sprint ? sprint.parentProduct : (Product) task.parentStory.backlog
        if (product.preferences.assignOnCreateTask) {
            task.responsible = user
        }
        task.parentProduct = product
        task.creator = user
        task.rank = Task.countByParentStoryAndType(task.parentStory, task.type) + 1
        task.uid = Task.findNextUId(product.id)
        if (!task.save(flush: true)) {
            throw new RuntimeException()
        }
        if (sprint) {
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        }
        publishSynchronousEvent(IceScrumEventType.CREATE, task)
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void update(Task task, User user, boolean force = false, props = [:]) {

        if (props.state != null) {
            state(task, props.state, user)
        }
        if (props.rank != null) {
            rank(task, props.rank)
        }
        def sprint = task.sprint
        if (sprint?.state == Sprint.STATE_DONE) {
            throw new IllegalStateException('is.sprint.error.state.not.inProgress')
        }
        if (sprint) {
            task.type = null
        }
        Product product = task.parentProduct
        if (task.type == Task.TYPE_URGENT
                && task.state == Task.STATE_BUSY
                && product.preferences.limitUrgentTasks != 0
                && product.preferences.limitUrgentTasks >= sprint.tasks?.findAll { it.type == Task.TYPE_URGENT && it.state == Task.STATE_BUSY && it.id != task.id }?.size()) {
            throw new IllegalStateException('is.task.error.limitTasksUrgent')
        }
        if (!(task.state == Task.STATE_DONE && task.doneDate)) {
            if (force || task.responsible?.id?.equals(user.id) || task.creator.id.equals(user.id) || securityService.scrumMaster(null, springSecurityService.authentication)) {
                if (task.state >= Task.STATE_BUSY && !task.inProgressDate) {
                    task.inProgressDate = new Date()
                    task.initial = task.estimation
                    task.blocked = false
                }
                if (task.state == Task.STATE_DONE) {
                    done(task, user)
                } else if (task.doneDate) {
                    def story = task.type ? null : Story.get(task.parentStory?.id)
                    if (story && story.state == Story.STATE_DONE) {
                        throw new IllegalStateException('is.story.error.done')
                    }
                    if (task.estimation == 0) {
                        task.estimation = null
                    }
                    task.doneDate = null
                } else if (task.estimation == 0 && sprint.state == Sprint.STATE_INPROGRESS) {
                    if (product.preferences.assignOnBeginTask && !task.responsible) {
                        task.responsible = user
                    }
                    task.state = Task.STATE_DONE
                    done(task, user)
                }
                if (task.state < Task.STATE_BUSY && task.inProgressDate) {
                    task.inProgressDate = null
                    task.initial = null
                }
            }
        } else {
            throw new IllegalStateException('is.task.error.done')
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, task)
        if (!task.save(flush: true)) {
            throw new RuntimeException(task.errors?.toString())
        }
        if (task.sprint) {
            task.sprint.lastUpdated = new Date()
            task.sprint.save()
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        }
        publishSynchronousEvent(IceScrumEventType.UPDATE, task, dirtyProperties)
    }

    private done(Task task, User user) {
        task.estimation = 0
        task.blocked = false
        task.doneDate = new Date()
        def story = task.type ? null : Story.get(task.parentStory?.id)
        if (story && task.parentProduct.preferences.autoDoneStory && !story.tasks.any { it.state != Task.STATE_DONE } && story.state != Story.STATE_DONE) {
            ApplicationContext ctx = (ApplicationContext)Holders.grailsApplication.mainContext;
            StoryService service = (StoryService) ctx.getBean("storyService");
            service.done(story)
        }
        if (user) {
            activityService.addActivity(task, user, 'taskFinish', task.name)
        }
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void delete(Task task, User user) {
        def sprint = task.sprint
        boolean scrumMaster = securityService.scrumMaster(null, springSecurityService.authentication)
        boolean productOwner = securityService.productOwner(task.parentProduct, springSecurityService.authentication)
        if (task.state == Task.STATE_DONE && !scrumMaster && !productOwner) {
            throw new IllegalStateException('is.task.error.delete.not.scrumMaster')
        }
        if (task.responsible && task.responsible.id.equals(user.id) || task.creator.id.equals(user.id) || productOwner || scrumMaster) {
            resetRank(task)
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, task)
            if (task.parentStory) {
                dirtyProperties.parentStory = task.parentStory
                activityService.addActivity(task.parentStory, user, 'taskDelete', task.name)
                task.parentStory.removeFromTasks(task)

            }
            if (task.sprint) {
                dirtyProperties.backlog = sprint
                sprint.removeFromTasks(task)
                sprint.save()
                clicheService.createOrUpdateDailyTasksCliche(sprint)
            }
            task.delete()
            publishSynchronousEvent(IceScrumEventType.DELETE, task, dirtyProperties)
        }
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    def copy(Task task, User user, def clonedState = Task.STATE_WAIT) {
        if (task.sprint?.state == Sprint.STATE_DONE) {
            throw new IllegalStateException('is.task.error.copy.done')
        }
        def clonedTask = new Task(
                name: task.name + '_1',
                rank: Task.countByParentStoryAndType(task.parentStory, task.type) + 1,
                state: clonedState,
                creator: user,
                color: task.color,
                description: task.description,
                notes: task.notes,
                dateCreated: new Date(),
                backlog: task.backlog,
                parentStory: task.parentStory ?: null,
                type: task.type
        )
        task.participants?.each {
            clonedTask.participants << it
        }
        clonedTask.validate()
        def i = 1
        while (clonedTask.hasErrors()) {
            if (clonedTask.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                i += 1
                clonedTask.name = clonedTask.name + '_' + i
                clonedTask.validate()
            } else if (clonedTask.errors.getFieldError('name')?.defaultMessage?.contains("maximum size")) {
                clonedTask.name = clonedTask.name[0..20]
                clonedTask.validate()
            } else {
                throw new RuntimeException()
            }
        }
        save(clonedTask, user)
        clicheService.createOrUpdateDailyTasksCliche(task.sprint)
        return clonedTask
    }

    private void state(Task task, Integer newState, User user) {
        def product = task.parentProduct
        if (task.sprint?.state != Sprint.STATE_INPROGRESS && newState >= Task.STATE_BUSY) {
            throw new IllegalStateException('is.sprint.error.state.not.inProgress')
        }
        if (task.state == Task.STATE_DONE && task.doneDate && newState == Task.STATE_DONE) {
            def story = task.type ? null : Story.get(task.parentStory?.id)
            if (story && story.state == Story.STATE_DONE) {
                throw new IllegalStateException('is.story.error.done')
            }
            task.doneDate = null
        } else {
            if (task.responsible == null && product.preferences.assignOnBeginTask && newState >= Task.STATE_BUSY) {
                task.responsible = user
            }
            if ((task.responsible && user.id.equals(task.responsible.id))
                    || user.id.equals(task.creator.id)
                    || securityService.productOwner(product, springSecurityService.authentication)
                    || securityService.scrumMaster(null, springSecurityService.authentication)) {
                if (newState == Task.STATE_BUSY && task.state != Task.STATE_BUSY) {
                    activityService.addActivity(task, user, 'taskInprogress', task.name)
                } else if (newState == Task.STATE_WAIT && task.state != Task.STATE_WAIT) {
                    activityService.addActivity(task, user, 'taskWait', task.name)
                }
                task.state = newState
            }
        }
    }

    private void resetRank(Task task) {
        def container = task.parentStory ?: task.backlog
        container.tasks.findAll {
            it.rank > task.rank && it.type == task.type && it.state == task.state
        }.each {
            it.rank--
            it.save()
        }
    }

    private void rank(Task task, int newRank) {
        def container = task.parentStory ?: task.backlog
        Range affectedRange = task.rank..newRank
        int delta = affectedRange.isReverse() ? 1 : -1
        container.tasks.findAll {
            it != task && it.rank in affectedRange && it.type == task.type && it.state == task.state
        }.each {
            it.rank += delta
            it.save() // consider push
        }
        task.rank = newRank
    }

    @Transactional(readOnly = true)
    def unMarshall(def task, Product p = null) {
        try {
            def inProgressDate = null
            if (task.inProgressDate?.text() && task.inProgressDate?.text() != "") {
                inProgressDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(task.inProgressDate.text()) ?: null
            }
            def doneDate = null
            if (task.doneDate?.text() && task.doneDate?.text() != "") {
                doneDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(task.doneDate.text()) ?: null
            }
            def t = new Task(
                    type: (task.type.text().isNumber()) ? task.type.text().toInteger() : null,
                    description: task.description.text(),
                    notes: task.notes.text(),
                    estimation: (task.estimation.text().isNumber()) ? task.estimation.text().toFloat() : null,
                    initial: (task.initial.text().isNumber()) ? task.initial.text().toFloat() : null,
                    rank: task.rank.text().toInteger(),
                    name: task."${'name'}".text(),
                    doneDate: doneDate,
                    inProgressDate: inProgressDate,
                    state: task.state.text().toInteger(),
                    creationDate: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(task.creationDate.text()),
                    blocked: task.blocked.text()?.toBoolean() ?: false,
                    uid: task.@uid.text()?.isEmpty() ? task.@id.text().toInteger() : task.@uid.text().toInteger(),
                    color: task?.color?.text() ?: "yellow"
            )
            if (p) {
                def u
                if (!task.creator?.@uid?.isEmpty()) {
                    u = ((User) p.getAllUsers().find { it.uid == task.creator.@uid.text() }) ?: null
                } else {
                    u = ApplicationSupport.findUserUIDOldXMl(task, 'creator', p.getAllUsers())
                }
                if (u) {
                    t.creator = u
                } else {
                    t.creator = p.productOwners.first()
                }
            }
            if ((!task.responsible?.@uid?.isEmpty() || !task.responsible?.@id?.isEmpty()) && p) {
                def u
                if (!task.responsible?.@uid?.isEmpty()) {
                    u = ((User) p.getAllUsers().find { it.uid == task.responsible.@uid.text() }) ?: null
                } else {
                    u = ApplicationSupport.findUserUIDOldXMl(task, 'responsible', p.getAllUsers())
                }
                if (u) {
                    t.responsible = u
                } else {
                    t.responsible = p.productOwners.first()
                }
            }
            return t
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }
    }
}