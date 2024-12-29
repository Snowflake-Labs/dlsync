package com.snowflake.dlsync.doa;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.Util;
import com.snowflake.dlsync.dependency.DependencyExtractor;
import com.snowflake.dlsync.models.*;
import com.snowflake.dlsync.parser.SqlTokenizer;
import lombok.extern.slf4j.Slf4j;

import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ScriptRepo {
    private Properties connectionProperties;
    private Connection connection;
    private Map<String, String> scriptHash = new HashMap<>();
    private Long changeSyncId;

    public final String CHANGE_SYNC_TABLE_NAME = "DL_SYNC_CHANGE_SYNC";
    public final String SCRIPT_HISTORY_TABLE_NAME = "DL_SYNC_SCRIPT_HISTORY";
    public final String SCRIPT_EVENT_TABLE_NAME = "DL_SYNC_SCRIPT_EVENT";
    public final String DEPENDENCY_LINEAGE_TABLE_NAME = "DL_SYNC_DEPENDENCY_LINEAGE";


    public ScriptRepo(Properties connectionProperties) {
        log.debug("Repo initialized with the following properties: {}", connectionProperties);
        this.connectionProperties = connectionProperties;
        try {
            openConnection();
            ResultSet resultSet = connection.createStatement().executeQuery("select current_database(), current_schema();");
            resultSet.next();
            log.info("Using database [{}] and schema [{}] for dlsync activities.", resultSet.getString(1), resultSet.getString(2));
            initScriptTables();
        } catch (SQLException e) {
            log.error("Error while initializing the script repo: {} cause {}", e.getMessage(), e.getCause());
            throw new RuntimeException(e);
        }
    }


    private void openConnection() throws SQLException {
        String jdbcUrl = "jdbc:snowflake://" + connectionProperties.getProperty("account") + ".snowflakecomputing.com/";
        connectionProperties.remove("account");
        log.debug("Connection opened with properties: {}", connectionProperties);
        connection = DriverManager.getConnection(jdbcUrl, connectionProperties);
    }

    private void initScriptTables() throws SQLException {
        ////varchar OBJECT_NAME, varchar SCRIPT_HASH, varchar created_by, timestamp created_ts, varchar updated_by, timestamp updated_ts;
        log.debug("Checking for deployment tables");
        try {
            String query = "SELECT * FROM " + CHANGE_SYNC_TABLE_NAME + " LIMIT 1;";
            Statement statement = connection.createStatement();
            statement.executeQuery(query);
            updateOldTableNames();
        } catch (SQLException e) {
            log.info("Running for the first time. Creating required tables.");
            String createChangeSyncSql = "CREATE OR REPLACE TABLE " + CHANGE_SYNC_TABLE_NAME + " (ID integer PRIMARY KEY, CHANGE_TYPE varchar, STATUS varchar, LOG varchar, CHANGE_COUNT integer, START_TIME timestamp, END_TIME timestamp);";

            String createSqlHash = "CREATE OR REPLACE TABLE " + SCRIPT_HISTORY_TABLE_NAME + " (SCRIPT_ID VARCHAR, OBJECT_NAME varchar, OBJECT_TYPE varchar, ROLLBACK_SCRIPT varchar, SCRIPT_HASH varchar, DEPLOYED_HASH varchar, CHANGE_SYNC_ID integer, CREATED_BY varchar, CREATED_TS timestamp, UPDATED_BY varchar, UPDATED_TS timestamp, FOREIGN KEY (CHANGE_SYNC_ID) REFERENCES " + CHANGE_SYNC_TABLE_NAME + "(ID));";

            String createSqlEvent = "CREATE OR REPLACE TABLE " + SCRIPT_EVENT_TABLE_NAME + " (ID VARCHAR, SCRIPT_ID VARCHAR, OBJECT_NAME varchar, SCRIPT_HASH varchar, STATUS varchar, LOG varchar, CHANGE_SYNC_ID integer, CREATED_BY varchar, CREATED_TS timestamp, FOREIGN KEY (CHANGE_SYNC_ID) REFERENCES " + CHANGE_SYNC_TABLE_NAME + "(ID));";
            log.debug("create hash table sql: {}", createSqlHash);
            log.debug("create event table sql: {}", createSqlEvent);
            Statement statement = connection.createStatement();
            statement.executeUpdate(createChangeSyncSql);
            statement.executeUpdate(createSqlHash);
            statement.executeUpdate(createSqlEvent);
        }
    }

    private void updateOldTableNames() {
        try {
            String query = "SELECT * FROM DL_SYNC_SCRIPT LIMIT 1;";
            Statement statement = connection.createStatement();
            statement.executeQuery(query);
            log.info("Found old dlsync table DL_SYNC_SCRIPT renaming it to [{}]", SCRIPT_HISTORY_TABLE_NAME);
            String alterSql = "ALTER TABLE IF EXISTS DL_SYNC_SCRIPT RENAME TO " + SCRIPT_HISTORY_TABLE_NAME + ";";
            statement.executeUpdate(alterSql);
        } catch (SQLException e) {
            log.debug("All tables are with new version");
        }
    }

    public Set<String> loadScriptHash() throws SQLException {
        String hashQuery =  "SELECT * FROM " + SCRIPT_HISTORY_TABLE_NAME + ";";
        log.debug("Loading hash with sql: {}", hashQuery);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(hashQuery);
        while (resultSet.next()) {
            String id = resultSet.getString("SCRIPT_ID");
            scriptHash.put(id, resultSet.getString("SCRIPT_HASH"));
        }
        log.debug("Script hash loaded: {}", scriptHash);
        return  scriptHash.keySet();

    }

    public Set<String> loadDeployedHash() throws SQLException {
        String hashColumn = "DEPLOYED_HASH";
        String hashQuery =  "SELECT * FROM " + SCRIPT_HISTORY_TABLE_NAME + ";";
        log.debug("Loading hash with sql: {}", hashQuery);
        Statement statement = connection.createStatement();
        ResultSet resultSet = statement.executeQuery(hashQuery);
        while (resultSet.next()) {
            String id = resultSet.getString("SCRIPT_ID");
            scriptHash.put(id, resultSet.getString(hashColumn));
        }
        log.debug("Script deployed hash loaded: {}", scriptHash);
        return  scriptHash.keySet();

    }

    public Long insertChangeSync(ChangeType changeType, Status status, String logMessage) throws SQLException {
        String queryGetId = "SELECT count(1) FROM " + CHANGE_SYNC_TABLE_NAME + ";";
        ResultSet rs = connection.createStatement().executeQuery(queryGetId);
        if(rs.next()) {
            changeSyncId =  rs.getLong(1) + 1;
        }

        String insertSql = "INSERT INTO " + CHANGE_SYNC_TABLE_NAME + " (ID, CHANGE_TYPE, STATUS, LOG, START_TIME) VALUES(?, ?, ?, ?, CURRENT_TIMESTAMP);";
        PreparedStatement statement = connection.prepareStatement(insertSql);
        statement.setLong(1, changeSyncId);
        statement.setString(2, changeType.toString());
        statement.setString(3, status.toString());
        statement.setString(4, logMessage);
        log.debug("Creating script event with the following SQL: {}", insertSql);
        statement.executeUpdate();

        return changeSyncId;
    }

    public void updateChangeSync(ChangeType changeType, Status status, String logMessage, Long changeCount) throws SQLException {
        String updateSql = "UPDATE " + CHANGE_SYNC_TABLE_NAME + " SET CHANGE_TYPE=?, STATUS=?, LOG=?, CHANGE_COUNT=?, END_TIME=CURRENT_TIMESTAMP WHERE ID = ? ;";
        PreparedStatement statement = connection.prepareStatement(updateSql);
        statement.setString(1, changeType.toString());
        statement.setString(2, status.toString());
        statement.setString(3, logMessage);
        statement.setObject(4, changeCount);
        statement.setLong(5, changeSyncId);
        log.debug("Creating script event with the following SQL: {}", updateSql);
        statement.executeUpdate();
    }
    private boolean updateScriptHash(Script script) throws SQLException {
        String rollback = null;
        if(script instanceof MigrationScript) {
            MigrationScript migrationScript = (MigrationScript)script;
            rollback = migrationScript.getRollback();
        }
        PreparedStatement statement;
        String deployedHash = Util.getMd5Hash(script.getContent());
        log.debug("Updating script hash of object {}", script.getId());
        if(scriptHash.containsKey(script.getId())) {
            String updateSql = "UPDATE " + SCRIPT_HISTORY_TABLE_NAME + " SET ROLLBACK_SCRIPT=?, SCRIPT_HASH=?, DEPLOYED_HASH=?, CHANGE_SYNC_ID=?, updated_by=current_user, updated_ts=current_timestamp WHERE SCRIPT_ID=?;";
            statement = connection.prepareStatement(updateSql);
            statement.setString(1, rollback);
            statement.setString(2, script.getHash());
            statement.setString(3, deployedHash);
            statement.setLong(4, changeSyncId);
            statement.setString(5, script.getId());
            log.debug("Updating script hash with the following SQL: {}", updateSql);
        }
        else {
            String insertSql = "INSERT INTO " + SCRIPT_HISTORY_TABLE_NAME + " VALUES(?, ?, ?, ?, ?, ?, ?, current_user, current_timestamp, current_user, current_timestamp);";
            statement = connection.prepareStatement(insertSql);
            statement.setString(1, script.getId());
            statement.setString(2, script.getFullObjectName());
            statement.setString(3, script.getObjectType().toString());
            statement.setString(4, rollback);
            statement.setString(5, script.getHash());
            statement.setString(6, deployedHash);
            statement.setLong(7, changeSyncId);
            log.debug("Updating script hash with the following SQL: {}", insertSql);
        }

        return statement.executeUpdate() >= 0;
    }

    private boolean insertScriptEvent(Script script, String status, String logs) throws SQLException {
        //varchar ID, varchar OBJECT_NAME, varchar SCRIPT_HASH, varchar STATUS, varchar log, varchar created_by, varchar created_ts;
        log.debug("Creating event for the object {} with status: {} and log: {} ", script.getObjectName(), status, logs);
        String insertSql = "INSERT INTO " + SCRIPT_EVENT_TABLE_NAME + " SELECT UUID_STRING(), ?, ?, ?, ?, ?, ?, current_user, current_timestamp;";
        PreparedStatement statement = connection.prepareStatement(insertSql);
        statement.setString(1, script.getId());
        statement.setObject(2, script.getFullObjectName());
        statement.setString(3, script.getHash());
        statement.setString(4, status);
        statement.setString(5, logs);
        statement.setLong(6, changeSyncId);
        log.debug("Creating script event with the following SQL: {}", insertSql);
        return statement.executeUpdate() > 0;
    }

    public boolean isScriptChanged(Script script) {
//        return true;
        return !scriptHash.getOrDefault(script.getId(), "null").equals(script.getHash());
    }

    public boolean isScriptVersionDeployed(Script script) {
        return scriptHash.containsKey(script.getId());
    }

    public void createScriptObject(Script script, boolean onlyHashes) throws SQLException {

        Statement statement = connection.createStatement();
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            if(!onlyHashes) {
                statement.executeUpdate(script.getContent());
                log.debug("Creating object using the SQL: {}", script.getContent());
            }
            updateScriptHash(script);
            insertScriptEvent(script, "SUCCESS", "Successfully Deployed Object");
            connection.commit();
            log.info("Successfully Deployed object: {}", script);
        }
        catch (SQLException e) {
            connection.rollback();
            log.error("Error {}, while creating the object {} with sql {}", e.getMessage(), script.getObjectName(), script.getContent());
            insertScriptEvent(script, "ERROR", e.getMessage());
            throw e;
        }
        finally {
            connection.setAutoCommit(autoCommit);
        }

    }
    public List<Script> getScriptsInSchema(String schema) throws SQLException {
        List<Script> scripts = new ArrayList<>();
        for(ScriptObjectType type: ScriptObjectType.values()) {
            scripts.addAll(getScriptsInSchema(schema, type));
        }
        return scripts;
    }

    public List<Script> getAllScriptsInSchema(String schema) throws SQLException {
        log.info("Getting all scripts in schema: {}", schema);
        String sql = String.format("SELECT GET_DDL('SCHEMA', '%s', true)", schema);
        log.debug("Getting all scripts using SQL: {}", sql);
        ResultSet resultSet = connection.createStatement().executeQuery(sql);
        if(resultSet.next()) {
            String ddl = resultSet.getString(1);
            return SqlTokenizer.parseDdlScripts(ddl, getDatabaseName(), schema);
        }
        else {
            throw new RuntimeException("Error while getting ddl scripts: result set has no data");
        }
    }

    public List<Script> getStateScriptsInSchema(String schema) throws SQLException {
        List<Script> scripts = new ArrayList<>();
        for(ScriptObjectType type: ScriptObjectType.values()) {
            if(!type.isMigration()) {
                scripts.addAll(getScriptsInSchema(schema, type));
            }
        }
        return scripts;
    }

    public List<Script> getScriptsInSchema(String schema, ScriptObjectType type) throws SQLException {
        log.debug("Getting {} type scripts in schema: {}",type, schema);
        String sql = "";
        if(type == ScriptObjectType.FUNCTIONS || type == ScriptObjectType.PROCEDURES) {
            sql = String.format("SELECT %s_NAME, ARGUMENT_SIGNATURE FROM INFORMATION_SCHEMA.%s WHERE %s_SCHEMA = '%s'",type.getEscapedSingular(), type, type.getEscapedSingular(), schema.toUpperCase());

        }
        else if(type == ScriptObjectType.STREAMS || type == ScriptObjectType.TASKS || type == ScriptObjectType.STAGES) {
            return new ArrayList<>();
        }
        else if(type == ScriptObjectType.VIEWS) {
            sql = String.format("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.VIEWS WHERE TABLE_SCHEMA = '%s'", schema.toUpperCase());
        }
        else if(type == ScriptObjectType.TABLES) {
            sql = String.format("SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_TYPE <> 'VIEW' AND TABLE_SCHEMA = '%s'", schema.toUpperCase());
        }
        else {
            sql = String.format("SELECT %s_NAME FROM INFORMATION_SCHEMA.%s WHERE %s_SCHEMA = '%s'",type.getEscapedSingular(), type, type.getEscapedSingular(), schema.toUpperCase());
        }

        log.debug("Getting all scripts using SQL: {}", sql);
        List<Script> scripts = new ArrayList<>();
        ResultSet resultSet = connection.createStatement().executeQuery(sql);
        while (resultSet.next()) {
            String ddlSql = "";
            String scriptObjectName = resultSet.getString(1);
            if(type == ScriptObjectType.FUNCTIONS || type == ScriptObjectType.PROCEDURES) {
                String arguments = resultSet.getString(2);
                String regex = "(\\(|\\,\\s)\\w+";
                arguments = arguments.replaceAll(regex, "$1");
                ddlSql = String.format("SELECT GET_DDL('%s', '%s.%s%s', true);",type.getEscapedSingular(), schema.toUpperCase(), scriptObjectName.toUpperCase(), arguments);
            }
            else {
                ddlSql = String.format("SELECT GET_DDL('%s', '%s.%s', true);",type.getEscapedSingular(), schema.toUpperCase(), scriptObjectName.toUpperCase());
            }
            log.debug("Get ddl script: {}", ddlSql);
            ResultSet ddlResultSet = connection.createStatement().executeQuery(ddlSql);
            ddlResultSet.next();
            String content = ddlResultSet.getString(1);
            if (content == null) {
                log.warn("Unable to read Script definition for {}", scriptObjectName);
                continue;
            }
            if(type.isMigration()) {
//                String migrationHeader = "---version: 0, author: DlSync\n";
//                String rollback = String.format("\n---rollback: DROP %s IF EXISTS %s;", type.getSingular(), getDatabaseName() + "." + schema + "." + scriptObjectName);
//                String verify = String.format("\n---verify: SHOW %s LIKE %s;",type, getDatabaseName() + "." + schema + "." + scriptObjectName);
//                content = migrationHeader + content + rollback + verify;
//                Script script = ScriptFactory.getScript(getDatabaseName(), schema, type, scriptObjectName, content, 0L, null, null, null);
                MigrationScript script = ScriptFactory.getMigrationScript(getDatabaseName(), schema, type, scriptObjectName, content);
                scripts.add(script);
            }
            else {
                Script script = ScriptFactory.getStateScript(getDatabaseName(), schema, type, scriptObjectName, content);
                scripts.add(script);
            }

        }
        return scripts;
    }


    public Script addConfig(Script script) throws SQLException {
        if(script.getObjectType() == ScriptObjectType.TABLES) {
            String additionalContent = String.format("SELECT * FROM %s", script.getFullObjectName());
            ResultSet resultSet = connection.createStatement().executeQuery(additionalContent);
            int count = resultSet.getMetaData().getColumnCount();
            StringBuilder insertBuilder = new StringBuilder(String.format("INSERT INTO %s values", script.getFullObjectName()));
            Boolean firstRow = true;
            while(resultSet.next()) {
                if(!firstRow) {
                    insertBuilder.append(", ");
                }
                firstRow = false;
                for(int i = 1; i <= count; i++) {
                    if(i == 1) {
                        insertBuilder.append("(");
                    }
                    else {
                        insertBuilder.append(", ");
                    }
                    Object value = resultSet.getObject(i);
                    if(value == null) {
                        insertBuilder.append("null");
                    }
                    else {
                        insertBuilder.append(String.format("'%s'", value.toString().replace("'", "''")));
                    }
                }
                insertBuilder.append(")");

            }
            insertBuilder.append(";");
            String migrationHeader = "---version: 1, author: DlSync\n";
            String rollback = "\n---rollback: DELETE FROM " + script.getFullObjectName() + ";";
            String verify = "\n---verify: SELECT COUNT(*) FROM  " + script.getFullObjectName() + ";";
            String insertContent = migrationHeader + insertBuilder + rollback + verify;

            String newContent = script.getContent() + "\n\n" + insertContent;
            script.setContent(newContent);
        }
        return script;
    }

