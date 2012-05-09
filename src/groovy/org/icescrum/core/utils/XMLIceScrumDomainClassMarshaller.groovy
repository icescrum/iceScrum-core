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
 */
package org.icescrum.core.utils

import org.codehaus.groovy.grails.web.converters.marshaller.xml.DomainClassMarshaller
import grails.util.GrailsNameUtils
import org.codehaus.groovy.grails.web.converters.ConverterUtil
import org.codehaus.groovy.grails.support.proxy.DefaultProxyHandler
import org.codehaus.groovy.grails.support.proxy.ProxyHandler
import org.codehaus.groovy.grails.web.converters.exceptions.ConverterException
import grails.converters.XML
import org.codehaus.groovy.grails.commons.GrailsDomainClass
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.codehaus.groovy.grails.commons.GrailsDomainClassProperty
import org.codehaus.groovy.grails.commons.GrailsClassUtils
import org.codehaus.groovy.grails.support.proxy.EntityProxyHandler

public class XMLIceScrumDomainClassMarshaller extends DomainClassMarshaller {

    private ProxyHandler proxyHandler
    private Map propertiesMap
    private boolean includeVersion

    public XMLIceScrumDomainClassMarshaller(boolean includeVersion, Map propertiesMap) {
        super(includeVersion)
        this.includeVersion = includeVersion
        this.proxyHandler = new DefaultProxyHandler()
        this.propertiesMap = propertiesMap
    }

    public boolean supports(Object object) {
        def configName = GrailsNameUtils.getShortName(object.getClass()).toLowerCase()
        return (ConverterUtil.isDomainClass(object.getClass()) && propertiesMap."${configName}" != null)
    }

    public void marshalObject(Object value, XML xml) throws ConverterException {
        Class clazz = value.getClass()
        GrailsDomainClass domainClass = ConverterUtil.getDomainClass(clazz.getName())
        BeanWrapper beanWrapper = new BeanWrapperImpl(value)
        def configName = GrailsNameUtils.getShortName(clazz).toLowerCase()

        GrailsDomainClassProperty id = domainClass.getIdentifier()
        Object idValue = beanWrapper.getPropertyValue(id.getName())

        if (idValue != null) xml.attribute("id", String.valueOf(idValue))

        if (includeVersion) {
            Object versionValue = beanWrapper.getPropertyValue(domainClass.getVersion().getName())
            xml.attribute("version", String.valueOf(versionValue))
        }

        List<GrailsDomainClassProperty> properties = domainClass.getPersistentProperties().toList()

        def excludes = []
        if(propertiesMap.exclude)
            excludes.addAll(propertiesMap.exclude)
        if(propertiesMap."${configName}".exclude)
            excludes.addAll(propertiesMap."${configName}".exclude)
        if(propertiesMap."${configName}"?.include)
            excludes.addAll(propertiesMap."${configName}".include)
        properties.removeAll{ it.getName() in excludes }

        for (GrailsDomainClassProperty property : properties) {
            xml.startNode(property.getName())
            if (!property.isAssociation()) {
                // Write non-relation property
                Object val = beanWrapper.getPropertyValue(property.getName())
                xml.convertAnother(val)
            }
            else {
                Object referenceObject = beanWrapper.getPropertyValue(property.getName())
                if (isRenderDomainClassRelations()) {
                    if (referenceObject != null) {
                        referenceObject = proxyHandler.unwrapIfProxy(referenceObject)
                        if (referenceObject instanceof SortedMap) {
                            referenceObject = new TreeMap((SortedMap) referenceObject)
                        }
                        else if (referenceObject instanceof SortedSet) {
                            referenceObject = new TreeSet((SortedSet) referenceObject)
                        }
                        else if (referenceObject instanceof Set) {
                            referenceObject = new HashSet((Set) referenceObject)
                        }
                        else if (referenceObject instanceof Map) {
                            referenceObject = new HashMap((Map) referenceObject)
                        }
                        else if (referenceObject instanceof Collection) {
                            referenceObject = new ArrayList((Collection) referenceObject)
                        }
                        xml.convertAnother(referenceObject)
                    }
                }
                else {
                    if (referenceObject != null) {
                        GrailsDomainClass referencedDomainClass = property.getReferencedDomainClass()

                        // Embedded are now always fully rendered
                        if (referencedDomainClass == null || property.isEmbedded() || GrailsClassUtils.isJdk5Enum(property.getType())) {
                            xml.convertAnother(referenceObject)
                        }
                        else if (property.isOneToOne() || property.isManyToOne() || property.isEmbedded()) {
                            asShortObject(referenceObject, xml, referencedDomainClass.getIdentifier(), referencedDomainClass)
                        }
                        else {
                            GrailsDomainClassProperty referencedIdProperty = referencedDomainClass.getIdentifier()
                            @SuppressWarnings("unused")
                            String refPropertyName = referencedDomainClass.getPropertyName()
                            if (referenceObject instanceof Collection) {
                                Collection o = (Collection) referenceObject
                                for (Object el : o) {
                                    xml.startNode(xml.getElementName(el))
                                    asShortObject(el, xml, referencedIdProperty, referencedDomainClass)
                                    xml.end()
                                }
                            }
                            else if (referenceObject instanceof Map) {
                                Map<Object, Object> map = (Map<Object, Object>) referenceObject
                                for (Map.Entry<Object, Object> entry : map.entrySet()) {
                                    String key = String.valueOf(entry.getKey())
                                    Object o = entry.getValue()
                                    xml.startNode("entry").attribute("key", key)
                                    asShortObject(o, xml, referencedIdProperty, referencedDomainClass)
                                    xml.end()
                                }
                            }
                        }
                    }
                }
            }
            xml.end()
        }
        propertiesMap."${configName}"?.include?.each {
            if (value.properties."${it}" != null) {
                xml.startNode(it)
                xml.convertAnother(value.properties."${it}")
                xml.end()
            }
        }
    }

    protected void asShortObject(Object refObj, XML xml, GrailsDomainClassProperty idProperty,
                                 @SuppressWarnings("unused") GrailsDomainClass referencedDomainClass) throws ConverterException {
        Object idValue
        if(proxyHandler instanceof EntityProxyHandler) {

            idValue = ((EntityProxyHandler) proxyHandler).getProxyIdentifier(refObj)
            if(idValue == null) {
                idValue = new BeanWrapperImpl(refObj).getPropertyValue(idProperty.getName())
            }

        }
        else {
            idValue = new BeanWrapperImpl(refObj).getPropertyValue(idProperty.getName())
        }
        xml.attribute("id",String.valueOf(idValue))

        def configName = GrailsNameUtils.getShortName(referencedDomainClass.getName()).toLowerCase()
        propertiesMap."${configName}"?.asShort?.each {
            if (refObj.properties."${it}" != null) {
                xml.startNode(it)
                xml.convertAnother(refObj.properties."${it}")
                xml.end()
            }
        }
    }
}