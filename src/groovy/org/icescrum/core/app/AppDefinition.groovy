/*
 * Copyright (c) 2017 Kagilum SAS
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
 * Nicolas Noullet (nnoullet@kagilum.com)
 * Vincent Barrier (vbarrier@kagilum.com)
 *
 */
package org.icescrum.core.app

class AppDefinition {
    boolean hasWidgets = false
    boolean hasWindows = false
    boolean hasProjectSettings = false
    boolean isSimple = true
    boolean isProject = false
    boolean isServer = false
    String id
    String name
    String logo
    String description
    String version
    String author
    String docUrl
    String websiteUrl
    String baseline
    List<String> screenshots = []
    List<String> tags = []
    Closure<Boolean> isEnabledForProject
    Closure onEnableForProject
    Closure onDisableForProject

    void name(String name) {
        this.name = name
    }

    void logo(String logo) {
        this.logo = logo
    }

    void description(String description) {
        this.description = description
    }

    void version(String version) {
        this.version = version
    }

    void author(String author) {
        this.author = author
    }

    void docUrl(String docUrl) {
        this.docUrl = docUrl
    }

    void websiteUrl(String websiteUrl) {
        this.websiteUrl = websiteUrl
    }

    void baseline(String baseline) {
        this.baseline = baseline
    }

    void screenshots(String... screenshots) {
        this.screenshots.addAll(screenshots)
    }

    void tags(String... tags) {
        this.tags.addAll(tags)
        this.tags.unique()
    }

    void hasWidgets(boolean hasWidgets) {
        this.hasWidgets = hasWidgets
    }

    void hasWindows(boolean hasWindows) {
        this.hasWindows = hasWindows
    }

    void hasProjectSettings(boolean hasProjectSettings) {
        this.hasProjectSettings = hasProjectSettings
    }

    void isSimple(boolean isSimple) {
        this.isSimple = isSimple
    }

    void isProject(boolean isProject) {
        this.isProject = isProject
    }

    void isServer(boolean isServer) {
        this.isServer = isServer
    }

    void isEnabledForProject(Closure<Boolean> isEnabledForProject) {
        this.isEnabledForProject = isEnabledForProject
    }

    void onEnableForProject(Closure onEnableForProject) {
        this.onEnableForProject = onEnableForProject
    }

    void onDisableForProject(Closure onDisableForProject) {
        this.onDisableForProject = onDisableForProject
    }
}
