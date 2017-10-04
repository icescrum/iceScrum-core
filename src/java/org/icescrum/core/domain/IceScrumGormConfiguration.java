package org.icescrum.core.domain;

import org.codehaus.groovy.grails.orm.hibernate.cfg.GrailsAnnotationConfiguration;
import org.hibernate.MappingException;
import org.hibernate.mapping.*;

import java.util.Arrays;
import java.util.Comparator;
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
        for (PersistentClass pc : classes.values()) {
            if (pc instanceof RootClass) {
                RootClass root = (RootClass) pc;
                Table table = root.getTable();
                for (Iterator iter = table.getForeignKeyIterator(); iter.hasNext(); ) {
                    ForeignKey fk = (ForeignKey) iter.next();
                    String fkName;
                    // Avoid duplicates by adding the reference table name in the constraint name generation proces
                    // Duplicates happen when FK on timebox because it generates not one but two FK for the same table and column, e.g. one to is_timebox and one to is_release
                    // Be careful, foreign keys in join table (many to many) don't use this method to generate key names
                    if ("true".equals(System.getProperty("icescrum.oracle"))) {
                        System.out.println("Oracle key naming");
                        // New naming that avoids duplicate while staying under 30 characters
                        fkName = generateName(table, fk);
                    } else {
                        System.out.println("Normal key naming");
                        // Below is legacy naming that avoids duplicated but exceeds 30 characters
                        String tableName = fk.getReferencedTable().getName().replaceAll("_", "");
                        fkName = fk.getName() + tableName;
                    }
                    fk.setName(fkName);
                }
                UniqueKey nameKey = table.getUniqueKey("unique_name");
                if (nameKey != null) {
                    String tableName = table.getName().replaceAll("_", "");
                    nameKey.setName(nameKey.getName() + tableName);
                }
            }
        }
        _alreadyProcessed = true;
    }

    // Copies org.hibernate.mapping.Constraint behavior to stay under 30 chars but adds the reference table name in the hash generation
    private static String generateName(Table table, ForeignKey fk) {
        StringBuilder sb = new StringBuilder("table`" + table.getName() + "`");
        Column[] alphabeticalColumns = (Column[]) fk.getColumns().toArray(new Column[0]);
        Arrays.sort(alphabeticalColumns, new Comparator<Column>() {
            @Override
            public int compare(Column col1, Column col2) {
                return col1.getName().compareTo(col2.getName());
            }
        });
        for (Column column : alphabeticalColumns) {
            String columnName = column == null ? "" : column.getName();
            sb.append("column`" + columnName + "`");
            sb.append(fk.getReferencedTable().getName()); // This is the customized part that allows to avoid duplicates
        }
        return "FK" + Constraint.hashedName(sb.toString());
    }
}