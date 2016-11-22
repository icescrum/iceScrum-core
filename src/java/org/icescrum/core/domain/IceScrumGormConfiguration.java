package org.icescrum.core.domain;

import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsAnnotationConfiguration;
import org.hibernate.MappingException;
import org.hibernate.mapping.ForeignKey;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.mapping.RootClass;
import org.hibernate.mapping.UniqueKey;

import java.util.Collection;
import java.util.Iterator;

public class IceScrumGormConfiguration extends GrailsAnnotationConfiguration {

    private static final long serialVersionUID = 1;

    private boolean _alreadyProcessed;

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    protected void secondPassCompile() throws MappingException {
        super.secondPassCompile();
        if (_alreadyProcessed) {
            return;
        }
        for (PersistentClass pc : (Collection<PersistentClass>) classes.values()) {
            if (pc instanceof RootClass) {
                RootClass root = (RootClass) pc;
                for (Iterator iter = root.getTable().getForeignKeyIterator(); iter.hasNext(); ) {
                    ForeignKey fk = (ForeignKey) iter.next();
                    String tableName = fk.getReferencedTable().getName().replaceAll("_", "");
                    fk.setName(fk.getName() + tableName);
                }
                UniqueKey nameKey = root.getTable().getUniqueKey("unique_name");
                if (nameKey != null) {
                    String tableName = root.getTable().getName().replaceAll("_", "");
                    nameKey.setName(nameKey.getName() + tableName);
                }
            }
        }
        _alreadyProcessed = true;
    }
}