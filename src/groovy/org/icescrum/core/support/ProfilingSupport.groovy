/*
 * Copyright (c) 2020 Kagilum SAS
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
 */

package org.icescrum.core.support


import org.apache.commons.logging.LogFactory

import java.util.concurrent.ConcurrentHashMap

class ProfilingSupport {

    private static final log = LogFactory.getLog(this)
    private static profilingDataByThread = new ConcurrentHashMap<Long, HashMap<String, Map>>()

    static void enableProfiling(ajax, controllerName, actionName) {
        def threadId = Thread.currentThread().getId()
        log.info("[Profiler-$threadId] Enable profiling for ${ajax ? 'ajax' : ''} request $controllerName/$actionName")
        profilingDataByThread.putIfAbsent(threadId, new HashMap<String, Map>())
        startProfiling('total', 'total')
    }

    static void startProfiling(String name, String group) {
        def threadId = Thread.currentThread().getId()
        def threadData = profilingDataByThread.get(threadId)
        if (threadData != null) {
            def profilingId = group + '-' + name
            def profilingData = threadData[profilingId]
            if (profilingData && !profilingData.end) {
                log.info("[Profiler-$threadId] [$profilingId]\t Error profiling already started, reset, may not be accurate")
            } else {
                profilingData = [spent: profilingData?.spent ?: 0, cycles: profilingData?.cycles ?: 0]
                threadData[profilingId] = profilingData
            }
            profilingData.group = group
            profilingData.start = new Date().getTime()
            profilingData.end = null
        }
    }

    static void endProfiling(String name, String group) {
        def threadId = Thread.currentThread().getId()
        def threadData = profilingDataByThread.get(threadId)
        if (threadData != null) {
            def profilingId = group + '-' + name
            def profilingData = threadData[profilingId]
            if (profilingData) {
                profilingData.end = new Date().getTime()
                def spent = profilingData.end - profilingData.start
                profilingData.spent += spent
                profilingData.cycles++
                if (spent > 5) {
                    log.info("[Profiler-$threadId] [$profilingId]\t ${spent}ms")
                }
            } else {
                log.info("[Profiler-$threadId] [$profilingId]\t Error profiling not started")
            }
        }
    }

    static void reportProfiling() {
        endProfiling('total', 'total')
        def threadId = Thread.currentThread().getId()
        def threadData = profilingDataByThread.remove(threadId)
        if (threadData != null) {
            log.info('***')
            log.info("[Profiler-$threadId] Start report")
            threadData.sort { it.value.spent }.each { profilingId, profilingData ->
                if (profilingData.spent > 5) {
                    log.info("[Profiler-$threadId] [$profilingId]\t ${profilingData.spent}ms")
                }
            }
            log.info('* by group *')
            threadData.groupBy { it.value.group }?.collect { group, entries ->
                [group: group, sum: entries*.value*.spent.sum(), cycles: entries*.value*.cycles.sum()]
            }?.sort { it.sum }?.each {
                if (it.sum > 5) {
                    log.info("[Profiler-$threadId] [$it.group]\t $it.cycles times = ${it.sum}ms")
                }
            }
            log.info('[Profiler] End report')
            log.info('***')
        }
    }

    static void clearProfiling() {
        profilingDataByThread.clear()
    }
}
