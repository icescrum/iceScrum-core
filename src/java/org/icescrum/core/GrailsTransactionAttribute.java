package org.icescrum.core;

import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.interceptor.RollbackRuleAttribute;
import org.springframework.transaction.interceptor.RuleBasedTransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttribute;

import java.util.List;

/**
 * This code is a copy/paste from the one in grails 2.5.1 + an override of
 * `rollbackOn` method.
 *
 * It's the only way that I know of to fix `DomainClass.withTransaction` to
 * rollback on any java checked exception (such as java.sql.SQLException).
 *
 * This hack relies on class loading priority. That is, when a grails
 * application has `rollback-on-exception` plugin installed, JVM will pick up
 * this (compiled) class instead of the one in grails JAR file.
 *
 * Refs:
 * - https://github.com/grails/grails-core/blob/v2.5.1/grails-core/src/main/groovy/org/codehaus/groovy/grails/transaction/GrailsTransactionAttribute.java
 */
public class GrailsTransactionAttribute extends RuleBasedTransactionAttribute {
    private static final long serialVersionUID = 1L;
    private boolean inheritRollbackOnly = true;

    public GrailsTransactionAttribute() {
        super();
    }

    public GrailsTransactionAttribute(int propagationBehavior, List<RollbackRuleAttribute> rollbackRules) {
        super(propagationBehavior, rollbackRules);
    }

    public GrailsTransactionAttribute(TransactionAttribute other) {
        super();
        setPropagationBehavior(other.getPropagationBehavior());
        setIsolationLevel(other.getIsolationLevel());
        setTimeout(other.getTimeout());
        setReadOnly(other.isReadOnly());
        setName(other.getName());
    }

    public GrailsTransactionAttribute(TransactionDefinition other) {
        super();
        setPropagationBehavior(other.getPropagationBehavior());
        setIsolationLevel(other.getIsolationLevel());
        setTimeout(other.getTimeout());
        setReadOnly(other.isReadOnly());
        setName(other.getName());
    }

    public GrailsTransactionAttribute(GrailsTransactionAttribute other) {
        this((RuleBasedTransactionAttribute)other);
    }

    public GrailsTransactionAttribute(RuleBasedTransactionAttribute other) {
        super(other);
        if(other instanceof GrailsTransactionAttribute) {
            this.inheritRollbackOnly = ((GrailsTransactionAttribute)other).inheritRollbackOnly;
        }
    }

    public boolean isInheritRollbackOnly() {
        return inheritRollbackOnly;
    }

    public void setInheritRollbackOnly(boolean inheritRollbackOnly) {
        this.inheritRollbackOnly = inheritRollbackOnly;
    }

    /**
     * This is the only change in this class WRT the same class in grails 2.5.1.
     * This makes sure that rollback is initiated whenever any throwable occurs.
     */
    @Override
    public boolean rollbackOn(Throwable e) {
        return true;
    }

}
