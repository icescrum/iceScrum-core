package org.grails.taggable

import grails.util.*

class TagsTagLib {

    static namespace = 'tags'
    static defaultCssClasses = ['smallest', 'small', 'medium', 'large', 'largest']

    def taggableService

    /**
     * Generates a tag cloud using CSS styles to identify the relative importance
     * of each tag. These CSS styles can be configured via a 'grails.taggable.css.classes'
     * runtime config setting.
     */
    def tagCloud = { attrs ->
        if (!attrs.action) throwTagError("Required attribute [action] is missing")
        if (attrs.tags == null || !(attrs.tags instanceof Map)) {
            throwTagError("Required attribute [tags] must be a map of tag names to tag counts")
        }

        def classes = grailsApplication.config.getProperty('grails.taggable.css.classes', List, null) ?: defaultCssClasses

        // The named arguments for the 'link' GSP tag used to display each tag.
        def idProperty = attrs?.idProperty ?: "id"
        def paramsMap = [:]
        def linkArgs = [action: attrs.action, params: paramsMap]

        // If a controller name is specified, we use that. Otherwise, we leave it
        // to the 'link' GSP tag to decide whcih controller to use.
        if (attrs.controller) linkArgs["controller"] = attrs.controller

        // How many times has the most used tag been applied?
        def tagMap = attrs.tags ?: [:]
        def maxCount = tagMap.values().max()
        out << "<ol class=\"tagCloud\">"
        for (t in tagMap) {
            def tagRanking
            if (t.value == maxCount) {
                tagRanking = classes.size() - 1
            }
            else {
                // This calculation only works if t.value != maxCount, otherwise
                // we end up with an array index that is equal to the length of
                // the 'classes' list.
                tagRanking = (classes.size() * t.value).intdiv(maxCount)
            }

            tagRanking = tagRanking?.toInteger()
            out << "<li class=\"${classes[tagRanking]}\">"

            paramsMap[idProperty] = t.key
            out << g.link(linkArgs.clone(), t.key)
            out << "</li>"
        }
        out << "</ol>"
    }

}
