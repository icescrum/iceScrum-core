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
 * Nicolas Noullet (nnoullet@kagilum.com)
 */

package org.icescrum.core.services

import java.text.SimpleDateFormat
import org.codehaus.groovy.grails.commons.ApplicationHolder
import org.icescrum.core.event.IceScrumTaskEvent
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.transaction.annotation.Transactional
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport

class TaskService {


    static transactional = true

    def clicheService
    def springSecurityService
    def securityService

    private boolean checkEstimation(Task task) {
        // Check if the estimation is numeric
        if (task.estimation) {
            try {
                task.estimation = Float.valueOf(task.estimation)
            } catch (NumberFormatException e) {
                throw new RuntimeException('is.task.error.estimation.number')
            }
        }

        if (task.estimation != null && task.estimation < 0)
            throw new IllegalStateException('is.task.error.negative.estimation')

        return true
    }

    @PreAuthorize('inProduct(#sprint.parentProduct) and !archivedProduct(#sprint.parentProduct)')
    void save(Task task, Sprint sprint, User user) {

        if (!task.id && sprint.state == Sprint.STATE_DONE){
            throw new IllegalStateException('is.task.error.not.saved')
        }

        checkEstimation(task)

        // If the estimation is equals to zero, drop it
        if (task.estimation == 0 && task.state != Task.STATE_DONE)
            task.estimation = null

        task.creator = user
        task.backlog = sprint
        task.rank = Task.countByParentStoryAndType(task.parentStory, task.type) + 1
        task.uid = Task.findNextUId(task.backlog.parentRelease.parentProduct.id)

        if (!task.save(flush:true)) {
            throw new RuntimeException()
        }
        clicheService.createOrUpdateDailyTasksCliche((Sprint) task.backlog)

        task.addActivity(user, 'taskSave', task.name)
        publishEvent(new IceScrumTaskEvent(task, this.class, user, IceScrumTaskEvent.EVENT_CREATED))
        broadcast(function: 'add', message: task)
    }

    @PreAuthorize('inProduct(#story.backlog) and !archivedProduct(#story.backlog)')
    void saveStoryTask(Task task, Story story, User user) {
        story.addToTasks(task)
        def currentProduct = (Product) story.backlog
        if (currentProduct.preferences.assignOnCreateTask) {
            task.responsible = user
        }
        save(task, story.parentSprint, user)
    }

    @PreAuthorize('inProduct(#sprint.parentProduct) and !archivedProduct(#sprint.parentProduct)')
    void saveRecurrentTask(Task task, Sprint sprint, User user) {
        task.type = Task.TYPE_RECURRENT
        def currentProduct = (Product) sprint.parentRelease.parentProduct
        if (currentProduct.preferences.assignOnCreateTask) {
            task.responsible = user
        }
        save(task, sprint, user)
    }

    @PreAuthorize('inProduct(#sprint.parentProduct) and !archivedProduct(#sprint.parentProduct)')
    void saveUrgentTask(Task task, Sprint sprint, User user) {
        task.type = Task.TYPE_URGENT
        def currentProduct = (Product) sprint.parentRelease.parentProduct
        if (currentProduct.preferences.assignOnCreateTask) {
            task.responsible = user
        }
        save(task, sprint, user)
    }

    /**
     * An update with a Task changing its parentStory
     * @param task
     * @param user
     * @param story
     */
    @PreAuthorize('inProduct(#story.backlog) and !archivedProduct(#story.backlog)')
    void changeTaskStory(Task task, Story story, User user) {
        if (task.parentStory.id != story.id) {
            task.parentStory = story
            update(task, user)
        }
    }

    /**
     * An update with a Task changing its type (URGENT/RECURRENT)
     * @param task
     * @param user
     * @param type
     */
    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void changeType(Task task, int type, User user) {
        task.type = type
        update(task, user)
    }

