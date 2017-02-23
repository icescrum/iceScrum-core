/*
 * Copyright (c) 2014 Kagilum.
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
 *
 */
package org.icescrum.core.event

import grails.util.GrailsNameUtils
import grails.util.Holders
import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.slf4j.Logger
import org.slf4j.LoggerFactory

abstract class IceScrumEventPublisher {

    static void registerListener(String domain, IceScrumEventType eventType, Closure listener) {
        listener.delegate = this
        GrailsApplication grailsApplication = Holders.grailsApplication
        if (grailsApplication.config.icescrum.listenersByDomain == null) {
            grailsApplication.config.icescrum.listenersByDomain = [:]
        }
        def listenersByDomain = grailsApplication.config.icescrum.listenersByDomain
        if (listenersByDomain[domain] == null) {
            listenersByDomain[domain] = [:]
        }
        if (listenersByDomain[domain][eventType] == null) {
            listenersByDomain[domain][eventType] = []
        }
        listenersByDomain[domain][eventType] << listener
    }

    static void registerListener(String domain, Closure listener) {
        IceScrumEventType.values().each { IceScrumEventType type ->
            if (type != IceScrumEventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                registerListener(domain, type, listener)
            }
        }
    }

    Map publishSynchronousEvent(IceScrumEventType type, object, Map dirtyProperties = extractDirtyProperties(type, object)) {
        logEvent(type, object, dirtyProperties)
        def domain = GrailsNameUtils.getPropertyNameRepresentation(object.class)
        Holders.grailsApplication.config.icescrum.listenersByDomain.getAt(domain)?.getAt(type)?.each {
            it(type, object, dirtyProperties)
        }
        return dirtyProperties
    }

    private static Map extractDirtyProperties(IceScrumEventType type, object) {
        def dirtyProperties = [:]
        if (type == IceScrumEventType.BEFORE_UPDATE) {
            object.dirtyPropertyNames.each {
                dirtyProperties[it] = object.getPersistentValue(it)
            }
        } else if (type == IceScrumEventType.BEFORE_DELETE) {
            new DefaultGrailsDomainClass(object.class).persistentProperties.each { property ->
                def name = property.name
                dirtyProperties[name] = object.properties[name]
            }
            dirtyProperties.id = object.id
        }
        return dirtyProperties
    }

    private static void logEvent(IceScrumEventType type, object, Map dirtyProperties) {
        Logger log = LoggerFactory.getLogger(getClass())
        if (log.isDebugEnabled()) {
            def id = object.id ?: dirtyProperties.id
            log.debug("$type ${GrailsNameUtils.getPropertyNameRepresentation(object.class)} $id")
            if (type == IceScrumEventType.UPDATE) {
                dirtyProperties.each { dirtyProperty, oldValue ->
                    if (object.hasProperty("$dirtyProperty")) {
                        def newValue = object."$dirtyProperty"
                        if (newValue != oldValue) {
                            if (dirtyProperty == 'password') {
                                oldValue = '*******************'
                                newValue = oldValue
                            }
                            log.debug("-- $dirtyProperty: \t" + oldValue + "\t-> " + newValue)
                        }
                    } else {
                        log.debug("-- $dirtyProperty: \t" + oldValue + "\t-> ")
                    }
                }
            }
        }
    }
}
