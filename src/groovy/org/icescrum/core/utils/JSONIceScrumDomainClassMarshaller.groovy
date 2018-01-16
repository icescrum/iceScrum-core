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
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl

public class JSONIceScrumDomainClassMarshaller extends DomainClassMarshaller {

    private ProxyHandler proxyHandler
    private Map propertiesMap
    private boolean includeClass
    private GrailsApplication grailsApplication
    public static final EXCLUDES_ALL_JSON_PROPERTIES = "*"
    public static final OVERRIDE_JSON_PROPERTIES = "#"

    public JSONIceScrumDomainClassMarshaller(GrailsApplication grailsApplication, boolean includeVersion, boolean includeClass, Map propertiesMap) {
        super(includeVersion, grailsApplication)
        this.proxyHandler = new DefaultProxyHandler()
        this.propertiesMap = propertiesMap
        this.includeClass = includeClass
        this.grailsApplication = grailsApplication
    }

    public boolean supports(Object object) {
        def configName = GrailsNameUtils.getShortName(object.getClass()).toLowerCase()
        return (DomainClassArtefactHandler.isDomainClass(object.getClass()) && propertiesMap."${configName}" != null)
    }

    public void marshalObject(Object value, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter()
        value = proxyHandler.unwrapIfProxy(value)
        Class<?> clazz = value.getClass()
        GrailsDomainClass domainClass = grailsApplication.getDomainClass(clazz.getName())
        BeanWrapper beanWrapper = new BeanWrapperImpl(value)
        def configName = GrailsNameUtils.getShortName(clazz).toLowerCase()
        def request = WebUtils.retrieveGrailsWebRequest()?.getCurrentRequest()

        writer.object()

        if (this.includeClass) {
            writer.key("class").value(GrailsNameUtils.getShortName(domainClass.getClazz().getName()))
        }

        GrailsDomainClassProperty id = domainClass.getIdentifier()
        Object idValue = extractValue(value, id)
        json.property("id", idValue)

        if (isIncludeVersion()) {
            GrailsDomainClassProperty versionProperty = domainClass.getVersion()
            Object version = extractValue(value, versionProperty)
            json.property("version", version)
        }

        List<GrailsDomainClassProperty> properties = domainClass.getPersistentProperties().toList()

        def excludes = []
        if (propertiesMap.exclude) {
            excludes.addAll(propertiesMap.exclude)
        }
        if (propertiesMap."${configName}".exclude) {
            excludes.addAll(propertiesMap."${configName}".exclude)
        }
        if (request?.marshaller?."${configName}"?.exclude) {
            excludes.addAll(request.marshaller."${configName}".exclude)
        }
        if (propertiesMap."${configName}"?.include) { // Treated separately after the main loop
            excludes.addAll(propertiesMap."${configName}".include.findAll { it != OVERRIDE_JSON_PROPERTIES })
        }
        if (request?.marshaller?."${configName}"?.include) {
            excludes.addAll(request.marshaller."${configName}".include)
        }

        if (request?.marshaller?."${configName}"?.exclude?.contains(EXCLUDES_ALL_JSON_PROPERTIES)) {
            properties = []
        } else {
            properties.removeAll { it.getName() in excludes }
        }

        for (GrailsDomainClassProperty property : properties) {
            if (!property.isAssociation()) {
                // Write non-relation property
                writer.key(property.getName())
                Object val = beanWrapper.getPropertyValue(property.getName())
                json.convertAnother(val)
            } else {
                Object referenceObject = beanWrapper.getPropertyValue(property.getName())
                if (isRenderDomainClassRelations()) {
                    writer.key(property.getName())
                    if (referenceObject == null) {
                        writer.value(null)
                    } else {
                        referenceObject = proxyHandler.unwrapIfProxy(referenceObject)
                        if (referenceObject instanceof SortedMap) {
                            referenceObject = new TreeMap((SortedMap) referenceObject)
                        } else if (referenceObject instanceof SortedSet) {
                            referenceObject = new TreeSet((SortedSet) referenceObject)
                        } else if (referenceObject instanceof Set) {
                            referenceObject = new HashSet((Set) referenceObject)
                        } else if (referenceObject instanceof Map) {
                            referenceObject = new HashMap((Map) referenceObject)
                        } else if (referenceObject instanceof Collection) {
                            referenceObject = new ArrayList((Collection) referenceObject)
                        }
                        json.convertAnother(referenceObject)
                    }
                } else {
                    if (referenceObject == null) {
                        writer.key(property.getName())
                        json.value(null)
                    } else {
                        GrailsDomainClass referencedDomainClass = property.getReferencedDomainClass()

                        // Embedded are now always fully rendered
                        if (referencedDomainClass == null || property.isEmbedded() || GrailsClassUtils.isJdk5Enum(property.getType())) {
                            writer.key(property.getName())
                            json.convertAnother(referenceObject)
                        } else if (property.isOneToOne() || property.isManyToOne() || property.isEmbedded()) {
                            writer.key(property.getName())
                            asShortObject(referenceObject, json, referencedDomainClass.getIdentifier(), referencedDomainClass)
                        } else {
                            GrailsDomainClassProperty referencedIdProperty = referencedDomainClass.getIdentifier()
                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject
                                if (propertiesMap[configName]?.withIds?.contains(property.name) || request?.marshaller?."${configName}"?.withIds?.contains(property.name)) {
                                    writer.key(property.getName() + "_ids")
                                    writer.array()
                                    for (Object el : o) {
                                        writer.object()
                                        writer.key("id").value(extractValue(el, referencedIdProperty))
                                        writer.endObject()
                                    }
                                    writer.endArray()
                                } else if (!referenceObject.hasProperty(property.getName() + "_count")) {
                                    int count = domainClass.getClazz().withSession { session ->
                                        session.createFilter(referenceObject, 'select count(*)').uniqueResult()
                                    }
                                    writer.key(property.getName() + "_count").value(count)
                                }
                            } else if (referenceObject instanceof Map) {
                                writer.key(property.getName())
                                Map<Object, Object> map = (Map<Object, Object>) referenceObject
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    String key = String.valueOf(entry.getKey())
                                    Object o = entry.getValue()
                                    writer.object()
                                    writer.key(key)
                                    asShortObject(o, json, referencedIdProperty, referencedDomainClass)
                                    writer.endObject()
                                }
                            }
                        }
                    }
                }
            }
        }

        def user = grailsApplication.mainContext.springSecurityService.currentUser

        if (!request?.marshaller?."${configName}"?.include?.contains(OVERRIDE_JSON_PROPERTIES)) {
            propertiesMap."${configName}"?.include?.each {
                propertyInclude(json, writer, value, configName, user, it)
            }
        }

        request?.marshaller?."${configName}"?.include?.each {
            if (it != OVERRIDE_JSON_PROPERTIES) {
                propertyInclude(json, writer, value, configName, user, it)
            }
        }

        if (!request?.marshaller?."${configName}"?.withIds?.contains(OVERRIDE_JSON_PROPERTIES)) {
            propertiesMap."${configName}"?.withIds?.each {
                //because same withIds works for gorm properties we need to check if it has been done already
                propertyWithIds(writer, properties, value, configName, user, it)
            }
        }

        request?.marshaller?."${configName}"?.withIds?.each {
            if (it != OVERRIDE_JSON_PROPERTIES) {
                propertyWithIds(writer, properties, value, configName, user, it)
            }
        }

        if (!request?.marshaller?."${configName}"?.textile?.contains(OVERRIDE_JSON_PROPERTIES)) {
            propertiesMap."${configName}"?.textile?.each {
                propertyTextile(writer, value, it)
            }
        }

        request?.marshaller?."${configName}"?.textile?.each {
            if (it != OVERRIDE_JSON_PROPERTIES) {
                propertyTextile(writer, value, it)
            }
        }

        writer.endObject()
    }

    private static void propertyTextile(def writer, def value, def it) {
        def val = value.properties."${it}"
        writer.key(it + "_html").value(ServicesUtils.textileToHtml(val))
    }

    private void propertyInclude(def json, def writer, def value, def configName, def user, def it) {
        def granted = propertiesMap."${configName}".security?."${it}" != null ? propertiesMap."${configName}".security?."${it}" : true
        granted = granted instanceof Closure ? granted(value, grailsApplication, user) : granted
        if (granted) {
            def val = value.properties."${it}"
            if (val != null) {
                writer.key(it);
                json.convertAnother(val);
            }
        }
    }

    private void propertyWithIds(def writer, def properties, def value, def configName, def user, def it) {
        def gormPropertiesName = properties.collect { it.name }
        if (!gormPropertiesName.contains(it)) {
            def granted = propertiesMap."${configName}".security?."${it}" != null ? propertiesMap."${configName}".security?."${it}" : true
            granted = granted instanceof Closure ? granted(value, grailsApplication, user) : granted
            if (granted) {
                def val = value.properties."${it}"
                if (val instanceof Collection) {
                    writer.key(it + "_ids")
                    writer.array()
                    for (Object el : val) {
                        writer.object()
                        writer.key("id").value(el.id)
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
        JSONWriter writer = json.getWriter()
        writer.object()

        if (this.includeClass) {
            writer.key("class").value(GrailsNameUtils.getShortName(referencedDomainClass.getName()))
        }

        writer.key("id").value(idValue)

        def configName = GrailsNameUtils.getShortName(referencedDomainClass.getName()).toLowerCase()
        def request = WebUtils.retrieveGrailsWebRequest()?.getCurrentRequest()
        def user = grailsApplication.mainContext.springSecurityService.currentUser

        if (!request?.marshaller?."${configName}"?.asShort?.contains(OVERRIDE_JSON_PROPERTIES)) {
            propertiesMap."${configName}"?.asShort?.each {
                propertyInclude(json, writer, refObj, configName, user, it)
            }
        }

        request?.marshaller?."${configName}"?.asShort?.each {
            if (it != OVERRIDE_JSON_PROPERTIES) {
                propertyInclude(json, writer, refObj, configName, user, it)
            }
        }

        writer.endObject()
    }
}