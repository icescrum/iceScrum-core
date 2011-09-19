package org.icescrum.cache

import grails.spring.BeanBuilder

/**
 * Created by IntelliJ IDEA.
 * User: vbarrier
 * Date: 16/09/11
 * Time: 01:39
 * To change this template use File | Settings | File Templates.
 */
interface CacheCreator {
    public boolean createCache(String _cacheName);
}
