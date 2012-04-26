package org.icescrum.core.services

import org.icescrum.core.event.IceScrumProductEvent
import org.springframework.context.ApplicationListener
import org.icescrum.core.domain.Product
import org.icescrum.core.domain.User
import grails.plugin.fluxiable.Activity
import grails.plugin.fluxiable.ActivityException
import grails.plugin.fluxiable.ActivityLink
import grails.util.GrailsNameUtils
import java.text.SimpleDateFormat
import org.grails.comments.Comment
import org.grails.comments.CommentException
import org.grails.comments.CommentLink

class AddonsService implements ApplicationListener<IceScrumProductEvent> {

    @Override
    void onApplicationEvent(IceScrumProductEvent e) {
        if (e.type == IceScrumProductEvent.EVENT_IMPORTED){
            addComments((Product) e.source, e.xml)
            addActivities((Product) e.source, e.xml)
        }
    }
    void addComments(Product p, def root){
        def defaultU = p.productOwners.first()
        root.'**'.findAll{ it.name() == "story" }.each{ story ->
            def s = null
            if (story.comments.comment.text()){
                s = p.stories?.find { it.uid == story.@uid.text().toInteger() } ?: null
            }
            if (s){
                story.comments.comment.each{ comment ->
                    def u = root.'**'.find{ it.id.text() == comment.posterId.text() &&  it.@uid.text() }
                    if(u){
                       u = User.findByUid(u.@uid.text())
                    }else if(defaultU){
                       u = defaultU
                    }
                    addComment(s,
                        u,
                        comment.body.text(),
                        new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(comment.dateCreated.text()))
                }
            }
        }
    }

    void addActivities(Product p, def root){
        def defaultU = p.productOwners.first()
        root.'**'.findAll{ it.name() == "story" }.each{ story ->
            def s = null
            if (story.activities.activity.text()){
                s = p.stories?.find { it.uid == story.@uid.text().toInteger() } ?: null
            }
            if (s){
                story.activities.activity.each{ activity ->
                    def u = root.'**'.find{ it.id.text() == activity.posterId.text() &&  it.@uid.text() }
                    if(u){
                        u = User.findByUid(u.@uid.text())
                    }else if(defaultU){
                        u = defaultU
                    }
                    addActivity(s,
                        u,
                        activity.code.text(),
                        activity.cachedLabel.text(),
                        new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(activity.dateCreated.text()),
                        activity.cachedDescription.text())
                }
            }
        }
        root.activities.activity.each{ activity ->
            def u = root.'**'.find{ it.id.text() == activity.posterId.text() &&  it.@uid.text() }
            if(u){
                u = User.findByUid(u.@uid.text())
            }else{
                u = defaultU
            }
            addActivity(p,
                        u,
                        activity.code.text(),
                        activity.cachedLabel.text(),
                        new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(activity.dateCreated.text()),
                        activity.cachedDescription.text())
        }
    }

    private addActivity(def object, User poster, String code, String label, Date dateCreated, String desc){
        def posterClass = poster.class.name
        def i = posterClass.indexOf('_$$_javassist')
        if (i > -1)
          posterClass = posterClass[0..i - 1]

        def c = new Activity(code: code,
                                cachedLabel: label,
                                posterId: poster.id,
                                posterClass: posterClass,
                                cachedId: object.id,
                                cachedDescription: desc)
        if (!c.validate()) {
          throw new ActivityException("Cannot create activity for arguments [$poster, $code, $label], they are invalid.")
        }
        c.save()
        def delegateClass = object.class.name
        i = delegateClass.indexOf('_$$_javassist')
        if (i > -1) delegateClass = delegateClass[0..i - 1]
        def link = new ActivityLink(activity: c, activityRef: object.id, type: GrailsNameUtils.getPropertyName(delegateClass))
        link.save()
        c.dateCreated = dateCreated
    }

    private addComment(def object, User poster, String body, Date dateCreated){
        def posterClass = poster.class.name
        def i = posterClass.indexOf('_$$_javassist')
        if(i>-1)
            posterClass = posterClass[0..i-1]
        def c = new Comment(body:body, posterId:poster.id, posterClass:posterClass)
        if(!c.validate()) {
            throw new CommentException("Cannot create comment for arguments [$poster, $body], they are invalid.")
        }
        c.save()
        def link = new CommentLink(comment:c, commentRef:object.id, type:GrailsNameUtils.getPropertyName(object.class))
        link.save()
        c.dateCreated = dateCreated
    }
}
