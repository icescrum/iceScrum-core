package org.icescrum.core.app

import grails.util.Holders

class AppDefinitionsBuilder {

    static void apps(@DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppDefinitions) Closure cl) {
        AppDefinitions appDefinitions = new AppDefinitions()
        Closure appDefinitionsClosure = cl.rehydrate(appDefinitions, this, this)
        appDefinitionsClosure.resolveStrategy = Closure.DELEGATE_ONLY
        appDefinitionsClosure()
        List<AppDefinition> definitions = appDefinitions.definitions
        if (appDefinitions.shared) { // Override each appDefinition fields with shared ones
            definitions.each { AppDefinition appDefinition ->
                Closure appDefinitionClosure = appDefinitions.shared.rehydrate(appDefinition, this, this)
                appDefinitionClosure.resolveStrategy = Closure.DELEGATE_ONLY
                appDefinitionClosure()
            }
        }
        Holders.grailsApplication.mainContext.appDefinitionService.registerAppDefinitions(definitions)
    }

    static class AppDefinitions {

        List<AppDefinition> definitions = []
        Closure shared

        void app(String id, @DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppDefinition) Closure cl) {
            AppDefinition appDefinition = new AppDefinition(id: id)
            Closure appDefinitionClosure = cl.rehydrate(appDefinition, this, this)
            appDefinitionClosure.resolveStrategy = Closure.DELEGATE_ONLY
            appDefinitionClosure()
            definitions << appDefinition
        }

        void shared(@DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppDefinition) Closure cl) {
            this.shared = cl
        }
    }
}
