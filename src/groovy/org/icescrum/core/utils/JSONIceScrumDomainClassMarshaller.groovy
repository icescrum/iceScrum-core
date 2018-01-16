/*
 * Copyright (c) 2011 Kagilum.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 *
 * iceScrum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY without even the implied warranty of
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
package org.icescrum.core.utils

import grails.converters.JSON
import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.commons.*
import org.codehaus.groovy.grails.support.proxy.DefaultProxyHandler
import org.codehaus.groovy.grails.support.proxy.EntityProxyHandler
import org.codehaus.groovy.grails.support.proxy.ProxyHandler
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import org.codehaus.groovy.grails.web.converters.marshaller.json.DomainClassMarshaller
import org.codehaus.groovy.grails.web.json.JSONWriter
import org.codehaus.groovy.grails.web.util.WebUtils
import org.icescrum.core.domain.User
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl

public class JSONIceScrumDomainClassMarshaller extends DomainClassMarshaller {

    private ProxyHandler proxyHandler
    private Map propertiesMap
    private GrailsApplication grailsApplication

    public JSONIceScrumDomainClassMarshaller(GrailsApplication grailsApplication, Map propertiesMap) {
        super(false, grailsApplication)
        this.proxyHandler = new DefaultProxyHandler()
        this.propertiesMap = propertiesMap
        this.grailsApplication = grailsApplication
    }

    public boolean supports(Object object) {
        def configName = GrailsNameUtils.getShortName(object.getClass()).toLowerCase()
        return (DomainClassArtefactHandler.isDomainClass(object.getClass()) && propertiesMap."$configName" != null)
    }

    public void marshalObject(Object value, JSON json) throws ConverterException {
        JSONWriter writer = json.writer
        value = proxyHandler.unwrapIfProxy(value)
        Class<?> clazz = value.getClass()
        GrailsDomainClass domainClass = grailsApplication.getDomainClass(clazz.name)
        BeanWrapper beanWrapper = new BeanWrapperImpl(value)
        def configName = GrailsNameUtils.getShortName(clazz).toLowerCase()
        def config = propertiesMap."$configName"
        def requestConfig = WebUtils.retrieveGrailsWebRequest()?.currentRequest?.marshaller?."$configName"

        writer.object()
        writer.key('class').value(GrailsNameUtils.getShortName(domainClass.clazz.name))
        json.property('id', extractValue(value, domainClass.identifier))

        List<GrailsDomainClassProperty> properties
        if (requestConfig?.excludeAll) {
            properties = []
        } else {
            properties = domainClass.persistentProperties.toList()
            def excludes = []
            if (config.exclude) {
                excludes.addAll(config.exclude)
            }
            if (requestConfig?.exclude) {
                excludes.addAll(requestConfig.exclude)
            }
            if (config.include) { // Treated separately after the main loop
                excludes.addAll(config.include)
            }
            if (requestConfig?.include) {
                excludes.addAll(requestConfig.include)
            }
            properties.removeAll { it.name in excludes }
        }
        properties.each { GrailsDomainClassProperty property ->
            marshallProperty(property, beanWrapper, writer, json, domainClass, config, requestConfig)
        }

        if (!requestConfig?.overrideInclude) {
            config.include?.each {
                propertyInclude(json, writer, value, config, it)
            }
        }
        requestConfig?.include?.each {
            propertyInclude(json, writer, value, config, it)
        }
        if (!requestConfig?.overrideWithIds) {
            config.withIds?.each {
                propertyWithIds(writer, properties, value, config, it)
            }
        }
        requestConfig?.withIds?.each {
            propertyWithIds(writer, properties, value, config, it)
        }
        if (!requestConfig?.overrideTextile) {
            config.textile?.each {
                propertyTextile(writer, value, it)
            }
        }
        requestConfig?.textile?.each {
            propertyTextile(writer, value, it)
        }

        writer.endObject()
    }

    private void marshallProperty(property, beanWrapper, writer, json, domainClass, config, requestConfig) {
        Object propertyValue = beanWrapper.getPropertyValue(property.name)
        if (!property.isAssociation()) {
            writer.key(property.name)
            json.convertAnother(propertyValue)
        } else if (propertyValue == null) {
            writer.key(property.name)
            json.value(null)
        } else {
            GrailsDomainClass referencedDomainClass = property.referencedDomainClass
            if (referencedDomainClass == null || property.isEmbedded() || GrailsClassUtils.isJdk5Enum(property.type)) {
                writer.key(property.name)
                json.convertAnother(propertyValue)
            } else if (property.isOneToOne() || property.isManyToOne() || property.isEmbedded()) {
                writer.key(property.name)
                asShortObject(propertyValue, json, referencedDomainClass.identifier, referencedDomainClass)
            } else {
                GrailsDomainClassProperty referencedIdProperty = referencedDomainClass.identifier
                if (propertyValue instanceof Collection) {
                    Collection o = (Collection) propertyValue
                    if (config.withIds?.contains(property.name) || requestConfig?.withIds?.contains(property.name)) {
                        writer.key(property.name + '_ids')
                        writer.array()
                        for (Object el : o) {
                            writer.object()
                            writer.key('id').value(extractValue(el, referencedIdProperty))
                            writer.endObject()
                        }
                        writer.endArray()
                    } else if (!propertyValue.hasProperty(property.name + '_count')) {
                        int count = domainClass.clazz.withSession { session ->
                            session.createFilter(propertyValue, 'select count(*)').uniqueResult()
                        }
                        writer.key(property.name + '_count').value(count)
                    }
                } else if (propertyValue instanceof Map) {
                    writer.key(property.name)
                    Map<Object, Object> map = (Map<Object, Object>) propertyValue
                    for (Map.Entry<Object, Object> entry : map.entrySet()) {
                        String key = String.valueOf(entry.key)
                        Object o = entry.value
                        writer.object()
                        writer.key(key)
                        asShortObject(o, json, referencedIdProperty, referencedDomainClass)
                        writer.endObject()
                    }
                }
            }
        }
    }

    private static void propertyTextile(def writer, def value, def it) {
        def val = value.properties."$it"
        writer.key(it + '_html').value(ServicesUtils.textileToHtml(val))
    }

    private void propertyInclude(def json, def writer, def value, def config, def it) {
        def granted = config.security?."$it" != null ? config.security?."$it" : true
        User user = (User) grailsApplication.mainContext.springSecurityService.currentUser
        granted = granted instanceof Closure ? granted(value, grailsApplication, user) : granted
        if (granted) {
            def val = value.properties."$it"
            if (val != null) {
                writer.key(it);
                json.convertAnother(val);
            }
        }
    }

    private void propertyWithIds(def writer, def properties, def value, def config, def it) {
        if (!properties.collect { it.name }.contains(it)) {
            def granted = config.security?."$it" != null ? config.security?."$it" : true
            User user = (User) grailsApplication.mainContext.springSecurityService.currentUser
            granted = granted instanceof Closure ? granted(value, grailsApplication, user) : granted
            if (granted) {
                def val = value.properties."$it"
                if (val instanceof Collection) {
                    writer.key(it + '_ids')
                    writer.array()
                    for (Object el : val) {
                        writer.object()
                        writer.key('id').value(el.id)
                        writer.endObject()
                    }
                    writer.endArray()
                }
            }
        }
    }
    @Override
    protected void asShortObject(Object refObj, JSON json, GrailsDomainClassProperty idProperty, GrailsDomainClass referencedDomainClass) throws ConverterException {
        Object idValue
        if (proxyHandler instanceof EntityProxyHandler) {
            idValue = ((EntityProxyHandler) proxyHandler).getProxyIdentifier(refObj)
            if (idValue == null) {
                idValue = extractValue(refObj, idProperty)
            }
        } else {
            idValue = extractValue(refObj, idProperty)
        }
        JSONWriter writer = json.writer
        writer.object()
        writer.key('class').value(GrailsNameUtils.getShortName(referencedDomainClass.name))
        writer.key('id').value(idValue)

        def configName = GrailsNameUtils.getShortName(referencedDomainClass.name).toLowerCase()
        def config = propertiesMap."$configName"
        def requestConfig = WebUtils.retrieveGrailsWebRequest()?.currentRequest?.marshaller?."$configName"

        if (!requestConfig?.overrideAsShort) {
            config?.asShort?.each {
                propertyInclude(json, writer, refObj, config, it)
            }
        }
        requestConfig?.asShort?.each {
            propertyInclude(json, writer, refObj, config, it)
        }

        writer.endObject()
    }
}