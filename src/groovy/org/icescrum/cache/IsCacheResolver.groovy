package org.icescrum.cache

import grails.plugin.springcache.CacheResolver
import org.springframework.web.context.request.RequestContextHolder as RCH
import org.springframework.web.servlet.support.RequestContextUtils as RCU
import org.apache.commons.logging.LogFactory
import grails.plugin.springcache.key.CacheKeyBuilder
import grails.plugin.springcache.web.key.WebContentKeyGenerator
import grails.plugin.springcache.web.ContentCacheParameters

class BacklogElementCacheResolver implements CacheResolver {
    private static final log = LogFactory.getLog(this)

    String resolveCacheName(String baseName) {
        def params = RCH.currentRequestAttributes().params
        def backlogElementId = ''
        def cachePattern = ~/\w+-\d+/
        if (!cachePattern.matcher(baseName).matches()){
            backlogElementId = params.story?.id ?: params.task?.id ?: params.feature?.id ?: params.actor?.id ?: params.id ?: null
        }
        def cache = "${baseName}${backlogElementId ?'-'+backlogElementId:''}"
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class UserCacheResolver implements CacheResolver {
    def springSecurityService
    private static final log = LogFactory.getLog(this)
    String resolveCacheName(String baseName) {
        def id = springSecurityService.principal?.id
        def cache = "${baseName}-user-${id ?: 'anoymous'}"
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class ProjectCacheResolver implements CacheResolver {
    private static final log = LogFactory.getLog(this)
    String resolveCacheName(String baseName) {
        def params = RCH.currentRequestAttributes().params
        def pid = params.product?.decodeProductKey() ?: params.id
        def cache = "${baseName}-project-${pid}"
        if (log.debugEnabled) log.debug("cache: ${cache}")
        return cache
    }
}

class UserProjectCacheResolver implements CacheResolver {
    private static final log = LogFactory.getLog(this)
    def springSecurityService
    String resolveCacheName(String baseName) {
        def params = RCH.currentRequestAttributes().params
        def pid = params.product?.decodeProductKey() ?: params.id
        def id = springSecurityService.isLoggedIn() ? springSecurityService.principal.id : 'anonymous'
        def cache = "${baseName}-project-${pid}-${id}"
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
        builder << id
    }
}

class FilterDelegate {
    def methodMissing(String methodName, args) {
        args[1].call()
    }
}
