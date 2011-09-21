/*
 * Copyright (c) 2011 Kagilum
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with iceScrum.  If not, see <http://www.gnu.org/licenses/>.
 *
 * Authors:
 *
 * Vincent Barrier (vbarrier@kagilum.com)
 *
 */
package org.icescrum.cache

import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.servlet.support.RequestContextUtils as RCU

import grails.plugin.springcache.CacheResolver
import grails.plugin.springcache.key.CacheKeyBuilder
import grails.plugin.springcache.web.ContentCacheParameters
import grails.plugin.springcache.web.key.WebContentKeyGenerator
import grails.spring.BeanBuilder
import net.sf.ehcache.CacheManager
import org.apache.commons.logging.LogFactory
import org.springframework.cache.ehcache.EhCacheFactoryBean
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware

class IceScrumCacheResolver implements CacheResolver {
    CacheCreator cacheCreator
    static final log = LogFactory.getLog(this)

    String resolveCacheName(String baseName){
        return baseName
    }

    boolean autoCreateCache(_cacheName){
        if (_cacheName != null)
            return cacheCreator.createCache(_cacheName)
        else
            return false
    }
}

class BacklogElementCacheResolver extends IceScrumCacheResolver {
        @Override
        String resolveCacheName(String baseName) {
        def params = RCH.currentRequestAttributes().params
        def backlogElementId = ''
        def cachePattern = ~/\w+\d+/
        if (!cachePattern.matcher(baseName).matches()){
            backlogElementId = params.story?.id ?: params.task?.id ?: params.feature?.id ?: params.actor?.id ?: params.id ?: null
        }
        def pid = params.product?.decodeProductKey()
        def cache = "project_${pid}_${baseName}${backlogElementId ?'_'+backlogElementId:''}"
        autoCreateCache(cache)
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class UserCacheResolver extends IceScrumCacheResolver {
    def springSecurityService
    @Override
    String resolveCacheName(String baseName) {
        def id = springSecurityService.principal?.id
        def cache = "${baseName}_user_${id ?: 'anoymous'}"
        autoCreateCache(cache)
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class ProjectCacheResolver extends IceScrumCacheResolver {
    @Override
    String resolveCacheName(String baseName) {
        def cache
        def cachePattern = ~/\d+\w+/
        def params = RCH.currentRequestAttributes().params
        if (!cachePattern.matcher(baseName).matches() && ( params && params.product instanceof String)){
            cache = "project_${params.product?.decodeProductKey() ?: params.id}_${baseName}"
        }else{
            cache = "project_${baseName}"
        }
        autoCreateCache(cache)
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}


class TeamCacheResolver extends IceScrumCacheResolver {
    @Override
    String resolveCacheName(String baseName) {
        def cache
        def cachePattern = ~/\d+\w+/
        if (!cachePattern.matcher(baseName).matches()){
            def params = RCH.currentRequestAttributes().params
            def tid = params.team?.decodeProductKey() ?: params.id
            cache = "team_${tid}_${baseName}"
        }else{
            cache = "team_${baseName}"
        }
        autoCreateCache(cache)
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class UserProjectCacheResolver extends IceScrumCacheResolver {
    def springSecurityService
    @Override
    String resolveCacheName(String baseName) {
        def params = RCH.currentRequestAttributes().params
        def pid = params.product?.decodeProductKey() ?: params.id
        def id = springSecurityService.isLoggedIn() ? springSecurityService.principal.id : 'anonymous'
        def cache = "project_${pid}_${baseName}_${id}"
        autoCreateCache(cache)
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

public class RoleAndLocaleKeyGenerator extends WebContentKeyGenerator {
    def securityService
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        def request = RCH.requestAttributes.currentRequest
        def role = ''
        if (!request.filtered){
            securityService.filterRequest()
        }
        if (request.admin) {
            role = 'adm'
        } else {
            if (request.scrumMaster)  {  role += 'scm'  }
            if (request.teamMember)   {  role += 'tm'  }
            if (request.productOwner) {  role += 'po'  }
            if (request.owner)        {  role += 'owner'  }
            if (!role && request.stakeHolder) {  role += 'sh'  }
        }
        role = role ?: 'anonymous'
        builder << role
        builder << RCU.getLocale(request).toString().substring(0, 2)
    }
}

public class LocaleKeyGenerator extends WebContentKeyGenerator {

    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        def request = RCH.requestAttributes.currentRequest
        builder << RCU.getLocale(request).toString().substring(0, 2)
    }
}

public class UserKeyGenerator extends WebContentKeyGenerator {
    def springSecurityService
    @Override
    protected void generateKeyInternal(CacheKeyBuilder builder, ContentCacheParameters context) {
        super.generateKeyInternal(builder, context)
        def id = springSecurityService.isLoggedIn() ? springSecurityService.principal.id : 'anonymous'
        def request = RCH.requestAttributes.currentRequest
        builder << RCU.getLocale(request).toString().substring(0, 2)
        builder << id
    }
}

class ApplicationCacheResolver extends IceScrumCacheResolver {
    @Override
    String resolveCacheName(String baseName) {
        def cache = "applicationCache_${baseName}"
        autoCreateCache(cache)
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class FilterDelegate {
    def methodMissing(String methodName, args) {
        args[1].call()
    }
}
