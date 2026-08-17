/*
 * Copyright (c) 2026 iceScrum.
 *
 * This file is part of iceScrum.
 *
 * iceScrum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License.
 */

package org.icescrum.core.hook

final class EventMessageRendererFactory {

    private static final String DEFAULT_RENDERER = DefaultEventMessageRenderer.name

    private EventMessageRendererFactory() {}

    static EventMessageRenderer create(String rendererClassName) {
        String className = rendererClassName ?: DEFAULT_RENDERER
        ClassLoader classLoader = Thread.currentThread().contextClassLoader ?: EventMessageRendererFactory.classLoader
        Class rendererClass = Class.forName(className, false, classLoader)
        if (!EventMessageRenderer.isAssignableFrom(rendererClass)) {
            throw new IllegalArgumentException('Hook renderer must implement EventMessageRenderer')
        }
        return (EventMessageRenderer) rendererClass.newInstance()
    }
}
