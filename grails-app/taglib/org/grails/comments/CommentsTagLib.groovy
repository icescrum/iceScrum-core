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

class CommentsTagLib {

	static namespace = "comments"

	def each =  { attrs, body ->
		def bean = attrs.bean
		def varName = attrs.var ?: "comment"
		if(bean?.metaClass?.hasProperty(bean, "comments")) {
			bean.comments?.each {
				out << body((varName):it)
			}
		}
	}

	def eachRecent = { attrs, body ->
		def domain = attrs.domain
		if(!domain && attrs.bean) domain = attrs.bean?.class
		def varName = attrs.var ?: "comment"

		if(domain) {
			domain.recentComments?.each {
				out << body((varName):it)
			}
		}
	}

	def render =  { attrs, body ->
		def bean = attrs.bean
		def noEscape = attrs.containsKey('noEscape') ? attrs.noEscape : false

		plugin.isAvailable(name:"grails-ui") {
			noEscape = true
		}
		if(bean?.metaClass?.hasProperty(bean, "comments")) {
			out << g.render(template:"/commentable/comments", plugin:"commentable", model:[commentable:bean, noEscape:noEscape])
		}
	}

}
