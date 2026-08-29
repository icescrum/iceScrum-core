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

class Comment implements Serializable {

	String body
	Date dateCreated
	Date lastUpdated
	Long posterId
	String posterClass

	def getPoster() {
		// handle proxied class names
		def i = posterClass.indexOf('_$$_javassist')
		if(i>-1)
			posterClass = posterClass[0..i-1]
		getClass().classLoader.loadClass(posterClass).get(posterId)
	}

	static findAllByPoster(poster, Map args = [:]) {
		if(poster.id == null) throw new CommentException("Poster [$poster] is not a persisted instance")
		if(!args.containsKey("cache")) {
			args.cache = true
		}
		Comment.findAllByPosterIdAndPosterClass(poster.id, poster.class.name, args)
	}

	static countByPoster(poster) {
		if(poster.id == null) throw new CommentException("Poster [$poster] is not a persisted instance")

		Comment.countByPosterIdAndPosterClass(poster.id, poster.class.name)
	}

	static constraints = {
		body blank:false
		posterClass blank:false
		posterId min:0L
	}

	static searchable = [only:'body']
	static mapping = {
		table 'comment' // Table name produced by the Grails 2 plugin, kept explicit for safety
		body type:"text"
		cache true
	}

}
