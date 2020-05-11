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
    private static profilingDataByThread = new ConcurrentHashMap<Long, HashMap<String, ProfilingData>>()

    static void enableProfiling(ajax, controllerName, actionName) {
        def threadId = Thread.currentThread().getId()
        log.info('***')
        log.info("[Profiler-$threadId] Enable profiling for ${ajax ? 'ajax' : ''} request $controllerName/$actionName")
        profilingDataByThread.putIfAbsent(threadId, new HashMap<String, ProfilingData>())
        startProfiling('total', 'total')
    }

    static void startProfiling(String name, String group) {
        def threadId = Thread.currentThread().getId()
        def threadData = profilingDataByThread.get(threadId)
        if (threadData != null) {
            def profilingId = group + '-' + name
            def profilingData = threadData[profilingId]
            if (!profilingData) {
                profilingData = new ProfilingData(group: group)
                threadData[profilingId] = profilingData
            } else if (profilingData.start) {
                log.info("[Profiler-$threadId] [$profilingId]\t Error profiling already in progress on this ID, the values will not be accurate")
            }
            profilingData.start = new Date().getTime()
        }
    }

    static void endProfiling(String name, String group) {
        def threadId = Thread.currentThread().getId()
        def threadData = profilingDataByThread.get(threadId)
        if (threadData != null) {
            def profilingId = group + '-' + name
            def profilingData = threadData[profilingId]
            if (profilingData && profilingData.start) {
                profilingData.spent << (new Date().getTime()) - profilingData.start
                profilingData.start = null
            } else {
                log.info("[Profiler-$threadId] [$profilingId]\t Error profiling not started on this ID")
            }
        }
    }

    static void reportProfiling() {
        endProfiling('total', 'total')
        def threadId = Thread.currentThread().getId()
        def threadData = profilingDataByThread.remove(threadId)
        if (threadData != null) {
            log.info("* details ")
            threadData.sort { it.value.totalSpent }.each { profilingId, profilingData ->
                def totalSpent = profilingData.totalSpent
                if (totalSpent > 5) {
                    log.info("[Profiler-$threadId] [$profilingId]\t ${totalSpent}ms")
                    if (profilingData.spent.size() > 1) {
                        profilingData.spent.each {
                            log.info("[Profiler-$threadId] [$profilingId]\t --${it}ms")
                        }
                    }
                }
            }
            log.info('* by group *')
            threadData.groupBy { it.value.group }?.collect { group, entries ->
                [group: group, totalSpentByGroup: entries*.value.sum { it.totalSpent }, cycles: entries*.value.sum { it.spent.size() }]
            }?.sort { it.totalSpentByGroup }?.each {
                if (it.totalSpentByGroup > 5) {
                    log.info("[Profiler-$threadId] [$it.group]\t ${it.cycles > 1 ? "(x$it.cycles)" : ''} ${it.totalSpentByGroup}ms")
                }
            }
            log.info('***')
        }
    }

    static void clearProfiling() {
        profilingDataByThread.clear()
    }

    private static class ProfilingData {
        String group
        List<Long> spent = []
        Long start

        Long getTotalSpent() {
            return spent ? spent.sum() : 0
        }
    }
}
