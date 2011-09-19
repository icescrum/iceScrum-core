package org.icescrum.cache

import org.springframework.context.ApplicationContextAware
import grails.spring.BeanBuilder
import org.springframework.cache.ehcache.EhCacheFactoryBean
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.springframework.context.ApplicationContext

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 16/09/11
 * Time: 01:42
 * To change this template use File | Settings | File Templates.
 */
class DefaultCacheCreator implements CacheCreator, ApplicationContextAware{
    ApplicationContext applicationContext
    GrailsApplication grailsApplication
    def springcacheCacheManager

    boolean createCache(String _cacheName) {
        String cache = springcacheCacheManager.cacheNames?.find { it == _cacheName }
        if (!cache){
            def defaultCacheName = _cacheName.split('_')
            def beanBuilder = new BeanBuilder(applicationContext)
            def isCacheConfig = null
            grailsApplication.config.springcache.caches?.each { name,configObject -> if (name == defaultCacheName.last()) isCacheConfig = configObject }
            beanBuilder.beans {
                "$_cacheName"(EhCacheFactoryBean) { bean ->
                    bean.parent = ref("springcacheDefaultCache", true)
                    cacheName = _cacheName
                    isCacheConfig?.each {
                        bean.setPropertyValue it.key, it.value
                    }
                }
            }
            beanBuilder.createApplicationContext()
            return true
        }
        return false
    }

    void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext
    }
}
