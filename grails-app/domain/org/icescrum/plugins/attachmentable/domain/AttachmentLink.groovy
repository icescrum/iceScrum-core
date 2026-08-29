package org.icescrum.plugins.attachmentable.domain

import grails.util.GrailsNameUtils

class AttachmentLink implements Serializable {

    Long attachmentRef
    String attachmentRefClass
    String type

    static belongsTo = [attachment: Attachment]

    static constraints = {
        attachmentRef min: 0L
        attachmentRefClass blank: false
        type blank: false
    }

    static mapping = {
        table 'attachmentable_attachmentlink'
        cache true
    }

    static namedQueries = {
        getAttachments { instance ->
            createAlias("attachment", "a")
            projections {
                property "attachment"
            }
            eq "attachmentRef", instance?.id
            eq 'type', GrailsNameUtils.getPropertyName(instance.class)
            order("a.dateCreated", "desc")
        }

        getTotalAttachments { instance ->
            projections {
                rowCount()
            }
            eq "attachmentRef", instance?.id
            eq 'type', GrailsNameUtils.getPropertyName(instance.class)
        }
    }
}
