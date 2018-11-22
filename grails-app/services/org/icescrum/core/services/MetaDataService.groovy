/*
 * Copyright (c) 2018 Kagilum SAS.
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
package org.icescrum.core.services

import grails.converters.JSON
import org.icescrum.core.domain.MetaData
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.utils.DateUtils

class MetaDataService extends IceScrumEventPublisher {

    void addOrUpdateMetadata(def object, String metaKey, String metaValue) {
        def type = object.class.name
        def meta = MetaData.findByParentRefAndParentTypeAndMetaKey(object.id, type, metaKey)
        if (meta) {
            meta.metaValue = metaValue
        } else {
            meta = new MetaData(parentRef: object.id, parentType: type, metaKey: metaKey, metaValue: metaValue)
        }
        meta.save()
    }

    void addOrUpdateMetadata(def object, String metaKey, def metaValue) {
        addOrUpdateMetadata(object, metaKey, (metaValue as JSON).toString())
    }

    def getMetadata(def object, String metaKey, boolean isJSON) {
        def meta = MetaData.findByParentRefAndParentTypeAndMetaKey(object.id, object.class.name, metaKey)
        return meta ? (isJSON ? JSON.parse(meta.metaValue) : meta.metaValue) : null
    }

    def unMarshall(def metaDataXml, def options) {
        def parent = options.parent
        MetaData.withTransaction(readOnly: !options.save) { transaction ->
            def metaData = new MetaData(
                    metaKey: metaDataXml.metaKey.text(),
                    metaValue: metaDataXml.metaValue.text(),
                    parentType: metaDataXml.parentType.text()
            )
            // References to object
            if (parent) {
                metaData.parentRef = parent.id
            }
            // Save before some hibernate stuff
            if (options.save) {
                metaData.save()
                //can't be in constructor
                metaData.dateCreated = DateUtils.parseDateFromExport(metaDataXml.dateCreated.text())
                metaData.save()
                if (parent) {
                    parent.addToMetaDatas(metaData)
                }
            }
            return (MetaData) importDomainsPlugins(metaDataXml, metaData, options)
        }
    }
}
