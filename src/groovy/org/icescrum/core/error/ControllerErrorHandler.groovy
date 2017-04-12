package org.icescrum.core.error

import grails.converters.JSON
import grails.validation.ValidationException
import org.hibernate.ObjectNotFoundException
import org.icescrum.core.support.ApplicationSupport
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException

trait ControllerErrorHandler {

    /**
     * Error handling for controllers and rendering to the user
     * @param text      Text message to return to the user, don't provide i18n but use directly the "code" shorthand instead
     * @param code      i18n message code to be formatted and returned to the user
     * @param exception Exception to log, useful for unexpected errors (e.g. from third party service) but not for business errors that would pollute the logs
     * @param errors    Grails domain validation errors to be formatted and returned to the user
     * @param silent    Option to return the error to the browser but don't display it to the user, useful for custom display or to swallow the error
     */
    def returnError = { Map attrs ->
        def error = ApplicationSupport.extractError(attrs)
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
        render(status: 400, contentType: 'application/json', text: error as JSON)
    }

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
        returnError(text: businessException.text ?: message(code: businessException.code, args: businessException.args ?: []))
    }
}
