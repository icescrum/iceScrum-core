/* Copyright 2006-2007 Graeme Rocher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.taggable

import grails.util.*
/**
 * Marker interface to add tagging capabilities to a particular domain instance
 * @author Graeme Rocher
 */
trait Taggable {

	Taggable addTag(String name) {
       if(this.id == null) throw new TagException("You need to save the domain instance before tagging it")
        def tag
        if (!Tag.preserveCase) {
            name = name.toLowerCase()
        }
        tag = Tag.findByName(name, [cache:true]) ?: new Tag(name:name).save()
        if(!tag) throw new TagException("Value [$name] is not a valid tag")

        def criteria = TagLink.createCriteria()
        def instance = this
        def link = criteria.get {
            criteria.eq 'tag', tag
            criteria.eq 'tagRef', instance.id
            criteria.eq 'type', GrailsNameUtils.getPropertyName(instance.class)
            criteria.cache true
        }

        if(!link) {
            link = new TagLink(tag:tag, tagRef:this.id, type:GrailsNameUtils.getPropertyName(this.class)).save()
        }
        return this // for method chaining
	}


    Taggable addTags( names ) {
        names.each { addTag it.toString() }
        return this
    }

    Collection<String> tags() {
    	getTags()
    }

    def getTags() {
        this.id ? getTagLinks(Holders.applicationContext.taggableService, this).tag.name : []
    }

    Taggable parseTags( String tags, String delimiter = "," ) {
        tags.split(delimiter).each {
            def tag = it.trim()
            if(tag) addTag(tag)
        }
        return this
    }

    Taggable removeTag( String name ) {
        if(this.id == null) throw new TagException("You need to save the domain instance before tagging it")

        if (!Tag.preserveCase) {
            name = name.toLowerCase()
        }

        def criteria = TagLink.createCriteria()
        def instance =this
        def link = criteria.get {
            criteria.tag {
                if (!Tag.preserveCase) {
                    criteria.eq 'name', name
                } else {
                    criteria.ilike 'name', name
                }
            }
            criteria.eq 'tagRef', instance.id
            criteria.eq 'type', GrailsNameUtils.getPropertyName(instance.class)
            criteria.cache true
        }
        link?.delete(flush:true)
        return this
    }

    Taggable setTags( List tags ) {
        // remove invalid tags
        tags =  tags?.findAll { it }

        if (tags) {
            // remove old tags that not appear in the new tags
            getTagLinks(Holders.applicationContext.taggableService, this)*.each { TagLink tagLink ->
                if (tags.contains(tagLink.tag.name)) {
                    tags.remove(tagLink.tag.name)
                } else {
                    tagLink.delete()
                }
            }

            // add the rest
            addTags(tags)
        } else {
            getTagLinks(Holders.applicationContext.taggableService, this)*.delete(flush:true)
        }
        return this
    }



    static List<String> getAllTags() {
    	def criteria = TagLink.createCriteria()
        criteria.list {
            criteria.projections { criteria.tag { criteria.distinct "name" } }
            criteria.'in'('type', (Collection)Holders.applicationContext.taggableService.domainClassFamilies[this.name])
            criteria.cache true
        }
    }

    static Integer getTotalTags() {
        def clazz = this
        def criteria = TagLink.createCriteria()
        criteria.get {
            criteria.projections { criteria.tag { criteria.countDistinct "name" } }
            criteria.'in'('type', (Collection)Holders.applicationContext.taggableService.domainClassFamilies[clazz.name])
            criteria.cache true
        }
    }
    static Integer countByTag( String tag ) {
        def identifiers = getTagReferences(Holders.applicationContext.taggableService, tag, this.name)
        if(identifiers) {
            def criteria = createCriteria()
            criteria.get {
                criteria.projections {
                    criteria.rowCount()
                }
                criteria.inList 'id', identifiers
                criteria.cache true
            }
        }
        else {
            return 0
        }
    }

    static List findAllByTag( String name ) {
        def identifiers = getTagReferences(Holders.applicationContext.taggableService, name, this.name)
        if(identifiers) {
            return findAllByIdInList(identifiers, [cache:true])
        }
        else {
            return Collections.EMPTY_LIST
        }
    }

    static List findAllByTag ( String name, Map args ) {
        def identifiers = getTagReferences(Holders.applicationContext.taggableService, name, this.name)
        if(identifiers) {
            args.cache=true
            return findAllByIdInList(identifiers, args)
        }
        else {
            return Collections.EMPTY_LIST
        }
    }

    static List findAllByTagWithCriteria( String name, Closure crit ) {
        def clazz = this
        def identifiers = getTagReferences(Holders.applicationContext.taggableService, name, clazz.name)
        if(identifiers) {
            return clazz.withCriteria {
                'in'('id', identifiers)

                crit.delegate = delegate
                crit.call()
            }
        }
        else {
            return Collections.EMPTY_LIST
        }
    }
    static List<String> findAllTagsWithCriteria( Map params, Closure crit ) {
        def clazz = this
        def criteria = TagLink.createCriteria()
        criteria.list {
           criteria.projections { criteria.tag { criteria.distinct "name" } }
           criteria.'in'('type', Holders.applicationContext.taggableService.domainClassFamilies[clazz.name])
           criteria.cache true
           criteria.tag(crit)

            if (params.offset != null) {
                criteria.firstResult(params.offset.toInteger())
            }
            if (params.max != null) {
                criteria.maxResults(params.max.toInteger())
            }
            criteria.tag {
                criteria.order('name', 'asc')
            }
        }
    }

    private getTagLinks(tagService, obj) {
        TagLink.findAllByTagRefAndTypeInList(obj.id, tagService.domainClassFamilies[obj.class.name], [cache:true])
    }

    private static getTagReferences(tagService, String tagName, String className) {
        if(tagName) {
        	def criteria = TagLink.createCriteria()
            criteria.list {
                criteria.projections {
                    criteria.property 'tagRef'
                }
                criteria.tag {
                    criteria.eq 'name', tagName
                }
                criteria.'in'('type', tagService.domainClassFamilies[className])
                criteria.cache true
            }

        }
        else {
            return Collections.EMPTY_LIST
        }
    }

}
