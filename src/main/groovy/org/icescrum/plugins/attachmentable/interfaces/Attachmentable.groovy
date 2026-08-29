/*
 * Copyright (c) 2010 iceScrum Technologies.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vincent.barrier@icescrum.com)
 *
 */

package org.icescrum.plugins.attachmentable.interfaces

import grails.util.Holders
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.icescrum.plugins.attachmentable.domain.AttachmentLink

/**
 * Trait used to specify a domain that can have attachments.
 *
 * Formerly a marker interface: the methods below were injected dynamically by
 * the Grails 2 IcescrumAttachmentableGrailsPlugin descriptor (doWithDynamicMethods)
 * and have been converted into trait methods with the same names and semantics.
 */
trait Attachmentable {

    def addAttachment(poster, def file, String originalName = null) {
        return attachmentableService.addAttachment(poster, this, file, originalName)
    }

    def addAttachments(poster, def tmpFiles) {
        tmpFiles.each { tmpFile ->
            if (tmpFile instanceof File) {
                addAttachment(poster, tmpFile)
            } else {
                addAttachment(poster, tmpFile.url ? tmpFile : tmpFile.file, tmpFile.filename)
            }
        }
    }

    def removeAttachment(Attachment attachment) {
        attachmentableService.removeAttachment(attachment, this)
    }

    def removeAttachment(Long id) {
        def attachment = Attachment.load(id)
        if (attachment) {
            removeAttachment(attachment)
        }
    }

    def removeAllAttachments() {
        def delDir = this.attachments?.findAll { it.url != null }?.size() > 0 ?: false
        this.attachments?.each { Attachment a ->
            removeAttachment(a)
        }
        if (delDir) {
            attachmentableService.removeAttachmentDir(this)
        }
    }

    def getAttachments() {
        AttachmentLink.getAttachments(this).list()
    }

    def getTotalAttachments() {
        AttachmentLink.getTotalAttachments(this).list()[0]
    }

    private getAttachmentableService() {
        Holders.applicationContext.attachmentableService
    }
}