    /**
     * Transforms a Sprint Task into a Story Task
     * @param task
     * @param story
     * @param user
     */
    @PreAuthorize('inProduct(#story.backlog) and !archivedProduct(#story.backlog)')
    void sprintTaskToStoryTask(Task task, Story story, User user) {
        task.type = null
        update(task, user)
        task.parentStory = story
    }
    /**
     * Transforms a Story Task into a Sprint Task
     * @param task
     * @param type
     * @param user
     */
    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void storyTaskToSprintTask(Task task, int type, User user) {
        def story = task.parentStory
        task.parentStory = null
        task.type = type
        update(task, user)
        story.addActivity(user, (type == Task.TYPE_URGENT ? 'taskAssociateUrgent' : 'taskAssociateRecurrent'), task.name)
    }

    /**
     * Update a Task
     * @param task
     * @param user
     */
    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void update(Task task, User user, boolean force = false) {
        def sprint = (Sprint) task.backlog

        if(sprint.state == Sprint.STATE_DONE){
            throw new IllegalStateException('is.sprint.error.state.not.inProgress')
        }

        if (!(task.state == Task.STATE_DONE && task.doneDate)) {
            checkEstimation(task)
            def p = (Product) sprint.parentRelease.parentProduct
            // TODO add check : if SM or PO, always allow
            if (force || (task.responsible && task.responsible.id.equals(user.id)) || task.creator.id.equals(user.id) || securityService.scrumMaster(null, springSecurityService.authentication)) {
                if (task.estimation == 0 && task.state != Task.STATE_DONE && sprint.state == Sprint.STATE_INPROGRESS) {
                    if(p.preferences.assignOnBeginTask)
                        task.responsible = task.responsible ? task.responsible : user;
                    task.state = Task.STATE_DONE
                    task.doneDate = new Date()
                    if (user)
                        task.addActivity(user, 'taskFinish', task.name)
                    publishEvent(new IceScrumTaskEvent(task, this.class, user, IceScrumTaskEvent.EVENT_STATE_DONE))
                } else if (task.state == Task.STATE_DONE) {
                    task.estimation = 0
                    task.blocked = false
                    task.doneDate = new Date()
                }

                if (task.state >= Task.STATE_BUSY && !task.inProgressDate) {
                    task.inProgressDate = new Date()
                    if (!task.isDirty('blocked'))
                        task.blocked = false
                    else {
                        if (task.blocked) {
                            publishEvent(new IceScrumTaskEvent(task, this.class, user, IceScrumTaskEvent.EVENT_STATE_BLOCKED))
                        }
                    }
                }

                if (task.state < Task.STATE_BUSY && task.inProgressDate)
                    task.inProgressDate = null

                if (!task.type && p.preferences.autoDoneStory && task.state == Task.STATE_DONE) {
                    ApplicationContext ctx = (ApplicationContext) ApplicationHolder.getApplication().getMainContext();
                    StoryService service = (StoryService) ctx.getBean("storyService");

                    Story s = Story.get(task.parentStory.id)
                    if (!s.tasks.any { it.state != Task.STATE_DONE } && s.state != Story.STATE_DONE) {
                        service.done(s)
                    }
                }
            }
        }else{
            throw new IllegalStateException('is.task.error.done')
        }
        if (!task.save(flush: true)) {
            throw new RuntimeException()
        }

        clicheService.createOrUpdateDailyTasksCliche(sprint)
        if (task.state != Task.STATE_DONE) {
            publishEvent(new IceScrumTaskEvent(task, this.class, user, IceScrumTaskEvent.EVENT_UPDATED))
        }

        broadcast(function: 'update', message: task)
    }

    /**
     * Assign a collection of task to a peculiar user
     * @param tasks
     * @param user
     * @param p
     * @return
     */
    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    boolean assign(Task task, User user) {
        if (task.state == Task.STATE_DONE){
            throw new IllegalStateException('is.task.error.done')
        }
        task.responsible = user
        update(task, user)
        return true
    }

