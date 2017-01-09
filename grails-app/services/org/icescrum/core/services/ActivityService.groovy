/*
 * Copyright (c) 2014 Kagilum SAS.
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

import grails.util.GrailsNameUtils
import org.hibernate.proxy.HibernateProxyHelper
import org.icescrum.core.domain.Activity
import org.icescrum.core.domain.User
import org.icescrum.core.event.IceScrumEventPublisher
import org.icescrum.core.event.IceScrumEventType

import java.text.SimpleDateFormat

class ActivityService extends IceScrumEventPublisher {

    Activity addActivity(Object item, User poster, String code, String label, String field = null, String beforeValue = null, String afterValue = null) {
        if (item.id == null) {
            throw new RuntimeException("You must save the entity [${item}] before calling addActivity")
        }
        def itemClass = HibernateProxyHelper.getClassWithoutInitializingProxy(item)
        def itemType = GrailsNameUtils.getPropertyName(itemClass)
        def activity = new Activity(poster: poster, parentRef: item.id, parentType: itemType,
                                    code: code, label: label, field: field, beforeValue: beforeValue, afterValue: afterValue)
        activity.save()
        item.addToActivities(activity)
        publishSynchronousEvent(IceScrumEventType.CREATE, activity)
        return activity
    }

    void removeAllActivities(Object item) {
        if (item.activities) {
            def activitiesToDelete = []
            activitiesToDelete.addAll(item.activities)
            activitiesToDelete.each { activity ->
                item.removeFromActivities(activity)
                activity.delete()
            }
        }
    }

    def unMarshall(def activityXml, def options) {
        def parent = options.parent
        def project = options.project
        Activity.withTransaction(readOnly: !options.save) { transaction ->
            try {
                def activity = new Activity(
                        dateCreated: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(activityXml.dateCreated.text()),
                        lastUpdated: new SimpleDateFormat('yyyy-MM-dd HH:mm:ss').parse(activityXml.lastUpdated.text()),
                        code: activityXml.code.text(),
                        label: activityXml.label.text(),
                        field: activityXml.field.text(),
                        afterValue: activityXml.afterValue.text(),
                        beforeValue: activityXml.beforeValue.text(),
                        parentType: activityXml.parentType.text()
                )
                // References to object
                if (project) {
                    def u = ((User) project.getAllUsers().find { it.uid == activityXml.poster.@uid.text() }) ?: null
                    activity.poster = (User) (u ?: project.productOwners.first())
                }
                // Save before some hibernate stuff
                if (options.save) {
                    activity.save()
                }
                if (parent) {
                    activity.parentRef = parent.id
                    parent.addToActivities(activity)
                }
                if (options.save) {
                    activity.save()
                }
                return (Activity) importDomainsPlugins(activityXml, activity, options)
            } catch (Exception e) {
                if (log.debugEnabled) {
                    e.printStackTrace()
                }
                throw new RuntimeException(e)
            }
        }
    }
}
