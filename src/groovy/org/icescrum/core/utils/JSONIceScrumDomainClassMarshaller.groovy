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
import grails.plugins.wikitext.WikiTextTagLib
import grails.util.Holders
import org.codehaus.groovy.grails.commons.DomainClassArtefactHandler
import org.codehaus.groovy.grails.commons.GrailsApplication
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import org.codehaus.groovy.grails.web.json.JSONWriter
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.codehaus.groovy.grails.web.converters.marshaller.json.DomainClassMarshaller
import org.codehaus.groovy.grails.support.proxy.DefaultProxyHandler
import org.codehaus.groovy.grails.support.proxy.ProxyHandler
import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.support.proxy.EntityProxyHandler

public class JSONIceScrumDomainClassMarshaller extends DomainClassMarshaller {

    private ProxyHandler proxyHandler
    private Map propertiesMap
    private WikiTextTagLib textileRenderer
    private boolean includeClass

    public JSONIceScrumDomainClassMarshaller(GrailsApplication grailsApplication, boolean includeVersion, boolean includeClass, Map propertiesMap, WikiTextTagLib textileRenderer) {
        super(includeVersion, grailsApplication)
        this.proxyHandler = new DefaultProxyHandler()
        this.propertiesMap = propertiesMap
        this.includeClass = includeClass
        this.textileRenderer = textileRenderer
    }

    public JSONIceScrumDomainClassMarshaller(GrailsApplication grailsApplication, boolean includeVersion, boolean includeClass, Map propertiesMap) {
        super(includeVersion, grailsApplication)
        this.proxyHandler = new DefaultProxyHandler()
        this.propertiesMap = propertiesMap
        this.includeClass = includeClass
    }

    public boolean supports(Object object) {
        def configName = GrailsNameUtils.getShortName(object.getClass()).toLowerCase()
        return (DomainClassArtefactHandler.isDomainClass(object.getClass()) && propertiesMap."${configName}" != null)
    }

    public void marshalObject(Object value, JSON json) throws ConverterException {
        JSONWriter writer = json.getWriter()
        value = proxyHandler.unwrapIfProxy(value)
        Class<?> clazz = value.getClass()
        GrailsDomainClass domainClass = Holders.grailsApplication.getDomainClass(clazz.getName())
        BeanWrapper beanWrapper = new BeanWrapperImpl(value)
        def configName = GrailsNameUtils.getShortName(clazz).toLowerCase()

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
        if (propertiesMap."${configName}"?.include) {
            excludes.addAll(propertiesMap."${configName}".include)
        }
        properties.removeAll { it.getName() in excludes }

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
                            @SuppressWarnings("unused")
                            String refPropertyName = referencedDomainClass.getPropertyName()
                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject
                                if (propertiesMap[configName]?.withIds?.contains(property.name)) {
                                    writer.key(property.getName() + "_ids")
                                    writer.array()
                                    for (Object el : o) {
                                        writer.object()
                                        writer.key("id").value(extractValue(el, referencedIdProperty))
                                        writer.endObject()
                                    }
                                    writer.endArray()
                                } else {
                                    writer.key(property.getName() + "_count").value(o.size())
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
        propertiesMap."${configName}"?.include?.each {
            if (value.properties."${it}" != null) {
                writer.key(it);
                json.convertAnother(value.properties."${it}");
            }
        }
        propertiesMap."${configName}"?.includeCount?.each {
            if (value.properties."${it}" instanceof Collection) {
                Collection o = value.properties."${it}"
                writer.key(it + "_count").value(o.size())
            }
        }
        if (textileRenderer && propertiesMap."${configName}"?.textile) {
            propertiesMap."${configName}"?.textile?.each {
                if (value.properties."${it}" != null) {
                    writer.key(it + "_html")
                    json.convertAnother(textileRenderer.renderHtml([markup: "Textile"], value.properties."${it}"))
                }
            }
        }
        writer.endObject()
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
        propertiesMap."${configName}"?.asShort?.each {
            if (refObj.properties."${it}" != null) {
                writer.key(it)
                json.convertAnother(refObj.properties."${it}")
            }
        }

        writer.endObject()
    }
}