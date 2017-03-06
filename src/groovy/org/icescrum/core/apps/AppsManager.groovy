package org.icescrum.core.apps

import grails.util.Holders

class AppsManager {

    static void apps(@DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppList) Closure cl) {
        AppList appList = new AppList()
        Closure appListClosure = cl.rehydrate(appList, this, this)
        appListClosure.resolveStrategy = Closure.DELEGATE_ONLY
        appListClosure()
        List<App> apps = appList.appList
        if (appList.common) {
            apps.each { App app ->
                Closure appClosure = appList.common.rehydrate(app, this, this)
                appClosure.resolveStrategy = Closure.DELEGATE_ONLY
                appClosure()
            }
        }
        Holders.grailsApplication.mainContext.appsService.registerApps(apps)
    }

    static class AppList {

        List<App> appList = []
        Closure common

        void app(String id, @DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=App) Closure cl) {
            App app = new App(id: id)
            Closure appClosure = cl.rehydrate(app, this, this)
            appClosure.resolveStrategy = Closure.DELEGATE_ONLY
            appClosure()
            appList << app
        }

        void common(@DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=App) Closure cl) {
            this.common = cl
        }
    }
}
