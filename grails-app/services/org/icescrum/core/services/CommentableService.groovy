package org.icescrum.core.services

import org.icescrum.core.event.IceScrumProductEvent
import org.springframework.context.ApplicationListener
import org.icescrum.core.domain.Product
import groovy.util.slurpersupport.NodeChild
import org.icescrum.core.domain.User

class CommentableService implements ApplicationListener<IceScrumProductEvent> {

    static transactional = true

    def serviceMethod() {

    }

    @Override
    void onApplicationEvent(IceScrumProductEvent e) {
        if (e.type == IceScrumProductEvent.EVENT_IMPORTED){
            addComments((Product) e.source, (NodeChild) e.xml)
        }
    }

    void addComments(Product p, NodeChild root){
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
                        s.addComment(User.findByUid(u.@uid.text()), comment.body.text())
                    }else if(defaultU){
                       s.addComment(defaultU, comment.body.text())
                    }
                }
            }
        }
    }
}
