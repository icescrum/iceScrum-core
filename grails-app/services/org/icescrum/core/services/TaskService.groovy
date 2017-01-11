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

import grails.transaction.Transactional
import grails.util.GrailsNameUtils
import grails.validation.ValidationException
import org.grails.comments.Comment
import org.grails.comments.CommentLink
import org.icescrum.core.domain.*
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.springframework.context.ApplicationContext
import org.springframework.security.access.prepost.PreAuthorize

@Transactional
class TaskService extends IceScrumEventPublisher {

    def clicheService
    def springSecurityService
    def securityService
    def activityService
    def grailsApplication
    def attachmentableService

    @PreAuthorize('(inProject(#task.backlog?.parentProject) or inProject(#task.parentStory?.backlog)) and (!archivedProject(#task.backlog?.parentProject) or !archivedProject(#task.parentStory?.backlog))')
    void save(Task task, User user) {
        if (task.parentStory?.parentSprint && !task.backlog) {
            task.backlog = task.parentStory.parentSprint
        }
        Sprint sprint = task.sprint
        if (!task.id && sprint?.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.task.error.not.saved')
        }
        if (task.estimation == 0f && task.state != Task.STATE_DONE) {
            task.estimation = null
        }
        Project project = sprint ? sprint.parentProject : (Project) task.parentStory.backlog
        if (project.preferences.assignOnCreateTask) {
            task.responsible = user
        }
        task.parentProject = project
        task.creator = user
        if (task.parentStory) {
            task.rank = Task.countByParentStoryAndState(task.parentStory, task.state) + 1
        } else {
            task.rank = Task.countByBacklogAndTypeAndState(task.backlog, task.type, task.state) + 1
        }
        task.uid = Task.findNextUId(project.id)
        task.save(flush: true)
        if (sprint) {
            clicheService.createOrUpdateDailyTasksCliche(sprint)
        }
        publishSynchronousEvent(IceScrumEventType.CREATE, task)
    }

