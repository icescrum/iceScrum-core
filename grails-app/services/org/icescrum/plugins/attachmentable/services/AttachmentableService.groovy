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

package org.icescrum.plugins.attachmentable.services

import grails.gorm.transactions.Transactional
import grails.util.GrailsNameUtils
import grails.util.Holders
import org.apache.commons.io.FileUtils
import org.apache.commons.io.FilenameUtils
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.icescrum.plugins.attachmentable.domain.AttachmentLink

@Transactional // Grails 7: services are no longer implicitly transactional
class AttachmentableService {

    Attachment addAttachment(def poster, def attachmentable, def file, def originalName = null) {

        if (attachmentable.id == null) {
            throw new RuntimeException("You must save the entity [${attachmentable}] before calling addAttachment")
        }

        def posterClass = poster.class.name
        def i = posterClass.indexOf('_$$_javassist')
        if (i > -1) {
            posterClass = posterClass[0..i - 1]
        }

        if (file instanceof File && !file?.length()) {
            throw new RuntimeException("Error file : ${file.getName()} is empty (${file.getAbsolutePath()})")
        }

        String filename = originalName ?: file.name

        def attachment = new Attachment(posterId: poster.id,
                posterClass: posterClass,
                inputName: originalName ?: file.name,
                name: FilenameUtils.getBaseName(filename),
                ext: FilenameUtils.getExtension(filename),
                length: file instanceof File ? file.length() : file.length,
                url: file instanceof Map ? file.url : null,
                provider: file instanceof Map ? file.provider : null,
                contentType: file instanceof File ? Holders.servletContext.getMimeType(filename.toLowerCase()) : null)

        if (!attachment.validate()) {
            throw new RuntimeException("Cannot create attachment for arguments [$poster, $file], they are invalid.")
        }
        attachment.save()

        def attachmentableClass = attachmentable.class.name
        i = attachmentableClass.indexOf('_$$_javassist')
        if (i > -1) {
            attachmentableClass = attachmentableClass[0..i - 1]
        }

        def link = new AttachmentLink(attachment: attachment, attachmentRef: attachmentable.id, type: GrailsNameUtils.getPropertyName(attachmentable.class), attachmentRefClass: attachmentableClass)
        link.save()

        if (file instanceof File) {
            // Save the file on disk
            def diskFile = new File(getFileDir(attachmentable), "${attachment.id + (attachment.ext ? '.' + attachment.ext : '')}")
            FileUtils.moveFile(file, diskFile)
            try {
                attachmentable.onAddAttachment(attachment)
            } catch (MissingMethodException e) {}
        }

        if (attachmentable.hasProperty('attachments_count')) {
            attachmentable.attachments_count = attachmentable.getTotalAttachments()
        }

        return attachment
    }

    def removeAttachment(Attachment attachment, def attachmentable) {
        if (!attachment.url) {
            def diskFile = new File(getFileDir(attachmentable), "${attachment.id + (attachment.ext ? '.' + attachment.ext : '')}")
            diskFile.delete()
            try {
                attachmentable.onRemoveAttachment(attachment)
            } catch (MissingMethodException e) {}
        }
        AttachmentLink.findAllByAttachment(attachment)*.delete()
        attachment.delete(flush: true)
        if (attachmentable.hasProperty('attachments_count')) {
            attachmentable.attachments_count = attachmentable.getTotalAttachments()
        }
        return attachmentable
    }

    def removeAttachmentDir(def attachmentable) {
        getFileDir(attachmentable).deleteDir()
        return attachmentable
    }

    private getFileDir(def attachmentable) {
        def config = Holders.config
        def dir = config.getProperty('grails.attachmentable.baseDir', String)
        def customDir = config.getProperty("grails.attachmentable.${GrailsNameUtils.getShortName(attachmentable.class).toLowerCase()}Dir", Closure, null)
        if (customDir) {
            dir = "${dir}${customDir(attachmentable)}"
        }
        def fileDir = new File(dir)
        fileDir.mkdirs()
        return fileDir
    }

    def getFile(def attachment) {
        def link = AttachmentLink.findByAttachment(attachment)
        def attachmentable = getClass().classLoader.loadClass(link.attachmentRefClass).get(link.attachmentRef)
        def diskFile = new File(getFileDir(attachmentable), "${attachment.id + (attachment.ext ? '.' + attachment.ext : '')}")
        if (!diskFile) {
            throw new FileNotFoundException()
        }
        return diskFile
    }
}
