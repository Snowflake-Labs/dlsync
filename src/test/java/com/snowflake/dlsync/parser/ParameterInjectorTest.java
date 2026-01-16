package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.models.SchemaScript;
import com.snowflake.dlsync.models.ScriptObjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ParameterInjectorTest {

    private ParameterInjector parameterInjector;

    @BeforeEach
    void setUp() {
        Properties parameters = new Properties();
        parameters.put("db", "TEST_DB");
        parameters.put("schema1", "test_schema_1");
        parameters.put("schema2", "test_schema_2");
        parameters.put("profile", "dev");
        parameters.put("tenant_id", "test_tenant_id");
        parameterInjector = new ParameterInjector(parameters);
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void injectParametersDontCapture() {
        String content = "create or replace view as select * from ${db}.${schema1}.table1 where db='my_db'";
        SchemaScript script = ScriptFactory.getSchemaScript("", "", ScriptObjectType.VIEWS, "VIEW1", content);
        parameterInjector.injectParameters(script);
        String actual = script.getContent();
        String expected = "create or replace view as select * from TEST_DB.test_schema_1.table1 where db='my_db'";
        assertEquals(expected, actual, "parameter injection test failed");
    }

    @Test
    void injectParametersTest() {
        String content = "create or replace view as select * from ${db}.${schema1}.table1 tb1 join test_${profile} tp on tp.id=tb1.id where tenant = '${tenant_id}'";
        SchemaScript script = ScriptFactory.getSchemaScript("", "", ScriptObjectType.VIEWS, "VIEW1", content);
        parameterInjector.injectParameters(script);
        String actual = script.getContent();
        String expected = "create or replace view as select * from TEST_DB.test_schema_1.table1 tb1 join test_dev tp on tp.id=tb1.id where tenant = 'test_tenant_id'";
        assertEquals(expected, actual, "parameter injection test failed");
    }

    @Test
    void parametrizeScriptTest() {
        String content = "create or replace view as select * from TEST_DB.test_schema_1.table1 where tenant = 'test_tenant_id'";
        SchemaScript script = ScriptFactory.getSchemaScript("", "", ScriptObjectType.VIEWS, "VIEW1", content);
        parameterInjector.parametrizeScript(script, false);
        String actual = script.getContent();
        String expected = "create or replace view as select * from ${db}.${schema1}.table1 where tenant = '${tenant_id}'";
        assertEquals(expected, actual, "parameterize script test failed");
    }

    @Test
    void parameterizeObjectNameTest() {
        String content = "create or replace view as select * from TEST_DB.test_schema_1.table1 where tenant = 'test_tenant_id'";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "test_schema_1", ScriptObjectType.VIEWS, "VIEW1", content);
        parameterInjector.parameterizeObjectName(script);
        String actual = script.getFullObjectName();
        String expected = "${DB}.${SCHEMA1}.VIEW1";
        assertEquals(expected, actual, "parameterize object name test failed");
    }
}