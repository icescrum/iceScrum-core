package org.icescrum.core.domain

import grails.plugin.fluxiable.Fluxiable
import org.icescrum.plugins.attachmentable.interfaces.Attachmentable
import org.grails.comments.Commentable
import org.grails.taggable.Taggable

abstract class BacklogElement implements Fluxiable, Attachmentable, Commentable, Serializable, Taggable {

    static final long serialVersionUID = -6800252500987149051L

    static final String TAG_KEYWORD = "tag:"

    String description
    String notes
    String name
    Date dateCreated
    Date lastUpdated
    Date creationDate = new Date()
    int uid

    TimeBox backlog

    static belongsTo = [backlog: TimeBox]

    static constraints = {
        description(maxSize: 3000, nullable: true)
        notes(maxSize: 5000, nullable: true)
        name(blank: false, unique: 'backlog', maxSize: 100)
    }

    static mapping = {
        cache true
        table 'icescrum2_backlogelement'
        description length: 3000
        notes length: 5000
        backlog index: 'be_name_index'
        name index: 'be_name_index'
        tablePerHierarchy false
        backlog lazy: true
    }

    static boolean hasTagKeyword(String term) {
        term.startsWith(TAG_KEYWORD)
    }

    static String removeTagKeyword(String term) {
        term -= TAG_KEYWORD
        term.trim()
    }

    static Map addTermOrTagToSearch (Map searchOptions, term) {
        if (term) {
            if (hasTagKeyword(term)) {
                def tag = removeTagKeyword(term)
                if (tag) {
                    searchOptions.tag = tag
                    return searchOptions
                }
            }
            searchOptions.term = term
        }
        return searchOptions
    }
}
