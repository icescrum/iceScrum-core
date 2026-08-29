/*
 * Copyright (c) 2026 iceScrum community.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * Replaces the download/preview controller actions that the Grails 2 plugin
 * descriptor injected through the metaclass: Grails 7 controller actions must
 * exist at compile time, so controllers now declare download()/preview()
 * actions that delegate to these helpers.
 */
package org.icescrum.core.support

import org.icescrum.plugins.attachmentable.domain.Attachment

import jakarta.servlet.http.HttpServletResponse

trait AttachmentDownloadSupport {

    def downloadAttachment() {
        Attachment attachment = Attachment.get(params.id as Long)
        if (attachment) {
            if (attachment.url) {
                redirect(url: "${attachment.url}")
                return
            } else {
                File file = attachmentableService.getFile(attachment)
                if (file.exists()) {
                    if (!attachment.previewable) {
                        String filename = attachment.filename
                        ['Content-disposition': "attachment;filename=\"$filename\"", 'Cache-Control': 'private', 'Pragma': ''].each { k, v ->
                            response.setHeader(k, v)
                        }
                    }
                    response.contentType = attachment.contentType
                    response.outputStream << file.newInputStream()
                    return
                }
            }
        }
        response.status = HttpServletResponse.SC_NOT_FOUND
    }

    def previewAttachment() {
        Attachment attachment = Attachment.get(params.id as Long)
        File file = attachmentableService.getFile(attachment)
        def thumbnail = new File(file.parentFile.absolutePath + File.separator + attachment.id + '-thumbnail.' + (attachment.ext?.toLowerCase() != 'gif' ? attachment.ext : 'jpg'))
        if (!thumbnail.exists()) {
            thumbnail.setBytes(hdImageService.scale(file.absolutePath, 40, 40))
        }
        if (thumbnail.exists()) {
            response.contentType = attachment.contentType
            response.outputStream << thumbnail.newInputStream()
        } else {
            render(status: 404)
        }
    }
}
