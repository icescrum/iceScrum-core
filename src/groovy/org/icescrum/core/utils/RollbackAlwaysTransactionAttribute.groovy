package org.icescrum.core.utils

import org.springframework.transaction.interceptor.DefaultTransactionAttribute

/**
 * Suggest rollback on any throwable.
 * 
 * @author Miro Bezjak
 * @since 2011-07-08
 * @version 0.1
 */
class RollbackAlwaysTransactionAttribute extends DefaultTransactionAttribute {

    boolean rollbackOn(Throwable e) {
        true
    } 

}