//    public List<Script> getScriptsInSchemaWithArguments(String schema, ScriptObjectType type) throws SQLException {
//        String sql = String.format("SELECT %s_NAME, ARGUMENT_SIGNATURE FROM INFORMATION_SCHEMA.%s WHERE %s_SCHEMA = '%s'",type.getSingular(), type, type.getSingular(), schema.toUpperCase());
//        log.debug("Getting all scripts using SQL: {}", sql);
//        List<Script> scripts = new ArrayList<>();
//        ResultSet resultSet = connection.createStatement().executeQuery(sql);
//        while (resultSet.next()) {
//            String scriptObjectName = resultSet.getString(1);
//            String arguments = resultSet.getString(2);
//            String regex = "(\\(|\\,\\s)\\w+";
//            arguments = arguments.replaceAll(regex, "$1");
//            String ddlSql = String.format("SELECT GET_DDL('%s', '%s.%s%s', true);",type.getSingular(), schema.toUpperCase(), scriptObjectName.toUpperCase(), arguments);
//            log.info("Get ddl script: {}", ddlSql);
//            ResultSet ddlResultSet = connection.createStatement().executeQuery(ddlSql);
//            ddlResultSet.next();
//            String content = ddlResultSet.getString(1);
//            if (content == null) {
//                log.warn("Unable to read Script definition for {}", scriptObjectName);
//                continue;
//            }
//            Script script = new Script(getDatabaseName(), schema, type, scriptObjectName, content);
//            scripts.add(script);
//        }
//        return scripts;
//    }

    public List<Script> getAllScriptsInDatabase() throws SQLException {
        log.info("Getting all scripts for database: {}", getDatabaseName());
        List<String> schemas = getAllSchemasInDatabase(getDatabaseName());
        List<Script> scripts = new ArrayList<>();
        for(String schema: schemas) {
//            for(ScriptObjectType scriptObjectType: ScriptObjectType.values()) {
//                scripts.addAll(getScriptsInSchema(schema, scriptObjectType));
//            }
            scripts.addAll(getScriptsInSchema(schema, ScriptObjectType.VIEWS));
        }
        return scripts;
    }

    public List<String> getAllSchemasInDatabase(String database) throws SQLException {
        log.info("Reading all schemas in database: {}", database);
        String query = String.format("SELECT * FROM %s.INFORMATION_SCHEMA.SCHEMATA", database);
        log.debug("Reading schemas using sql: {}", query);
        ResultSet resultSet = executeQuery(query);
        List<String> schemas = new ArrayList<>();
        while(resultSet.next()) {
            String schema = resultSet.getString("SCHEMA_NAME");
            if(schema.equalsIgnoreCase("INFORMATION_SCHEMA") || schema.equalsIgnoreCase("PUBLIC")) {
                continue;
            }
            schemas.add(schema);
        }
        return schemas;
    }

    public String getDatabaseName() {
        return connectionProperties.getProperty("db");
    }

    public String getSchemaName() {
        return connectionProperties.getProperty("schema");
    }

    private ResultSet executeQuery(String query) throws SQLException {
        return connection.createStatement().executeQuery(query);
    }

    public void insertDependencyList(List<ScriptDependency> dependencyList) throws SQLException {
        String createTable = "CREATE TABLE IF NOT EXISTS " + DEPENDENCY_LINEAGE_TABLE_NAME + "(OBJECT_NAME VARCHAR, OBJECT_TYPE VARCHAR, DEPENDENCY VARCHAR, DEPENDECY_OBEJECT_TYPE VARCHAR, CHANGE_SYNC_ID VARCHAR, CREATED_BY VARCHAR, CREATED_TS TIMESTAMP);";
        connection.createStatement().executeUpdate(createTable);
        StringBuilder insertSql = new StringBuilder("INSERT INTO " + DEPENDENCY_LINEAGE_TABLE_NAME + " VALUES ");

        for(ScriptDependency dependency: dependencyList) {
            String values = String.format("('%s', '%s', '%s', '%s', '%s', current_user, current_timestamp),", dependency.getObjectName(), dependency.getObjectType(), dependency.getDependency(), dependency.getDependencyObjectType(), changeSyncId);
            insertSql.append(values);
        }
        insertSql.deleteCharAt(insertSql.length() - 1);
        log.info("inserting dag scripts using {} ", insertSql);
        connection.createStatement().executeUpdate(insertSql.toString());
    }

    public void insertSortedScript(List<Script> sequencedScript) throws SQLException {
        DependencyExtractor dependencyExtractor = new DependencyExtractor();
        String createTable = "CREATE OR REPLACE TABLE DL_SYNC_TOPOLOGICAL_SORTED(id INT, script VARCHAR, dependency_size INT, dependencies VARCHAR);";
        connection.createStatement().executeUpdate(createTable);
        StringBuilder insertSql = new StringBuilder("INSERT INTO DL_SYNC_TOPOLOGICAL_SORTED VALUES ");

        for(int i = 0; i < sequencedScript.size(); i++) {
            Script script = sequencedScript.get(i);
            Set<String> dependencies = dependencyExtractor.extractScriptDependencies(script).stream().map(s -> s.getFullObjectName()).collect(Collectors.toSet());
            if(dependencies.size() == 0) {
                log.debug("Found zero dependency for {}", script.getFullObjectName());
            }
            String values = String.format("(%s, '%s', %s, '%s'),", i, script.getFullObjectName(), dependencies.size(), dependencies.toString());
            insertSql.append(values);
        }
        insertSql.deleteCharAt(insertSql.length() - 1);
        log.info("inserting sorted scripts using {} ", insertSql.toString());
        connection.createStatement().executeUpdate(insertSql.toString());
    }

    public List<MigrationScript> getMigrationScripts(Set<String> ids) throws SQLException {
        if(ids.size() == 0) {
            return new ArrayList<>();
        }
        String allIdJoined = ids.stream().map(v -> "'" + v + "'").collect(Collectors.joining(",", "(", ");"));
        String query = "SELECT * FROM " + SCRIPT_HISTORY_TABLE_NAME + " where SCRIPT_ID in " + allIdJoined;
        PreparedStatement statement = connection.prepareStatement(query);
        ResultSet rs = statement.executeQuery();
        List<MigrationScript> migrations = new ArrayList<>();
        while(rs.next()) {
            ScriptObjectType objectType = ScriptObjectType.valueOf(rs.getString("OBJECT_TYPE"));
            if(!objectType.isMigration()) {
                continue;
            }
            String id = rs.getString("SCRIPT_ID");
            String[] idSplit = id.split(":");
            Long version = null;
            if(idSplit.length > 1) {
                version = Long.parseLong(idSplit[1]);
            }
            String fullObjectName = rs.getString("OBJECT_NAME");


            String rollback = rs.getString("ROLLBACK_SCRIPT");

            MigrationScript migrationScript = ScriptFactory.getMigrationScript(fullObjectName, objectType, "", version, null, rollback, null);
            migrations.add(migrationScript);
        }
        return migrations;
    }

    public void executeRollback(MigrationScript migrationScript) throws SQLException {
        Statement statement = connection.createStatement();
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            if(migrationScript.getRollback() != null && !migrationScript.getRollback().trim().equals("")) {
                log.debug("Executing rollback using the SQL: {}", migrationScript.getRollback());
                statement.executeUpdate(migrationScript.getRollback());
            }

            deleteScriptHash(migrationScript);
            insertScriptEvent(migrationScript, "SUCCESS", "Successfully Rolled-back Object");
            connection.commit();
            log.info("Successfully Rollback object: {}", migrationScript);
        }
        catch (SQLException e) {
            connection.rollback();
            log.error("Error {}, while rollback the object {} with sql {}", e.getMessage(), migrationScript.getObjectName(), migrationScript.getRollback());
            insertScriptEvent(migrationScript, "ERROR", e.getMessage());
            throw e;
        }
        finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    public boolean executeVerify(MigrationScript migrationScript) throws SQLException {
        Statement statement = connection.createStatement();
        boolean autoCommit = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            if(migrationScript.getVerify() != null && !migrationScript.getVerify().trim().equals("")) {
                log.debug("Executing verify using the SQL: {}", migrationScript.getVerify());
                statement.executeQuery(migrationScript.getVerify());
            }
            insertScriptEvent(migrationScript, "SUCCESS", "Successfully Verified Object");
            connection.commit();
            log.debug("Successfully Verified object: {}", migrationScript);
            return true;
        }
        catch (SQLException e) {
            connection.rollback();
            log.error("Error {}, while verifying the object {} with sql {}", e.getMessage(), migrationScript.getObjectName(), migrationScript.getVerify());
            insertScriptEvent(migrationScript, "ERROR", e.getMessage());
            return false;
        }
        finally {
            connection.setAutoCommit(autoCommit);
        }
    }

    private void deleteScriptHash(MigrationScript migration) throws SQLException {
        String deleteSql = "DELETE FROM " + SCRIPT_HISTORY_TABLE_NAME + " WHERE SCRIPT_ID=?;";
        PreparedStatement statement = connection.prepareStatement(deleteSql);
        statement.setString(1, migration.getId());
        statement.executeUpdate();
    }

    public boolean verifyScript(Script script) {
        if(!scriptHash.containsKey(script.getId())) {
            log.warn("Script file does not exist for the db object: {}", script);
        }
        return scriptHash.getOrDefault(script.getId(), script.getHash()).equals(script.getHash());
    }

    public boolean compareScript(Script script1, Script script2) {
        return SqlTokenizer.compareScripts(script1, script2);
    }
}

