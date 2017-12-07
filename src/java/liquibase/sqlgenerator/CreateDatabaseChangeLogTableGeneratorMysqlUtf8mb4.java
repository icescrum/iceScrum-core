package liquibase.sqlgenerator;

import liquibase.database.Database;
import liquibase.database.core.MySQLDatabase;
import liquibase.sqlgenerator.core.CreateDatabaseChangeLogTableGenerator;
import liquibase.statement.core.CreateDatabaseChangeLogTableStatement;
import org.icescrum.core.support.ApplicationSupport;

public class CreateDatabaseChangeLogTableGeneratorMysqlUtf8mb4 extends CreateDatabaseChangeLogTableGenerator {
    public CreateDatabaseChangeLogTableGeneratorMysqlUtf8mb4() {
    }

    public int getPriority() {
        return 5;
    }

    public boolean supports(CreateDatabaseChangeLogTableStatement statement, Database database) {
        return database instanceof MySQLDatabase && ApplicationSupport.isMySQLUTF8mb4();
    }

    protected String getFilenameColumnSize() {
        return "191";
    }
}