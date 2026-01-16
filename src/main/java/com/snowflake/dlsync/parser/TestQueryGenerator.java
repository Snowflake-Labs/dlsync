package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.models.SchemaScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.models.TestScript;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Slf4j
public class TestQueryGenerator {
    private TestScript testScript;
    private TestScriptParser testScriptParser;
    private ScriptParser scriptParser;
    private String assertion = "select count(1) as result, 'rows missing from actual data' as message from (\n" +
            "\t\tselect * from actual_data\n" +
            "\t\texcept \n" +
            "\t\tselect * from expected_data\n" +
            "\t) having result > 0\n" +
            "\tunion \n" +
            "\tselect count(1) as result, 'rows missing from expected data' as message from ( \n" +
            "\t\tselect * from expected_data\n" +
            "\t\texcept\n" +
            "\t\tselect * from actual_data\n" +
            "\t) having result > 0";
    public TestQueryGenerator(TestScript testScript) {
        this.testScript = testScript;
    }

    public String generateTestQuery() throws IOException {
        parseQueryScript();
        parseMainScript();
        Map<String, String> cteMap = new HashMap<>(testScriptParser.getCteMap());
        String expectedData = cteMap.remove("EXPECTED_DATA");
        String actualData = scriptParser.getMainQuery();
        Set<String> objectReferences = scriptParser.getObjectReferences();
        actualData = updateQueryWithMockData(actualData, objectReferences, cteMap);
        log.debug("objectReferences for {} are: [{}]", testScript.getMainScript().getFullObjectName(), objectReferences);
        StringBuilder testQuery = new StringBuilder();
        testQuery.append("with ");
        for(String mock : cteMap.keySet()) {
            testQuery.append(mock).append(" as (\n\t").append(cteMap.get(mock)).append("\n),");
        }
        testQuery.append("\nexpected_data as (\n\t").append(expectedData).append("\n),");
        testQuery.append("\nactual_data as (\n\t").append(actualData).append("\n),");
        testQuery.append("\nassertion as (\n\t").append(assertion).append("\n)");
        testQuery.append("\nselect * from assertion;");
        return testQuery.toString();
    }

    public String updateQueryWithMockData(String query, Set<String> objectReferences, Map<String, String> mockData) {
        for(String mock : mockData.keySet()) {
            if(!objectReferences.contains(mock)) {
                for(String objectName : objectReferences) {
                    if(objectName.endsWith(mock)) {
                        query = query.replace(objectName,mock);
                    }
                }
            }
        }
        return query;
    }

    public void parseQueryScript() throws IOException {
        testScriptParser = new TestScriptParser(testScript);
        testScriptParser.parse();
    }

    public void parseMainScript() throws IOException {
        Script mainScript = testScript.getMainScript();
        switch (mainScript.getObjectType()) {
            case VIEWS:
                scriptParser = new ViewScriptParser(mainScript);
                break;
            case FUNCTIONS:
                scriptParser = new FunctionScriptParser(mainScript);
                break;
            default:
                log.error("Unsupported test for object type: {} of script: {}", mainScript.getObjectType(), mainScript);
                throw new UnsupportedOperationException("Unsupported test for object type: " + mainScript.getObjectType());
        }
        scriptParser.parse();
    }



}
