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

import org.codehaus.groovy.grails.commons.DefaultGrailsDomainClass

abstract class IceScrumEventPublisher {

    private Map<IceScrumEventType, List<Closure>> listenersByEventType = [:]

    synchronized void registerListener(IceScrumEventType eventType, Closure listener) {
        def listeners = listenersByEventType[eventType]
        if (listeners == null) {
            def emptyListeners = []
            listenersByEventType[eventType] = emptyListeners
            listeners = emptyListeners
        }
        listener.delegate = this
        listeners.add(listener)
    }

    synchronized void registerListener(Closure listener) {
        IceScrumEventType.values().each { IceScrumEventType type ->
            if (type != IceScrumEventType.UGLY_HACK_BECAUSE_ANNOTATION_CANT_BE_NULL) {
                registerListener(type, listener)
            }
        }
    }

    synchronized Map publishSynchronousEvent(IceScrumEventType type, object, Map dirtyProperties = extractDirtyProperties(type, object)) {
        logEvent(type, object, dirtyProperties)
        listenersByEventType[type]?.each { it(type, object, dirtyProperties) }
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
        def id = object.id ?: dirtyProperties.id
        println "$type ${object.class.toString().split('\\.').last()} $id"
        if (type == IceScrumEventType.UPDATE) {
            dirtyProperties.each { dirtyProperty, oldValue ->
                if (object.hasProperty("$dirtyProperty")){
                    def newValue = object."$dirtyProperty"
                    if (newValue != oldValue) {
                        if (dirtyProperty == 'password') {
                            oldValue = '*******************'
                            newValue = oldValue
                        }
                        println "-- $dirtyProperty: \t" + oldValue + "\t-> " + newValue
                    }
                } else {
                    println "-- $dirtyProperty: \t" + oldValue + "\t-> "
                }
            }
        }
    }
}
