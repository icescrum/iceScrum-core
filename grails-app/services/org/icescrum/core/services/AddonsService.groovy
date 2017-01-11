package org.icescrum.core.services

import grails.util.GrailsNameUtils
import org.grails.comments.Comment
import org.grails.comments.CommentLink
import org.icescrum.core.domain.*
import org.icescrum.core.support.ApplicationSupport

//TODO migrate to new event
class AddonsService {

    void synchronisedDataImport(def e) {
        Project p = (Project) e.source
        try {
            addAttachments(p, e.xml, e.importPath)
        } catch (Exception _e) {
            log.error("error when importing attachments")
            if (log.debugEnabled) {
                _e.printStackTrace()
            }
        }

        try {
            addComments(p, e.xml)
        } catch (Exception _e) {
            log.error("error when importing comments")
            if (log.debugEnabled) {
                _e.printStackTrace()
            }
        }
    }

    void addAttachments(Project p, def root, File importPath) {
        def defaultU = p.productOwners.first()
        def tasksCache = []
        def sprintsCache = []
        log.debug("start import files")
        root.'**'.findAll {
            it.name() in ["story", "task", "feature", "release", "sprint", "project"]
        }.each { element ->
            def elemt = null
            if (element.attachments.attachment.text()) {
                switch (element.name()) {
                    case 'story':
                        elemt = Story.findByBacklogAndUid(p, element.@uid.text().toInteger()) ?: null
                        break
                    case 'task':
                        tasksCache = tasksCache ?: Task.getAllInProject(p.id)
                        elemt = tasksCache?.find { it.uid == element.@uid.text().toInteger() } ?: null
                        break
                    case 'feature':
                        elemt = Feature.findByBacklogAndUid(p, element.@uid.text().toInteger()) ?: null
                        break
                    case 'release':
                        elemt = Release.findByParentProjectAndOrderNumber(p, element.orderNumber.text().toInteger()) ?: null
                        break
                    case 'sprint':
                        sprintsCache = sprintsCache ?: Release.findAllByParentProject(p)*.sprints.flatten()
                        elemt = sprintsCache.find {
                            it.orderNumber == element.orderNumber.text().toInteger() && it.startDate.format(('yyyy-MM-dd HH:mm:ss')) == element.startDate.text()
                        } ?: null
                        break
                    case 'project':
                        elemt = p
                        break
                }
            }
            if (elemt) {
                element.attachments.attachment.each { attachment ->
                    def u = root.'**'.find { it.id.text() == attachment.posterId.text() && it.@uid.text() }
                    if (u) {
                        u = User.findByUid(u.@uid.text())
                    } else if (defaultU) {
                        u = defaultU
                    }
                    def originalName = attachment.inputName.text()
                    if (!attachment.url?.text()) {
                        def path = "${importPath.absolutePath}${File.separator}attachments${File.separator}${attachment.@id.text()}.${attachment.ext.text()}"
                        def fileAttch = new File(path)
                        if (fileAttch.exists()) {
                            elemt.addAttachment(u, fileAttch, originalName)
                        }
                    } else {
                        elemt.addAttachment(u, [filename: originalName, url: attachment.url.text(), provider: attachment.provider.text(), size: attachment.length.toInteger()])
                    }
                }
            }
        }
        log.debug("end import files")
    }

    void addComments(Project p, def root) {
        def defaultU = p.productOwners.first()
        log.debug("start import comments")
        root.'**'.findAll { it.name() == "story" }.each { story ->
            def s = null
            if (story.comments.comment.text()) {
                s = Story.findByBacklogAndUid(p, story.@uid.text().toInteger()) ?: null
            }
            if (s) {
                story.comments.comment.each { comment ->
                    def u = root.'**'.find { it.id.text() == comment.posterId.text() && it.@uid.text() }
                    if (u) {
                        u = User.findByUid(u.@uid.text())
                    } else if (defaultU) {
                        u = defaultU
                    }
                    addComment(s,
                            u,
                            comment.body.text(),
                            ApplicationSupport.parseDate(comment.dateCreated.text()))
                }
            }
        }
        def tasksCache = []
        root.'**'.findAll { it.name() == "task" }.each { task ->
            def t = null
            if (task.comments.comment.text()) {
                tasksCache = tasksCache ?: Task.getAllInProject(p.id)
                t = tasksCache?.find { it.uid == task.@uid.text().toInteger() } ?: null
            }
            if (t) {
                task.comments.comment.each { comment ->
                    def u = root.'**'.find { it.id.text() == comment.posterId.text() && it.@uid.text() }
                    if (u) {
                        u = User.findByUid(u.@uid.text())
                    } else if (defaultU) {
                        u = defaultU
                    }
                    addComment(t,
                            u,
                            comment.body.text(),
                            ApplicationSupport.parseDate(comment.dateCreated.text()))
                }
            }
        }
        log.debug("end import comments")
    }

    private addComment(def object, User poster, String body, Date dateCreated) {
        def posterClass = poster.class.name
        def i = posterClass.indexOf('_$$_javassist')
        if (i > -1)
            posterClass = posterClass[0..i - 1]
        def c = new Comment(body: body, posterId: poster.id, posterClass: posterClass)
        c.save()
        def link = new CommentLink(comment: c, commentRef: object.id, type: GrailsNameUtils.getPropertyName(object.class))
        link.save()
        c.dateCreated = dateCreated
    }
}