    @PreAuthorize('inProject(#task.parentProject) and !archivedProject(#task.parentProject)')
    void update(Task task, User user, boolean force = false, props = [:]) {
        if (props.state != null) {
            state(task, props.state, user)
        }
        def sprint = task.sprint
        if (sprint?.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.sprint.error.state.not.inProgress')
        }
        Project project = task.parentProject
        if (task.type == Task.TYPE_URGENT
                && task.state == Task.STATE_BUSY
                && project.preferences.limitUrgentTasks != 0
                && sprint.tasks?.findAll { it.type == Task.TYPE_URGENT && it.state == Task.STATE_BUSY && it.id != task.id }?.size() >= project.preferences.limitUrgentTasks) {
            throw new BusinessException(code: 'is.task.error.limitTasksUrgent', args: [project.preferences.limitUrgentTasks])
        }
        if (task.state != Task.STATE_DONE || !task.doneDate) {
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
                        throw new BusinessException(code: 'is.story.error.done')
                    }
                    if (task.estimation == 0f) {
                        task.estimation = null
                    }
                    task.doneDate = null
                } else if (task.estimation == 0f && sprint.state == Sprint.STATE_INPROGRESS) {
                    if (project.preferences.assignOnBeginTask && !task.responsible) {
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
            throw new BusinessException(code: 'is.task.error.done')
        }
        if (task.isDirty('state') || task.isDirty('parentStory') || task.isDirty('type')) {
            if (props.rank == null) {
                def container = task.parentStory ?: task.backlog
                def sameStateAndTypeTasks = container.tasks.findAll { it.state == task.state && it.type == task.type && it.id != task.id }
                props.rank = sameStateAndTypeTasks ? sameStateAndTypeTasks.size() + 1 : 1
            }
            resetRank(task)
            setRank(task, props.rank)
        } else if (props.rank != null) {
            updateRank(task, props.rank)
        }
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, task)
        task.save()
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
        if (story && task.parentProject.preferences.autoDoneStory && !story.tasks.any { it.state != Task.STATE_DONE } && story.state != Story.STATE_DONE) {
            ApplicationContext ctx = (ApplicationContext) grailsApplication.mainContext
            StoryService service = (StoryService) ctx.getBean("storyService")
            service.done(story)
        }
        if (user) {
            activityService.addActivity(task, user, 'taskFinish', task.name)
        }
    }

    @PreAuthorize('inProject(#task.parentProject) and !archivedProject(#task.parentProject)')
    void delete(Task task, User user) {
        def sprint = task.sprint
        boolean scrumMaster = securityService.scrumMaster(null, springSecurityService.authentication)
        boolean productOwner = securityService.productOwner(task.parentProject, springSecurityService.authentication)
        if (task.state == Task.STATE_DONE && !scrumMaster && !productOwner) {
            throw new BusinessException(code: 'is.task.error.delete.not.scrumMaster')
        }
        if (sprint && sprint.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.task.error.delete.sprint.done')
        }
        if (task.responsible && task.responsible.id.equals(user.id) || task.creator.id.equals(user.id) || productOwner || scrumMaster) {
            def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, task)
            resetRank(task)
            if (task.parentStory) {
                dirtyProperties.parentStory = task.parentStory
                activityService.addActivity(task.parentStory, user, 'taskDelete', task.name)
                task.parentStory.removeFromTasks(task)
            }
            if (sprint) {
                dirtyProperties.backlog = sprint
                sprint.removeFromTasks(task)
                sprint.save()
                clicheService.createOrUpdateDailyTasksCliche(sprint)
            }
            task.delete()
            publishSynchronousEvent(IceScrumEventType.DELETE, task, dirtyProperties)
        }
    }

    @PreAuthorize('isAuthenticated() and !archivedProject(#task.parentProject)')
    def makeStory(Task task) {
        Story story = new Story()
        ['name', 'description', 'notes'].each { property ->
            story[property] = task[property]
        }
        Project project = task.parentProject
        story.backlog = project // Duplicate with what is done in StoryService but required to allow unique name validation
        story.validate()
        def i = 1
        while (story.hasErrors() && story.errors.getFieldError('name')) {
            if (story.errors.getFieldError('name')?.defaultMessage?.contains("unique")) {
                i += 1
                story.name = story.name + '_' + i
                story.validate()
            } else if (task.errors.getFieldError('name')?.defaultMessage?.contains("maximum size")) {
                story.name = story.name[0..20]
                story.validate()
            } else {
                throw new ValidationException('Validation Error(s) occurred during save()', story.errors)
            }
        }
        grailsApplication.mainContext.getBean("storyService").save(story, project, springSecurityService.currentUser)
        task.attachments.each { attachment ->
            story.addAttachment(task.creator, attachmentableService.getFile(attachment), attachment.filename)
        }
        task.comments.each { Comment comment ->
            def commentLink = CommentLink.findByComment(comment)
            if (commentLink) {
                commentLink.commentRef = story.id
                commentLink.type = GrailsNameUtils.getPropertyName(story.class)
                commentLink.save()
            }
        }
        story.tags = task.tags
        delete(task, springSecurityService.currentUser)
        return story
    }

    @PreAuthorize('inProject(#task.parentProject) and !archivedProject(#task.parentProject)')
    def copy(Task task, User user, def clonedState = Task.STATE_WAIT) {
        if (task.sprint?.state == Sprint.STATE_DONE) {
            throw new BusinessException(code: 'is.task.error.copy.done')
        }
        def clonedTask = new Task(
                name: task.name + '_1',
                state: clonedState,
                creator: user,
                color: task.color,
                description: task.description,
                notes: task.notes,
                dateCreated: new Date(),
                backlog: task.parentStory ? task.parentStory.parentSprint : task.backlog,
                parentStory: task.parentStory ?: null,
                parentProject: task.parentProject,
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
                throw new ValidationException('Validation Error(s) occurred during save()', clonedTask.errors)
            }
        }
        save(clonedTask, user)
        if (task.sprint) {
            clicheService.createOrUpdateDailyTasksCliche(task.sprint)
        }
        return clonedTask
    }

    private void state(Task task, Integer newState, User user) {
        def project = task.parentProject
        if (task.sprint?.state != Sprint.STATE_INPROGRESS && newState >= Task.STATE_BUSY) {
            throw new BusinessException(code: 'is.sprint.error.state.not.inProgress')
        }
        if (task.state == Task.STATE_DONE && task.doneDate && newState == Task.STATE_DONE) {
            def story = task.type ? null : Story.get(task.parentStory?.id)
            if (story && story.state == Story.STATE_DONE) {
                throw new BusinessException(code: 'is.story.error.done')
            }
            task.doneDate = null
        } else {
            if (task.responsible == null && project.preferences.assignOnBeginTask && newState >= Task.STATE_BUSY) {
                task.responsible = user
            }
            if ((task.responsible && user.id.equals(task.responsible.id))
                    || user.id.equals(task.creator.id)
                    || securityService.productOwner(project, springSecurityService.authentication)
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
        def container = task.getPersistentValue('parentStory') ?: task.backlog
        container.tasks.findAll {
            it.rank > task.rank && it.type == task.getPersistentValue('type') && it.state == task.getPersistentValue('state')
        }.each {
            it.rank--
            it.save()
        }
    }

    private void setRank(Task task, int newRank) {
        def container = task.parentStory ?: task.backlog
        container.tasks.findAll {
            it.rank >= newRank && it.type == task.type && it.state == task.state
        }.each {
            it.rank++
            it.save()
        }
        task.rank = newRank
    }

    private void updateRank(Task task, int newRank) {
        def container = task.parentStory ?: task.backlog
        Range affectedRange = task.rank..newRank
        int delta = affectedRange.isReverse() ? 1 : -1
        container.tasks.findAll {
            it != task && it.rank in affectedRange && it.type == task.type && it.state == task.state
        }.each {
            it.rank += delta
            it.save()
        }
        task.rank = newRank
    }

    def unMarshall(def taskXml, def options) {
        Project project = options.project
        Sprint sprint = options.sprint
        Story story = options.story
        Task.withTransaction(readOnly: options.save) { transaction ->
            try {
                def inProgressDate = null
                if (taskXml.inProgressDate?.text() && taskXml.inProgressDate?.text() != "") {
                    inProgressDate = ApplicationSupport.parseDate(taskXml.inProgressDate.text())
                }
                def doneDate = null
                if (taskXml.doneDate?.text() && taskXml.doneDate?.text() != "") {
                    doneDate = ApplicationSupport.parseDate(taskXml.doneDate.text())
                }
                def todoDate = null
                if (taskXml.todoDate?.text() && taskXml.todoDate?.text() != "") {
                    todoDate = ApplicationSupport.parseDate(taskXml.todoDate.text())
                } else if (sprint || story) {
                    todoDate = sprint?.todoDate ?: story.todoDate
                }
                def task = new Task(
                        type: (taskXml.type.text().isNumber()) ? taskXml.type.text().toInteger() : null,
                        description: taskXml.description.text() ?: null,
                        notes: taskXml.notes.text() ?: null,
                        estimation: (taskXml.estimation.text().isNumber()) ? taskXml.estimation.text().toFloat() : null,
                        initial: (taskXml.initial.text().isNumber()) ? taskXml.initial.text().toFloat() : null,
                        rank: taskXml.rank.text().toInteger(),
                        name: taskXml."${'name'}".text(),
                        todoDate: todoDate,
                        inProgressDate: inProgressDate,
                        doneDate: doneDate,
                        state: taskXml.state.text().toInteger(),
                        blocked: taskXml.blocked.text()?.toBoolean() ?: false,
                        uid: taskXml.@uid.text()?.isEmpty() ? taskXml.@id.text().toInteger() : taskXml.@uid.text().toInteger(),
                        color: taskXml?.color?.text() ?: "yellow")
                if (project) {
                    def u = ((User) project.getAllUsers().find { it.uid == taskXml.creator.@uid.text() }) ?: null
                    task.creator = u ?: (User) project.productOwners.first()
                    project.addToTasks(task)

                    if ((!taskXml.responsible?.@uid?.isEmpty() || !taskXml.responsible?.@id?.isEmpty()) && project) {
                        u = ((User) project.getAllUsers().find { it.uid == taskXml.responsible.@uid.text() }) ?: null
                        task.responsible = u ?: (User) project.productOwners.first()
                    }
                }

                if (sprint) {
                    sprint.addToTasks(task)
                }
                if (story) {
                    story.addToTasks(task)
                }

                // Save before some hibernate stuff
                if (options.save) {
                    task.save()

                    //Handle tags
                    if(taskXml.tags.text()){
                        task.tags = taskXml.tags.text().replaceAll(' ', '').replace('[', '').replace(']', '').split(',')
                    }

                    if(project){
                        taskXml.comments.comment.each{ _commentXml ->
                            def uid = options.IDUIDUserMatch?."${_commentXml.posterId.text().toInteger()}"?:null
                            User user = (User)project.getAllUsers().find{ it.uid == uid }?: (User)springSecurityService.currentUser
                            ApplicationSupport.importComment(task, user, _commentXml.body.text(), ApplicationSupport.parseDate(_commentXml.dateCreated.text()))
                        }
                        taskXml.attachments.attachment.each { _attachmentXml ->
                            def uid = options.IDUIDUserMatch?."${_attachmentXml.posterId.text().toInteger()}"?:null
                            User user = (User)project.getAllUsers().find{ it.uid == uid }?: (User)springSecurityService.currentUser
                            ApplicationSupport.importAttachment(task, user, options.path, _attachmentXml)
                        }
                    }
                }

                // Child objects
                options.task = task

                options.parent = task
                taskXml.activities?.activity?.each{ def activityXml ->
                    activityService.unMarshall(activityXml, options)
                }
                options.parent = null

                if (options.save) {
                    task.save()
                }
                options.task = null
                return (Task) importDomainsPlugins(taskXml, task, options)
            } catch (Exception e) {
                if (log.debugEnabled) e.printStackTrace()
                throw new RuntimeException(e)
            }
        }
    }
}
