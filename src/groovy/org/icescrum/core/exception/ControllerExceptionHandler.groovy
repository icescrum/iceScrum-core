package org.icescrum.core.exception

import grails.validation.ValidationException
import org.hibernate.ObjectNotFoundException
import org.springframework.orm.hibernate4.HibernateOptimisticLockingFailureException

trait ControllerExceptionHandler {

    def validationException(ValidationException validationException) {
        returnError(errors: validationException.errors)
    }

    def objectNotFoundException(ObjectNotFoundException objectNotFoundException) {
        def identifierString = "unknown"
        try {
            identifierString = objectNotFoundException.identifier.join(', ')
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
