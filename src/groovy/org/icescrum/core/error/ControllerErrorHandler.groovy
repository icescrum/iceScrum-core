package org.icescrum.core.error

import grails.converters.JSON
import grails.validation.ValidationException
import org.hibernate.ObjectNotFoundException
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException

trait ControllerErrorHandler {

    // Error rendering
    def returnError = { Map attrs ->
        def error = attrs.errors ? attrs.errors.allErrors.collect { [code: "${controllerName}.${it.field}", text: message(error: it)] } :
                attrs.code ? [text: message(code: attrs.code)] :
                        attrs.text ? [text: attrs.text] :
                                attrs.exception?.message ? [text: attrs.exception.message] :
                                        [text: 'An unexpected error has occurred']
        if (attrs.exception) {
            if (log.debugEnabled) {
                log.debug(attrs.exception)
                log.debug(attrs.exception.cause)
                attrs.exception.stackTrace.each {
                    log.debug(it)
                }
            } else if (log.errorEnabled) {
                log.error(attrs.exception)
                log.error(attrs.exception.cause)
                attrs.exception.stackTrace.each {
                    log.error(it)
                }
            }
        }
        if (attrs.silent) {
            error.silent = true
        }
        render(status: 400, contentType: 'application/json', text:error as JSON)
    }

    // Exception handlers

    def validationException(ValidationException validationException) {
        returnError(errors: validationException.errors)
    }

    def objectNotFoundException(ObjectNotFoundException objectNotFoundException) {
        def identifierString = "unknown"
        try {
            identifierString = objectNotFoundException.identifier instanceof String ? objectNotFoundException.identifier : objectNotFoundException.identifier.join(', ')
        } catch (Throwable) {}
        returnError(text: message(code: 'is.error.object.not.found', args: [objectNotFoundException.entityName, identifierString]))
    }

    def hibernateOptimisticLockingFailureException(HibernateOptimisticLockingFailureException hibernateOptimisticLockingFailureException) {
        def classString = "unknown"
        try {
            classString = hibernateOptimisticLockingFailureException.persistentClassName.split('\\.').last()
        } catch (Throwable) {}
        returnError(text: message(code: 'is.error.object.concurrent.modification', args: [classString, hibernateOptimisticLockingFailureException.identifier]))
    }

    def businessException(BusinessException businessException) {
        returnError(text: message(code: businessException.code, args: businessException.args ?: []))
    }
}
