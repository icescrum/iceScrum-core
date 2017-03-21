package org.icescrum.core.app

import grails.util.Holders
import org.slf4j.LoggerFactory

class AppDefinitionsBuilder {

    static private final log = LoggerFactory.getLogger(this.class.name)

    static void apps(@DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppDefinitions) Closure appDefinitionsClosure) {
        AppDefinitions appDefinitions = new AppDefinitions()
        builObjectFromClosure(appDefinitions, appDefinitionsClosure, this)
        List<AppDefinition> definitions = appDefinitions.definitions
        if (appDefinitions.shared) { // Override each appDefinition fields with shared ones
            definitions.each { AppDefinition appDefinition ->
                builObjectFromClosure(appDefinition, appDefinitions.shared, this)
            }
        }
        definitions.each { AppDefinition appDefinition ->
            def validationResult = appDefinition.validate()
            if (!validationResult.valid) {
                log.error(validationResult.errorMessage)
            }
        }
        Holders.grailsApplication.mainContext.appDefinitionService.registerAppDefinitions(definitions)
    }

    static void builObjectFromClosure(Object objectToBuild, Closure cl, Object context) {
        Closure buildObject = cl.rehydrate(objectToBuild, context, context)
        buildObject.resolveStrategy = Closure.DELEGATE_ONLY
        buildObject()
    }

    static class AppDefinitions {

        List<AppDefinition> definitions = []
        Closure shared

        void app(String id, @DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppDefinition) Closure appDefinitionClosure) {
            AppDefinition appDefinition = new AppDefinition(id: id)
            builObjectFromClosure(appDefinition, appDefinitionClosure, this)
            definitions << appDefinition
        }

        void shared(@DelegatesTo(strategy=Closure.DELEGATE_ONLY, value=AppDefinition) Closure sharedClosure) {
            this.shared = sharedClosure
        }
    }
}
