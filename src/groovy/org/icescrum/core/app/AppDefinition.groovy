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

import grails.util.Holders

class AppDefinition {

    boolean hasWidgets = false
    boolean hasWindows = false
    boolean isProject = false
    boolean isNew = false
    String id
    String logo = 'logo.png'
    String version
    String author
    String docUrl
    String websiteUrl
    List<String> screenshots = []
    List<String> tags = []
    Closure reportUsageData
    Closure onEnableForProject
    Closure onDisableForProject
    Closure<Boolean> isEnabledForServer
    Closure<Boolean> isAvailableForServer
    AppSettingsDefinition projectSettings

    AppDefinition(String id) {
        this.id = id
    }

    // Builder

    void logo(String logo) {
        this.logo = logo
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

    void isProject(boolean isProject) {
        this.isProject = isProject
    }

    void isNew(boolean isNew) {
        this.isNew = isNew
    }

    void reportUsageData(Closure reportUsageData) {
        this.reportUsageData = reportUsageData
    }

    void onEnableForProject(Closure onEnableForProject) {
        this.onEnableForProject = onEnableForProject
    }

    void onDisableForProject(Closure onDisableForProject) {
        this.onDisableForProject = onDisableForProject
    }

    void isEnabledForServer(Closure<Boolean> isEnabledForServer) {
        this.isEnabledForServer = isEnabledForServer
    }

    void isAvailableForServer(Closure<Boolean> isAvailableForServer) {
        this.isAvailableForServer = isAvailableForServer
    }

    void projectSettings(@DelegatesTo(strategy = Closure.DELEGATE_ONLY, value = AppSettingsDefinition) Closure settingsClosure) {
        AppSettingsDefinition projectSettings = new AppSettingsDefinition()
        AppDefinitionsBuilder.builObjectFromClosure(projectSettings, settingsClosure, this)
        this.projectSettings = projectSettings
    }

    // Getters

    boolean getAvailableForServer() {
        return isAvailableForServer ? isAvailableForServer(Holders.grailsApplication) : true
    }

    boolean getEnabledForServer() {
        return isEnabledForServer ? isEnabledForServer(Holders.grailsApplication) : true
    }

    // Utility

    Map validate() {
        def result = [valid: false, errorMessage: "Error, this app definition cannot be registered: $id"]
        if (!id || !docUrl || !version || !author) {
            result.errorMessage += '\n - These fields are required: docUrl, version, author'
        } else if ((onEnableForProject || onDisableForProject || projectSettings) && !isProject) {
            result.errorMessage += '\n - The fields onEnableForProject, onDisableForProject and projectSettings can be defined only if isProject is true'
        } else {
            result.valid = true;
        }
        return result
    }

    String getAssetPath(String fileName) {
        return "apps/$id/$fileName"
    }
}