    /**
     * Unassign a collection of task to a peculiar user
     * @param tasks
     * @param user
     * @param p
     * @return
     */
    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    boolean unassign(Task task, User user) {
        if (task.responsible?.id != user.id)
            throw new IllegalStateException('is.task.error.unassign.not.responsible')
        if (task.state == Task.STATE_DONE)
            throw new IllegalStateException('is.task.error.done')
        task.responsible = null
        task.state = Task.STATE_WAIT
        update(task, user, true)
        return true
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void delete(Task task, User user) {
        def p = ((Sprint) task.backlog).parentRelease.parentProduct
        boolean scrumMaster = securityService.scrumMaster(null, springSecurityService.authentication)
        boolean productOwner = securityService.productOwner(p, springSecurityService.authentication)
        if (task.state == Task.STATE_DONE && !scrumMaster && !productOwner) {
            throw new IllegalStateException('is.task.error.delete.not.scrumMaster')
        }
        if (task.responsible && task.responsible.id.equals(user.id) || task.creator.id.equals(user.id) || productOwner || scrumMaster) {
            task.removeAllAttachments()
            Sprint sprint = (Sprint)task.backlog
            if (task.parentStory) {
                task.parentStory.addActivity(user, 'taskDelete', task.name)
                task.parentStory.removeFromTasks(task)
            }
            resetRank(task)
            sprint.removeFromTasks(task)
            clicheService.createOrUpdateDailyTasksCliche((Sprint) sprint)
            broadcast(function: 'delete', message: [class: task.class, id: task.id])
        }
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    def copy(Task task, User user, def clonedState = Task.STATE_WAIT) {
        if (task.backlog.state == Sprint.STATE_DONE) {
            throw new IllegalStateException('is.task.error.copy.done')
        }

        def clonedTask = new Task(
                name: task.name + '_1',
                rank: Task.countByParentStoryAndType(task.parentStory, task.type) + 1,
                state: clonedState,
                creator: user,
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
            if (clonedTask.errors.getFieldError('name')) {
                i += 1
                clonedTask.name = task.name + '_' + i
                clonedTask.validate()
            } else {
                throw new RuntimeException()
            }
        }

        save(clonedTask, (Sprint) task.backlog, user)
        clicheService.createOrUpdateDailyTasksCliche((Sprint) task.backlog)
        return clonedTask
    }

    /**
     * Change the state of a task
     * @param t
     * @param u
     * @param i
     * @return
     */
    @PreAuthorize('inProduct(#t.parentProduct) and !archivedProduct(#t.parentProduct)')
    void state(Task t, Integer state, User u) {

        def p = ((Sprint) t.backlog).parentRelease.parentProduct

        if(((Sprint)t.backlog).state != Sprint.STATE_INPROGRESS && state >= Task.STATE_BUSY){
            throw new IllegalStateException('is.sprint.error.state.not.inProgress')
        }

        if (t.state == Task.STATE_DONE){
            throw new IllegalStateException('is.task.error.done')
        }

        if (t.responsible == null && p.preferences.assignOnBeginTask && state >= Task.STATE_BUSY) {
            t.responsible = u
        }

        if (t.type == Task.TYPE_URGENT
                && state == Task.STATE_BUSY
                && t.state != Task.STATE_BUSY
                && p.preferences.limitUrgentTasks != 0
                && p.preferences.limitUrgentTasks == ((Sprint) t.backlog).tasks?.findAll {it.type == Task.TYPE_URGENT && it.state == Task.STATE_BUSY}?.size()) {
            throw new IllegalStateException('is.task.error.limitTasksUrgent')
        }

        if ((t.responsible && u.id.equals(t.responsible.id)) || u.id.equals(t.creator.id) || securityService.productOwner(p, springSecurityService.authentication) || securityService.scrumMaster(null, springSecurityService.authentication)) {
            if (state == Task.STATE_BUSY && t.state != Task.STATE_BUSY) {
                t.addActivity(u, 'taskInprogress', t.name)
                publishEvent(new IceScrumTaskEvent(t, this.class, u, IceScrumTaskEvent.EVENT_STATE_IN_PROGRESS))
            } else if (state == Task.STATE_WAIT && t.state != Task.STATE_WAIT) {
                t.addActivity(u, 'taskWait', t.name)
                publishEvent(new IceScrumTaskEvent(t, this.class, u, IceScrumTaskEvent.EVENT_STATE_WAIT))
            }
            t.state = state
            update(t, u)
        }
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void setRank(Task task, int rank) {
        def container = task.parentStory ?: task.backlog
        container.tasks.findAll {it.type == task.type && it.state == task.state}?.each { t ->
            if (t.rank >= rank) {
                t.rank++
                t.save()
            }
        }
        task.rank = rank
        if (!task.save())
            throw new RuntimeException()
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    void resetRank(Task task) {
        def container = task.parentStory ?: task.backlog
        container.tasks.findAll {it.type == task.type && it.state == task.state}.each { t ->
            if (t.rank > task.rank) {
                t.rank--
                t.save()
            }
        }
    }

    @PreAuthorize('inProduct(#task.parentProduct) and !archivedProduct(#task.parentProduct)')
    boolean rank(Task task, int rank) {
        def container
        if (task.parentStory) {
            container = task.parentStory
        } else {
            container = task.backlog
        }

        if (task.rank != rank) {
            if (task.rank > rank) {
                container.tasks.findAll {it.type == task.type && it.state == task.state}.each {it ->
                    if (it.rank >= rank && it.rank <= task.rank && it != task) {
                        it.rank = it.rank + 1
                        it.save()
                    }
                }
            } else {
                container.tasks.findAll {it.type == task.type && it.state == task.state}.each {it ->
                    if (it.rank <= rank && it.rank >= task.rank && it != task) {
                        it.rank = it.rank - 1
                        it.save()
                    }
                }
            }
            task.rank = rank
            return task.save() ? true : false
        } else {
            return false
        }
    }

    @Transactional(readOnly = true)
    def unMarshall(def task, Product p = null) {
        try {
            def inProgressDate = null
            if (task.inProgressDate?.text() && task.inProgressDate?.text() != "")
                inProgressDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(task.inProgressDate.text()) ?: null

            def doneDate = null
            if (task.doneDate?.text() && task.doneDate?.text() != "")
                doneDate = new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(task.doneDate.text()) ?: null

            def t = new Task(
                    type: (task.type.text().isNumber()) ? task.type.text().toInteger() : null,
                    description: task.description.text(),
                    notes: task.notes.text(),
                    estimation: (task.estimation.text().isNumber()) ? task.estimation.text().toFloat() : null,
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
                if (!task.creator?.@uid?.isEmpty())
                    u = ((User) p.getAllUsers().find { it.uid == task.creator.@uid.text() } ) ?: null
                else{
                    u = ApplicationSupport.findUserUIDOldXMl(task,'creator',p.getAllUsers())
                }
                if (u)
                    t.creator = u
                else
                    t.creator = p.productOwners.first()
            }

            if ((!task.responsible?.@uid?.isEmpty() || !task.responsible?.@id?.isEmpty()) && p) {
                def u
                if (!task.responsible?.@uid?.isEmpty())
                    u = ((User) p.getAllUsers().find { it.uid == task.responsible.@uid.text() } ) ?: null
                else{
                    u = ApplicationSupport.findUserUIDOldXMl(task,'responsible',p.getAllUsers())
                }
                if (u)
                    t.responsible = u
                else
                    t.responsible = p.productOwners.first()
            }
            return t
        } catch (Exception e) {
            if (log.debugEnabled) e.printStackTrace()
            throw new RuntimeException(e)
        }

    }
}