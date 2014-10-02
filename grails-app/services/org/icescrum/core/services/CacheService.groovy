/*
 * Copyright (c) 2014 Kagilum SAS.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 * Nicolas Noullet (nnoullet@kagilum.com)
 */


package org.icescrum.core.services

import org.springframework.cache.Cache
import org.springframework.cache.Cache.ValueWrapper

class CacheService {

    def grailsCacheManager

    def doWithCache(String cacheName, Serializable key, Closure closure) {
        println "Test cache"
        if (!grailsCacheManager) {
            log.error "Cache manager not found"
            return closure()
        }
            if (grailsCacheManager.cacheExists(cacheName)) {
                println "Cache '$cacheName' was found"
            } else {
                println "Cache '$cacheName' does not exist, it will be created"
            }
        Cache cache = grailsCacheManager.getCache(cacheName)
        ValueWrapper cachedValue = cache.get(key)
        def result
        if (cachedValue == null) {
            println "Cache '$cache.name' missed with key '$key'"
            result = closure()
            cache.put(key, result)
        } else {
            result = cachedValue.get()
            println "Cache '$cache.name' hit with key '$key'"
        }
        return result
    }
}