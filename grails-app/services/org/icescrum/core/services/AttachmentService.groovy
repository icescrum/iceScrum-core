/*
 * Copyright (c) 2020 Kagilum.
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
 *
 */

package org.icescrum.core.services


import grails.util.GrailsNameUtils
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.domain.Feature
import org.icescrum.core.domain.Portfolio
import org.icescrum.core.domain.Project
import org.icescrum.core.domain.Release
import org.icescrum.core.domain.Sprint
import org.icescrum.core.domain.Story
import org.icescrum.core.domain.Task
import org.icescrum.core.domain.User
import org.icescrum.core.error.BusinessException
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType
import org.icescrum.core.support.ApplicationSupport
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.icescrum.plugins.attachmentable.domain.AttachmentLink

class AttachmentService extends IceScrumEventPublisher {

    def grailsApplication

    Attachment save(Object attachmentable, User poster, props) {
        Attachment attachment
        if (props.filePath) {
            File attachmentFile = new File(props.filePath)
            if (!attachmentFile.length()) {
                throw new BusinessException(code: 'todo.is.ui.backlogelement.attachments.error.empty')
            }
            attachment = attachmentable.addAttachment(poster, attachmentFile, props.filename)
        } else {
            attachment = attachmentable.addAttachment(poster, props, props.name)
        }
        attachment.provider = props instanceof Map ? props.provider : null
        publishAttachmentableEvent(attachment, attachmentable, 'addedAttachment')
        publishSynchronousEvent(IceScrumEventType.CREATE, attachment)
        return attachment
    }

    void update(Attachment attachment, props) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_UPDATE, attachment)
        if (props.name && attachment.name != props.name) {
            attachment.name = props.name
            attachment.inputName = attachment.name
        }
        attachment.save(flush: true)
        def attachmentable = getAttachmentable(attachment)
        publishAttachmentableEvent(attachment, attachmentable, 'updatedAttachment')
        publishSynchronousEvent(IceScrumEventType.UPDATE, attachment, dirtyProperties)
    }

    void delete(Attachment attachment) {
        def dirtyProperties = publishSynchronousEvent(IceScrumEventType.BEFORE_DELETE, attachment)
        AttachmentLink attachmentLink = AttachmentLink.findByAttachment(attachment)
        dirtyProperties.attachmentable = [
                class: attachmentLink.type.capitalize(),
                id   : attachmentLink.attachmentRef
        ]
        dirtyProperties.workspace = getWorkspace(attachment)
        if (dirtyProperties.workspace instanceof Project) {
            dirtyProperties.project = dirtyProperties.workspace
        }
        def attachmentable = getAttachmentable(attachment)
        attachmentable.removeAttachment(attachment)
        publishAttachmentableEvent(attachment, attachmentable, 'removedAttachment')
        publishSynchronousEvent(IceScrumEventType.DELETE, attachment, dirtyProperties)
    }

    Attachment withAttachment(long workspaceId, String workspaceType, long id) {
        Attachment attachment = Attachment.get(id)
        if (!attachment) {
            throw new ObjectNotFoundException(id, 'Attachment')
        }
        AttachmentLink attachmentLink = AttachmentLink.findByAttachment(attachment)
        withAttachmentable(workspaceId, workspaceType, attachmentLink.attachmentRef, attachmentLink.type) // Important security check that the attachmenttable exists and in the right project
        return attachment
    }

    Object withAttachmentable(long workspaceId, String workspaceType, long attachmentableId, String type) {
        if (type == 'story') {
            return Story.withStory(workspaceId, attachmentableId)
        } else if (type == 'task') {
            return Task.withTask(workspaceId, attachmentableId)
        } else if (type == 'feature') {
            return Feature.withFeature(workspaceId, attachmentableId, workspaceType)
        } else if (type == 'release') {
            return Release.getInProject(workspaceId, attachmentableId).list()
        } else if (type == 'sprint') {
            return Sprint.getInProject(workspaceId, attachmentableId).list()
        } else if (type == 'project') {
            return Project.get(attachmentableId)
        } else if (type == 'portfolio') {
            return Portfolio.get(attachmentableId)
        } else {
            throw new ObjectNotFoundException(attachmentableId, type)
        }
    }

    def getWorkspace(Attachment attachment) {
        AttachmentLink attachmentLink = AttachmentLink.findByAttachment(attachment)
        if (attachmentLink.type == 'story') {
            return Story.get(attachmentLink.attachmentRef).backlog
        } else if (attachmentLink.type == 'task') {
            return Task.get(attachmentLink.attachmentRef).parentProject
        } else if (attachmentLink.type == 'feature') {
            Feature feature = Feature.get(attachmentLink.attachmentRef)
            return feature.backlog ?: feature.portfolio
        } else if (attachmentLink.type == 'sprint') {
            return Sprint.get(attachmentLink.attachmentRef).parentProject
        } else if (attachmentLink.type == 'release') {
            return Release.get(attachmentLink.attachmentRef).parentProject
        } else if (attachmentLink.type == 'project') {
            return Project.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'portfolio') {
            return Portfolio.get(attachmentLink.attachmentRef)
        } else {
            return null
        }
    }

    private Object getAttachmentable(Attachment attachment) {
        AttachmentLink attachmentLink = AttachmentLink.findByAttachment(attachment)
        if (attachmentLink.type == 'story') {
            return Story.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'task') {
            return Task.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'feature') {
            return Feature.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'sprint') {
            return Sprint.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'release') {
            return Release.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'project') {
            return Project.get(attachmentLink.attachmentRef)
        } else if (attachmentLink.type == 'portfolio') {
            return Portfolio.get(attachmentLink.attachmentRef)
        } else {
            return null
        }
    }

    Map getRenderableAttachment(Attachment attachment, Object attachmentable = null) {
        def attachmentLink = attachmentable ? [attachmentRef: attachmentable.id, type: getAttachmentableType(attachmentable)] : AttachmentLink.findByAttachment(attachment)
        return [
                class         : 'Attachment',
                id            : attachment.id,
                name          : attachment.name,
                ext           : attachment.ext,
                contentType   : attachment.contentType,
                length        : attachment.length,
                url           : attachment.url,
                provider      : attachment.provider,
                poster        : attachment.poster,
                inputName     : attachment.inputName,
                filename      : attachment.filename,
                previewable   : attachment.previewable,
                dateCreated   : attachment.dateCreated,
                attachmentable: [
                        class: attachmentLink.type.capitalize(),
                        id   : attachmentLink.attachmentRef
                ]
        ]
    }

    private void publishAttachmentableEvent(attachment, attachmentable, event) {
        grailsApplication.mainContext[getAttachmentableType(attachmentable) + 'Service'].publishSynchronousEvent(IceScrumEventType.UPDATE, attachmentable, [(event): attachment])
    }

    private String getAttachmentableType(Object attachmentable) {
        return ApplicationSupport.getUnproxiedClassName(GrailsNameUtils.getPropertyName(attachmentable.class))
    }

}
