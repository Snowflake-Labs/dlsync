package com.snowflake.dlsync.models;

import com.snowflake.dlsync.parser.TestQueryGenerator;

import java.io.IOException;

public class TestScript extends Script {
    private Script mainScript;
    private TestQueryGenerator testQueryGenerator;

    public TestScript(String scriptPath, String databaseName, String schemaName, String objectName, ScriptObjectType objectType, String content, Script mainScript) {
        super(scriptPath, databaseName, schemaName, objectName, objectType, content);
        this.mainScript = mainScript;
        this.testQueryGenerator = new TestQueryGenerator(this);
    }

    public Script getMainScript() {
        return mainScript;
    }

    public String getTestQuery() throws IOException {
        return testQueryGenerator.generateTestQuery();
    }

    @Override
    public String getId() {
        return mainScript.getId() + "_TEST";
    }
}
