package org.icescrum.core.event

import org.icescrum.core.domain.User
import org.icescrum.plugins.attachmentable.domain.Attachment
import org.icescrum.core.domain.BacklogElement
import org.grails.comments.Comment
import org.icescrum.core.domain.Story

class IceScrumStoryEvent extends IceScrumEvent {

    static final String EVENT_SUGGESTED = 'Suggested'
    static final String EVENT_ACCEPTED = 'Accepted'
    static final String EVENT_ESTIMATED = 'Estimated'
    static final String EVENT_PLANNED = 'Planned'
    static final String EVENT_UNPLANNED = 'UnPlanned'
    static final String EVENT_INPROGRESS = 'InProgress'
    static final String EVENT_DONE = 'Done'
    static final String EVENT_UNDONE = 'UnDone'

    static final String EVENT_FEATURE_ASSOCIATED = 'featureAssociated'
    static final String EVENT_FEATURE_DISSOCIATED = 'featureDissociated'

    static final String EVENT_DEPENDS_ON = 'dependsOn'
    static final String EVENT_NOT_DEPENDS_ON = 'notDependsOn'

    static final String EVENT_ACCEPTED_AS_FEATURE = 'AcceptedAsFeature'
    static final String EVENT_ACCEPTED_AS_TASK = 'AcceptedAsTask'

    static final String EVENT_COMMENT_ADDED = 'CommentAdded'
    static final String EVENT_COMMENT_UPDATED = 'CommentUpdated'
    static final String EVENT_COMMENT_DELETED = 'CommentDeleted'

    static final String EVENT_FILE_ATTACHED_ADDED = 'FileAttachedAdded'

    static final EVENT_STATE_LIST = [EVENT_SUGGESTED, EVENT_ACCEPTED, EVENT_ESTIMATED, EVENT_PLANNED, EVENT_UNPLANNED, EVENT_INPROGRESS, EVENT_DONE, EVENT_UNDONE]
    static final EVENT_COMMENT_LIST = [EVENT_COMMENT_ADDED, EVENT_COMMENT_UPDATED]
    static final EVENT_ACCEPTED_AS_LIST = [EVENT_ACCEPTED_AS_FEATURE, EVENT_ACCEPTED_AS_TASK]
    static final EVENT_FEATURE_LIST = [EVENT_FEATURE_ASSOCIATED, EVENT_FEATURE_DISSOCIATED]

    def attachment = null
    def comment = null

    IceScrumStoryEvent(Story story, Class generatedBy, User doneBy, def type, boolean synchronous = false) {
        super(story, generatedBy, doneBy, type, synchronous)
    }

    IceScrumStoryEvent(BacklogElement element, Class generatedBy, User doneBy, def type, boolean synchronous = false) {
        super(element, generatedBy, doneBy, type, synchronous)
    }

    IceScrumStoryEvent(BacklogElement element, Comment comment, Class generatedBy, User doneBy, def type, boolean synchronous = false) {
        super(element, generatedBy, doneBy, type, synchronous)
        this.comment = comment
    }

    IceScrumStoryEvent(BacklogElement element, Attachment attachment, Class generatedBy, User doneBy, def type, boolean synchronous = false) {
        super(element, generatedBy, doneBy, type, synchronous)
        this.attachment = attachment
    }
}
