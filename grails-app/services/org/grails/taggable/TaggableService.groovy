package org.grails.taggable

import grails.gorm.transactions.Transactional
import grails.util.*

@Transactional // Grails 7: services are no longer implicitly transactional
class TaggableService {

    def grailsApplication

    def domainClassFamilies = [:]

    def getTagCounts(type) {
        def tagCounts = [:]
        TagLink.withCriteria {
            eq('type', type)
            projections {
                groupProperty('tag')
                count('tagRef')
            }
        }.each {
            def tagName = it[0].name
            def count = it[1]
            tagCounts[tagName] = tagCounts[tagName] ? (tagCounts[tagName] + count) : count
        }
        return tagCounts
    }

    /**
     * Update the graph of known subclasses
     *
     * Example:
     * [
     *  WcmContent: [
     *      WcmBlog,
     *      WcmHTMLContent,
     *      WcmComment
     *   ]
     *  WcmBlog: [],
     *  WcmHTMLContent: [WcmRichContent],
     *  WcmRichContent: [],
     *  WcmStatus: []
     * ]
     */
    def refreshDomainClasses() {
        def domainClasses = grailsApplication.domainClasses
        domainClasses.each { artefact ->
            if (Taggable.class.isAssignableFrom(artefact.clazz)) {
                def family = [GrailsNameUtils.getPropertyName(artefact.clazz)]
                // Add all subclasses (GrailsDomainClass.subClasses is gone in Grails 7)
                domainClasses.each { other ->
                    if (other.clazz != artefact.clazz && artefact.clazz.isAssignableFrom(other.clazz)) {
                        family << GrailsNameUtils.getPropertyName(other.clazz)
                    }
                }
                domainClassFamilies[artefact.clazz.name] = family
            }
        }
    }
}
