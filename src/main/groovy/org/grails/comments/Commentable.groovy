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
package org.grails.comments

import grails.util.GrailsNameUtils

/**
 * Trait used to specify a domain that can be commented on.
 *
 * Formerly a marker interface: the methods below were injected dynamically by
 * the Grails 2 CommentableGrailsPlugin descriptor (doWithDynamicMethods) and
 * have been converted into trait methods with the same names and semantics.
 *
 * @author Graeme Rocher
 */
trait Commentable {

	static List<Comment> getRecentComments() {
		def clazz = this
		CommentLink.withCriteria {
			projections { property "comment" }
			eq 'type', GrailsNameUtils.getPropertyName(clazz)
			maxResults 5
			cache true
			comment {
				order "dateCreated", "desc"
			}
		}
	}

	def addComment(poster, String text) {
		if(this.id == null) throw new CommentException("You must save the entity [${this}] before calling addComment")

		def posterClass = poster.class.name
		def i = posterClass.indexOf('_$$_javassist')
		if(i>-1)
			posterClass = posterClass[0..i-1]

		def c = new Comment(body:text, posterId:poster.id, posterClass:posterClass)
		if(!c.validate()) {
			throw new CommentException("Cannot create comment for arguments [$poster, $text], they are invalid.")
		}
		c.save()
		def link = new CommentLink(comment:c, commentRef:this.id, type:GrailsNameUtils.getPropertyName(this.class))
		link.save()
		try {
			this.onAddComment(c)
		} catch (MissingMethodException e) {}
		return this
	}

	List<Comment> getComments() {
		def instance = this
		if(instance.id != null) {
			CommentLink.withCriteria {
				projections {
					property "comment"
				}
				eq "commentRef", instance.id
				eq 'type', GrailsNameUtils.getPropertyName(instance.class)
				cache true
			}
		} else {
			return Collections.EMPTY_LIST
		}
	}

	def getTotalComments() {
		def instance = this
		if(instance.id != null) {
			CommentLink.createCriteria().get {
				projections {
					rowCount()
				}
				eq "commentRef", instance.id
				eq 'type', GrailsNameUtils.getPropertyName(instance.class)
				cache true
			}
		} else {
			return 0
		}
	}

	def removeComment(Comment c) {
		CommentLink.findAllByComment(c)*.delete()
		c.delete(flush:true) // cascades deletes to links
	}

	def removeComment(Long id) {
		def c = Comment.get(id)
		if(c) removeComment(c)
	}

	def deleteComments() {
		getComments()?.each{ Comment c ->
			removeComment(c)
		}
	}
}
