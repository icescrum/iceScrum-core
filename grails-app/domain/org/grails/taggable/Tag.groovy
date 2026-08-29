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

import grails.util.Holders

/**
 * A tag entity used to store the tag names
 *
 * @author Graeme Rocher
 */
class Tag implements Serializable{

    static transients = ['caseSensitive']

    static Boolean preserveCaseForTesting = null
    static @Lazy boolean preserveCaseFromConfig = {
        Holders.config.getProperty('grails.taggable.preserve.case', Boolean, false)
    }
    static boolean getPreserveCase() {
        return (preserveCaseForTesting != null) ? preserveCaseForTesting : preserveCaseFromConfig
    }

    String name

    void setName(String name) {
        this.@name = Tag.preserveCase ? name : name.toLowerCase()
    }

    String toString() {
        name
    }

    static searchable = { only = ['name'] }

    static constraints = {
        name blank:false, unique:true
    }

    static mapping = {
        cache 'read-write'
        def config = Holders.config
        table config.getProperty('grails.taggable.tag.table', String, "tags")
        autoImport config.getProperty('grails.taggable.tag.autoImport', Boolean, false)
    }
}
