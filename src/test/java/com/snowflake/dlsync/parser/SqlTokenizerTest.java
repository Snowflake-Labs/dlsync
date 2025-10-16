package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.models.Migration;
import com.snowflake.dlsync.models.MigrationScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.models.ScriptObjectType;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SqlTokenizerTest {

    @Test
    public void removeSqlCommentsAllComments() {
        String sql = "CREATE OR REPLACE VIEW VIEW1 COMMENT='SOME COMMENTS' AS SELECT * FROM --TABLE_COMMENT1\n" +
                "TABLE1 T1\n" +
                "JOIN  \n" +
                "//TABLE_COMMENT_2 TC2 ON T1.ID = TC2.ID\n" +
                "TABLE3 TC3 ON T1.ID = '-- invalid\\' comments'\n" +
                "JOIN /*\n" +
                "ADDITIONAL COMMENTS HERE\n" +
                "SELECT * FROM TABLE_COMMENT_4;\n" +
                "*/\n" +
                "TABLE4 T4 ON T4.ID = T1.ID;/*new test at end */";


        String expected = "CREATE OR REPLACE VIEW VIEW1 COMMENT='SOME COMMENTS' AS SELECT * FROM \n" +
                "TABLE1 T1\n" +
                "JOIN  \n" +
                "\n" +
                "TABLE3 TC3 ON T1.ID = '-- invalid\\' comments'\n" +
                "JOIN " +
                "\n" +
                "TABLE4 T4 ON T4.ID = T1.ID;";
        String actual = SqlTokenizer.removeSqlComments(sql);
        assertEquals(expected, actual, "Failed to remove comments.");
    }

    @Test
    void parseMigrationScripts() {
        String content = "---version:0, author:junit\n"+
                "create or replace table (id integer, key varchar, value varchar);\n"+
                "---version:1, author:junit\n"+
                "insert into table1 values(1, 'key', null);\n"+
                "---rollback: delete from table1 where id = 1;\n"+
                "---verify: select * from table1 where id = 1;\n";
        Migration migration1 = Migration.builder()
                .version(0L)
                .author("junit")
                .content("---version:0, author:junit\n"+
        "create or replace table (id integer, key varchar, value varchar);")
                .build();

        Migration migration2 = Migration.builder()
                .version(1L)
                .author("junit")
                .content("\n---version:1, author:junit\n"+
                        "insert into table1 values(1, 'key', null);\n"+
                        "---rollback: delete from table1 where id = 1;\n"+
                        "---verify: select * from table1 where id = 1;")
                .rollback("delete from table1 where id = 1;")
                .verify("select * from table1 where id = 1;")
                .build();
        List<Migration> expected = List.of(migration1, migration2);
        List<Migration> actual = SqlTokenizer.parseMigrationScripts(content);
        assertIterableEquals(expected, actual, "Test failed for parsing basic migration script.");
    }
    @Test
    void parseMigrationScriptsEmptyVerifyAndRollbackTest() {
        String content = "---version:0, author: junit\n"+
                "create or replace table (id integer, key varchar, value varchar);\n"+
                "---verify:  \n" +
                "---version:1\n"+
                "insert into table1 values(1, 'key', null);\n"+
                "---rollback:  \n"+
                "---verify: select * from table1 where id = 1;\n";
        Migration migration1 = Migration.builder()
                .version(0L)
                .author("junit")
                .content("---version:0, author: junit\n"+
                        "create or replace table (id integer, key varchar, value varchar);\n"+
                        "---verify:  ")
                .verify(" ")
                .build();

        Migration migration2 = Migration.builder()
                .version(1L)
                .author(null)
                .content("\n---version:1\n"+
                        "insert into table1 values(1, 'key', null);\n"+
                        "---rollback:  \n"+
                        "---verify: select * from table1 where id = 1;")
                .rollback(" ")
                .verify("select * from table1 where id = 1;")
                .build();
        List<Migration> expected = List.of(migration1, migration2);
        List<Migration> actual = SqlTokenizer.parseMigrationScripts(content);
        assertIterableEquals(expected, actual, "Test failed for parsing basic migration script.");
    }



    @Test
    void parseMigrationScriptsWithSemicolonContent() {
        String content = "---version:0, author: junit\n"+
                "create or replace table (id integer, key varchar, value varchar);\n"+
                "---verify:  \n" +
                "---version:1\n"+
                "insert into table1 values(1, 'key', 'some date with ; inside');\n"+
                "---rollback:  \n"+
                "---verify: select * from table1 where id = 1;\n";
        Migration migration1 = Migration.builder()
                .version(0L)
                .author("junit")
                .content("---version:0, author: junit\n"+
                        "create or replace table (id integer, key varchar, value varchar);\n"+
                        "---verify:  ")
                .verify(" ")
                .build();

        Migration migration2 = Migration.builder()
                .version(1L)
                .author(null)
                .content("\n---version:1\n"+
                        "insert into table1 values(1, 'key', 'some date with ; inside');\n"+
                        "---rollback:  \n"+
                        "---verify: select * from table1 where id = 1;")
                .rollback(" ")
                .verify("select * from table1 where id = 1;")
                .build();
        List<Migration> expected = List.of(migration1, migration2);
        List<Migration> actual = SqlTokenizer.parseMigrationScripts(content);
        assertIterableEquals(expected, actual, "Test failed for parsing basic migration script.");
    }

    @Test
    void parseMigrationScriptsWithMultilineContent() {
        String content = "---version:0, author: junit\n"+
                "create or replace table (id integer, key varchar, value varchar);\n"+
                "---verify:  \n" +
                "---version:1\n"+
                "insert into table1(id, key, value)\n" +
                "values(1, 'key', 'some date with ; inside');\n"+
                "---rollback:  \n"+
                "---verify: select * from table1 where id = 1;\n";
        Migration migration1 = Migration.builder()
                .version(0L)
                .author("junit")
                .content("---version:0, author: junit\n"+
                        "create or replace table (id integer, key varchar, value varchar);\n"+
                        "---verify:  ")
                .verify(" ")
                .build();

        Migration migration2 = Migration.builder()
                .version(1L)
                .author(null)
                .content("\n---version:1\n"+
                        "insert into table1(id, key, value)\n" +
                        "values(1, 'key', 'some date with ; inside');\n"+
                        "---rollback:  \n"+
                        "---verify: select * from table1 where id = 1;")
                .rollback(" ")
                .verify("select * from table1 where id = 1;")
                .build();
        List<Migration> expected = List.of(migration1, migration2);
        List<Migration> actual = SqlTokenizer.parseMigrationScripts(content);
        assertIterableEquals(expected, actual, "Test failed for parsing basic migration script.");
    }

    @Test
    void getPreviousToken() {
    }

    @Test
    void removeSqlComments() {
    }

    @Test
    void getFullIdentifiersTest() {
        String content = "select * from schema1.object_name1 join schema2.object_name1 join (select * from db1.schema2.object_name2) union select * from \"schema3\".\"object_name3\" union select * from object_name4,schema5.object_name5,schema2.object_name6;";
        Set<String> expected1 = Set.of("schema1.object_name1", "schema2.object_name1");
        assertEquals(expected1, SqlTokenizer.getFullIdentifiers("object_name1", content), "Test failed to extract full token.");
        Set<String> expected2 = Set.of("db1.schema2.object_name2");
        assertEquals(expected2, SqlTokenizer.getFullIdentifiers("object_name2", content), "Test failed to extract full token.");
        Set<String> expected3 = Set.of("schema3.object_name3");
        assertEquals(expected3, SqlTokenizer.getFullIdentifiers("object_name3", content), "Test failed to extract full token.");
        Set<String> expected5 = Set.of("schema5.object_name5");
        assertEquals(expected5, SqlTokenizer.getFullIdentifiers("object_name5", content), "Test failed to extract full token.");

    }

    @Test
    void parseDdlScriptsTest() {
        String ddl = "create or replace schema schema1;\n\n" +
                "create or replace view db1.schema1.view1 as select * from table1;\n" +
                "create or replace table db1.schema1.table1 (col1 varchar, col2 number);\n" +
                "create or replace transient table db1.schema1.table2 (col1 varchar, col2 number);\n" +
                "create or replace hybrid table db1.schema1.table3 (col1 varchar, col2 number);\n" +
                "create or replace table db1.schema1.\"table4\" (col1 varchar, col2 number);\n" +
                "create or replace function db1.schema1.function1(arg1 varchar)\n" +
                "RETURNS VARCHAR(16777216)\n" +
                "LANGUAGE JAVASCRIPT\n" +
                "AS '" +
                "return arg1.trim();\n"+
                "';";
        List<Script> actual = SqlTokenizer.parseDdlScripts(ddl, "db1", "schema1");
        List<Script> expected = List.of(
                ScriptFactory.getStateScript("db1", "schema1", ScriptObjectType.VIEWS, "view1","create or replace view db1.schema1.view1 as select * from table1;"),
                ScriptFactory.getMigrationScript("db1", "schema1", ScriptObjectType.TABLES, "table1","create or replace table db1.schema1.table1 (col1 varchar, col2 number);"),
                ScriptFactory.getMigrationScript("db1", "schema1", ScriptObjectType.TABLES, "table2","create or replace transient table db1.schema1.table2 (col1 varchar, col2 number);"),
                ScriptFactory.getMigrationScript("db1", "schema1", ScriptObjectType.TABLES, "table3","create or replace hybrid table db1.schema1.table3 (col1 varchar, col2 number);"),
                ScriptFactory.getMigrationScript("db1", "schema1", ScriptObjectType.TABLES, "\"table4\"","create or replace table db1.schema1.\"table4\" (col1 varchar, col2 number);"),
                ScriptFactory.getStateScript("db1", "schema1", ScriptObjectType.FUNCTIONS, "function1","create or replace function db1.schema1.function1(arg1 varchar)\n" +
                        "RETURNS VARCHAR(16777216)\n" +
                        "LANGUAGE JAVASCRIPT\n" +
                        "AS '" +
                        "return arg1.trim();\n"+
                        "'")
        );

        assertEquals(expected, actual, "Parse ddl failed");
    }

    @Test
    void removeSqlStringLiteralsWithSimpleLiterals() {
        String sql = "insert into table1 values('tabl2', 'table3', 'table4')";
        String expected = "insert into table1 values('', '', '')";
        String actual = SqlTokenizer.removeSqlStringLiterals(sql);
        assertEquals(expected, actual, "Remove string literal assertion failed!");
    }

    @Test
    void removeSqlStringLiteralsWithEscapedLiterals() {
        String sql = "select 'he said \\'good\\'', 'and she said ''GREAT'''";
        String expected = "select '', ''";
        String actual = SqlTokenizer.removeSqlStringLiterals(sql);
        assertEquals(expected, actual, "Remove string literal assertion failed!");
    }

    @Test
    void removeSqlStringLiteralsWithUDFContent() {
        String sql = "CREATE OR REPLACE FUNCTION UDF1(\"ARG1\" VARCHAR(16777216), \"ARG2\" VARCHAR(16777216))\n" +
                "RETURNS BOOLEAN\n" +
                "LANGUAGE SQL\n" +
                "as '\n" +
                "select ARG1, ARG2 FROM TABLE1';";
        String expected = "CREATE OR REPLACE FUNCTION UDF1(\"ARG1\" VARCHAR(16777216), \"ARG2\" VARCHAR(16777216))\n" +
                "RETURNS BOOLEAN\n" +
                "LANGUAGE SQL\n" +
                "as '\n" +
                "select ARG1, ARG2 FROM TABLE1';";
        String actual = SqlTokenizer.removeSqlStringLiterals(sql);
        assertEquals(expected, actual, "Remove string literal assertion failed!");
    }

    @Test
    void removeSqlStringLiteralsWithCTAS() {
        String sql = "CREATE OR REPLACE TABLE TABLE1(COL1 VARCHAR(16777216), COL2 VARCHAR(16777216))\n" +
                "as \n" +
                "select 1, 'VALUE';";
        String expected = "CREATE OR REPLACE TABLE TABLE1(COL1 VARCHAR(16777216), COL2 VARCHAR(16777216))\n" +
                "as \n" +
                "select 1, '';";
        String actual = SqlTokenizer.removeSqlStringLiterals(sql);
        assertEquals(expected, actual, "Remove string literal assertion failed!");
    }

    @Test
    void removeStringLiterals() {
        String sql = "create or replace view view1 " +
                " comment='some comments'" +
                " as select * from table1 where id = 'some values ' " +
                " and date <= current_date();";
        String expected = "create or replace view view1 " +
                " comment=''" +
                " as select * from table1 where id = '' " +
                " and date <= current_date();";

        String actual = SqlTokenizer.removeSqlStringLiterals(sql);
        assertEquals(expected, actual, "Failed to remove comments.");
    }

    @Test
    void parseScriptTypeView() {
        String filePath = "db_scripts/db1/schema1/VIEWS/VIEW1.SQL";
        String name = "VIEW1.SQL";
        String scriptType = "VIEWS";
        String content = "CREATE OR REPLACE VIEW db1.schema1.VIEW1 AS SELECT * FROM table1;";

        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        Script script = scripts.iterator().next();
        assertEquals("VIEW1", script.getObjectName(), "Object name should be VIEW1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.VIEWS, script.getObjectType(), "Object type should be VIEWS");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeFunction() {
        String filePath = "db_scripts/db1/schema1/FUNCTIONS/FUNCTION1.SQL";
        String name = "FUNCTION1.SQL";
        String scriptType = "FUNCTIONS";
        String content = "CREATE OR REPLACE FUNCTION db1.schema1.FUNCTION1() RETURNS STRING LANGUAGE JAVASCRIPT AS 'return \"Hello\";';";

        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        Script script = scripts.iterator().next();
        assertEquals("FUNCTION1", script.getObjectName(), "Object name should be FUNCTION1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.FUNCTIONS, script.getObjectType(), "Object type should be FUNCTIONS");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeProcedure() {
        String filePath = "db_scripts/db1/schema1/PROCEDURES/PROCEDURE1.SQL";
        String name = "PROCEDURE1.SQL";
        String scriptType = "PROCEDURES";
        String content = "CREATE OR REPLACE PROCEDURE db1.schema1.PROCEDURE1() RETURNS STRING LANGUAGE JAVASCRIPT AS 'return \"Hello\";';";

        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        Script script = scripts.iterator().next();
        assertEquals("PROCEDURE1", script.getObjectName(), "Object name should be PROCEDURE1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.PROCEDURES, script.getObjectType(), "Object type should be PROCEDURES");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeFileFormat() {
        String filePath = "db_scripts/db1/schema1/FILE_FORMATS/FILE_FORMAT1.SQL";
        String name = "FILE_FORMAT1.SQL";
        String scriptType = "FILE_FORMATS";
        String content = "CREATE OR REPLACE FILE FORMAT db1.schema1.FILE_FORMAT1 TYPE = 'CSV';";

        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        Script script = scripts.iterator().next();
        assertEquals("FILE_FORMAT1", script.getObjectName(), "Object name should be FILE_FORMAT1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.FILE_FORMATS, script.getObjectType(), "Object type should be FILE_FORMATS");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeTable() {
        String filePath = "db_scripts/db1/schema1/TABLES/TABLE1.SQL";
        String name = "TABLE1.SQL";
        String scriptType = "TABLES";
        String content = "---version: 0, author: dlsync \n" +
                "CREATE OR REPLACE TABLE db1.schema1.TABLE1 (id INT, name STRING);\n" +
                "---rollback: drop table db1.schema1.TABLE1;\n" +
                "---verify: select * from db1.schema1.TABLE1;";

        String expected_rollback = "drop table db1.schema1.TABLE1;";
        String expected_verify = "select * from db1.schema1.TABLE1;";
        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        MigrationScript script = (MigrationScript) scripts.iterator().next();
        assertEquals(0, script.getVersion(), "Version should be 0");
        assertEquals(expected_rollback, script.getRollback(), "Rollback should match the input content");
        assertEquals(expected_verify, script.getVerify(), "Verify should match the input content");
        assertEquals("TABLE1", script.getObjectName(), "Object name should be TABLE1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.TABLES, script.getObjectType(), "Object type should be TABLES");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeStream() {
        String filePath = "db_scripts/db1/schema1/STREAMS/STREAM1.SQL";
        String name = "STREAM1.SQL";
        String scriptType = "STREAMS";

        String content = "---version: 0, author: dlsync \n" +
                "CREATE OR REPLACE STREAM db1.schema1.STREAM1 ON TABLE db1.schema1.TABLE1;\n" +
                "---rollback: drop stream db1.schema1.STREAM1;\n" +
                "---verify: select * from db1.schema1.STREAM1;";

        String expected_rollback = "drop stream db1.schema1.STREAM1;";
        String expected_verify = "select * from db1.schema1.STREAM1;";
        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        MigrationScript script = (MigrationScript) scripts.iterator().next();
        assertEquals(0, script.getVersion(), "Version should be 0");
        assertEquals(expected_rollback, script.getRollback(), "Rollback should match the input content");
        assertEquals(expected_verify, script.getVerify(), "Verify should match the input content");
        assertEquals("STREAM1", script.getObjectName(), "Object name should be STREAM1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.STREAMS, script.getObjectType(), "Object type should be STREAMS");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeSequence() {
        String filePath = "db_scripts/db1/schema1/SEQUENCES/SEQUENCE1.SQL";
        String name = "SEQUENCE1.SQL";
        String scriptType = "SEQUENCES";
        String content = "---version: 0, author: dlsync \n" +
                "CREATE OR REPLACE SEQUENCE db1.schema1.SEQUENCE1 START WITH 1 INCREMENT BY 1;\n" +
                "---rollback: drop sequence db1.schema1.SEQUENCE1;\n" +
                "---verify: select * from db1.schema1.SEQUENCE1;";

        String expected_rollback = "drop sequence db1.schema1.SEQUENCE1;";
        String expected_verify = "select * from db1.schema1.SEQUENCE1;";
        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        MigrationScript script = (MigrationScript) scripts.iterator().next();
        assertEquals(0, script.getVersion(), "Version should be 0");
        assertEquals(expected_rollback, script.getRollback(), "Rollback should match the input content");
        assertEquals(expected_verify, script.getVerify(), "Verify should match the input content");
        assertEquals("SEQUENCE1", script.getObjectName(), "Object name should be SEQUENCE1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.SEQUENCES, script.getObjectType(), "Object type should be SEQUENCES");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeStage() {
        String filePath = "db_scripts/db1/schema1/STAGES/STAGE1.SQL";
        String name = "STAGE1.SQL";
        String scriptType = "STAGES";
        String content = "---version: 0, author: dlsync \n" +
                "CREATE OR REPLACE STAGE db1.schema1.STAGE1 URL='s3://mybucket/mypath/';\n" +
                "---rollback: drop stage db1.schema1.STAGE1;\n" +
                "---verify: list @db1.schema1.STAGE1;";

        String expected_rollback = "drop stage db1.schema1.STAGE1;";
        String expected_verify = "list @db1.schema1.STAGE1;";
        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        MigrationScript script = (MigrationScript) scripts.iterator().next();
        assertEquals(0, script.getVersion(), "Version should be 0");
        assertEquals(expected_rollback, script.getRollback(), "Rollback should match the input content");
        assertEquals(expected_verify, script.getVerify(), "Verify should match the input content");
        assertEquals("STAGE1", script.getObjectName(), "Object name should be STAGE1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.STAGES, script.getObjectType(), "Object type should be STAGES");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeTask() {
        String filePath = "db_scripts/db1/schema1/TASKS/TASK1.SQL";
        String name = "TASK1.SQL";
        String scriptType = "TASKS";
        String content = "---version: 0, author: dlsync \n" +
                "CREATE OR REPLACE TASK db1.schema1.TASK1 WAREHOUSE = 'my_warehouse' SCHEDULE = 'USING CRON 0 0 * * * UTC' AS CALL my_procedure();\n" +
                "---rollback: drop task db1.schema1.TASK1;\n" +
                "---verify: select * from db1.schema1.TASK1;";

        String expected_rollback = "drop task db1.schema1.TASK1;";
        String expected_verify = "select * from db1.schema1.TASK1;";
        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        MigrationScript script = (MigrationScript) scripts.iterator().next();
        assertEquals(0, script.getVersion(), "Version should be 0");
        assertEquals(expected_rollback, script.getRollback(), "Rollback should match the input content");
        assertEquals(expected_verify, script.getVerify(), "Verify should match the input content");
        assertEquals("TASK1", script.getObjectName(), "Object name should be TASK1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.TASKS, script.getObjectType(), "Object type should be TASKS");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }

    @Test
    void parseScriptTypeStreamlit() {
        String filePath = "db_scripts/db1/schema1/STREAMLITS/STREAMLIT1.SQL";
        String name = "STREAMLIT1.SQL";
        String scriptType = "STREAMLITS";
        String content = "CREATE OR REPLACE STREAMLIT db1.schema1.STREAMLIT1 " +
                "\troot_location='MY_STAGE'\n" +
                "\tmain_file='/streamlit_app.py'\n" +
                "\tquery_warehouse='${MY_WAREHOUSE}';";

        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);

        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");

        Script script = scripts.iterator().next();
        assertEquals("STREAMLIT1", script.getObjectName(), "Object name should be STREAMLIT1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.STREAMLITS, script.getObjectType(), "Object type should be STREAMLITS");
        assertEquals(content, script.getContent(), "Script content should match the input content");

    }

    @Test
    void parseScriptTypePipe() {
        String filePath = "db_scripts/db1/schema1/PIPES/PIPE1.SQL";
        String name = "PIPE1.SQL";
        String scriptType = "PIPES";
        String content = "CREATE OR REPLACE PIPE db1.schema1.PIPE1\n" +
                         "  AUTO_INGEST = TRUE\n" +
                         "  COMMENT = 'Loads data automatically from stage'\n" +
                         "  AS COPY INTO db1.schema1.my_table\n" +
                         "  FROM @my_stage/file_prefix\n" +
                         "  FILE_FORMAT = (TYPE = 'CSV');";
    
        Set<Script> scripts = SqlTokenizer.parseScript(filePath, name, scriptType, content);
    
        assertNotNull(scripts, "Scripts should not be null");
        assertEquals(1, scripts.size(), "There should be exactly one script parsed");
    
        Script script = scripts.iterator().next();
        assertEquals("PIPE1", script.getObjectName(), "Object name should be PIPE1");
        assertEquals("db1".toUpperCase(), script.getDatabaseName(), "Database name should be db1");
        assertEquals("schema1".toUpperCase(), script.getSchemaName(), "Schema name should be schema1");
        assertEquals(ScriptObjectType.PIPES, script.getObjectType(), "Object type should be PIPES");
        assertEquals(content, script.getContent(), "Script content should match the input content");
    }
    


}