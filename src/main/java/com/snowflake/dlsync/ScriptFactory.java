package com.snowflake.dlsync;

import com.snowflake.dlsync.models.*;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class ScriptFactory {

    public static AccountScript getAccountScript(String scriptPath, ScriptObjectType objectType, String objectName, String content) {
        return new AccountScript(scriptPath, objectName, objectType, content);
    }

    public static SchemaScript getSchemaScript(String scriptPath, String databaseName, String schemaName, ScriptObjectType objectType, String objectName, String content) {
        return new SchemaScript(scriptPath, databaseName, schemaName, objectName, objectType, content);
    }

    public static SchemaScript getSchemaScript(String databaseName, String schemaName, ScriptObjectType objectType, String objectName, String content) {
        return getSchemaScript(null, databaseName, schemaName, objectType, objectName, content);
    }

    public static MigrationScript getMigrationScript(Script parentScript, String content, Long version, String author, String rollback, String verify) {
        return new MigrationScript(parentScript, content, version, author, rollback, verify);
    }

    public static MigrationScript getSchemaMigrationScript(String databaseName, String schemaName, ScriptObjectType objectType, String objectName, String content, Long version, String author, String rollback, String verify) {
        SchemaScript schemaScript = getSchemaScript(databaseName, schemaName, objectType, objectName, content);
        return new MigrationScript(schemaScript, content, version, author, rollback, verify);
    }

    public static MigrationScript getSchemaMigrationScript(String fullObjectName, ScriptObjectType objectType, String content, Long version, String author, String rollback, String verify) {
        String databaseName = null, schemaName = null, objectName = null;
        String[] nameSplit = fullObjectName.split("\\.");
        if(nameSplit.length > 2) {
            databaseName = nameSplit[0];
            schemaName = nameSplit[1];
            objectName = nameSplit[2];
        }
        else {
            log.error("Error while splitting fullObjectName {}: Missing some values", fullObjectName);
            throw new RuntimeException("Error while splitting fullObjectName");
        }
        return getSchemaMigrationScript(databaseName, schemaName, objectType, objectName, content, version, author, rollback, verify);

    }



    public static MigrationScript getMigrationScript(Script parentScript, Migration migration) {
        return new MigrationScript(parentScript, migration.getContent(), migration.getVersion(), migration.getAuthor(), migration.getRollback(), migration.getVerify());
    }

    public static List<MigrationScript> getMigrationScripts(Script parentScript, List<Migration> migrations) {
        if(!parentScript.getObjectType().isMigration()) {
            log.error("Script {} is not a migration script.", parentScript);
            return null;
        }
        List<MigrationScript> migrationScripts = new ArrayList<>();
        for(Migration migration: migrations) {
            MigrationScript script = ScriptFactory.getMigrationScript(parentScript, migration);
            if(migrationScripts.contains(script)) {
                log.error("Duplicate version {} for script {} found.", script.getVersion(), script);
                throw new RuntimeException("Duplicate version number is not allowed in the same script file.");
            }
            migrationScripts.add(script);
        }
        return migrationScripts;
    }

    public static TestScript getTestScript(String scriptPath, String objectName, String content, Script mainScript) {
        if (mainScript instanceof SchemaScript) {
            SchemaScript schemaScript = (SchemaScript) mainScript;
            return new TestScript(scriptPath, objectName, schemaScript.getObjectType(), content, schemaScript);
        }
        return null;
    }
}
