package com.snowflake.dlsync.dependency;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.models.MigrationScript;
import com.snowflake.dlsync.models.SchemaScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.models.ScriptObjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class DependencyExtractorTest {

    private DependencyExtractor dependencyExtractor;

    @BeforeEach
    void setUp() {
        dependencyExtractor = new DependencyExtractor();
    }

    @AfterEach
    void tearDown() {
    }

    List<SchemaScript> mockScripts(String name, String schema, String... contents) {
        List<SchemaScript> scripts = new ArrayList<>();
        for(int i = 0; i < contents.length; i++) {
            SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", schema, ScriptObjectType.VIEWS, name + i, contents[i]);
            scripts.add(script);
        }
        return scripts;

    }

    List<SchemaScript> mockScripts() {
        String[] contents = {"CREATE OR REPLACE MOCK1 AS SELECT * FROM TABLE1;", "CREATE OR REPLACE TEST_SCHEMA.MOCK2 AS SELECT * FROM MOCK1;", "CREATE OR REPLACE TEST_DB.TEST_SCHEMA.MOCK3 AS SELECT * FROM MOCK1 UNION SELECT * FROM MOCK2;"};
        List<SchemaScript> mock = mockScripts("MOCK", "TEST_SCHEMA", contents);
        mock.addAll(mockNotDependency());
        return mock;
    }

    List<SchemaScript> mockNotDependency() {
        String[] contents = {"CREATE OR REPLACE NOT_DEPENDENCY1 AS SELECT * FROM TABLE1;", "CREATE OR REPLACE TEST_SCHEMA.NOT_DEPENDENCY2 AS SELECT * FROM MOCK1;", "CREATE OR REPLACE TEST_DB.TEST_SCHEMA.NOT_DEPENDENCY3 AS SELECT * FROM MOCK1 UNION SELECT * FROM MOCK2;"};
        return mockScripts("NOT_DEPENDENCY", "TEST_SCHEMA", contents);
    }

    SchemaScript mockViewDependency(String name, String schema) {
        String content = "CREATE OR REPLACE VIEW " + name + " AS SELECT * FROM  TABLE1;";
        return ScriptFactory.getSchemaScript("TEST_DB", schema, ScriptObjectType.VIEWS, name, content);
    }

    MigrationScript mockTableDependency(String name, String schema) {
        String content = "CREATE OR REPLACE TABLE " + name + "(ID VARCHAR, COL1 NUMBER)";
        return ScriptFactory.getSchemaMigrationScript("TEST_DB", schema, ScriptObjectType.TABLES, name, content, 0L, "dlsync", "", "");
    }

    SchemaScript mockUdfDependency(String name, String schema) {
        String content = "CREATE OR REPLACE FUNCTION " + name + "(ARG1 VARCHAR)\n" +
                "RETURNS VARCHAR\n" +
                "LANGUAGE JAVASCRIPT\n" +
                "AS\n" +
                "$$\n" +
                " return ARG1.toUpperCase();\n" +
                "$$;";
        return ScriptFactory.getSchemaScript("TEST_DB", schema, ScriptObjectType.FUNCTIONS, name, content);
    }

    @Test
    void extractScriptDependenciesTestFrom() {
        String content = "CREATE OR REPLACE VIEW VIEW1 AS SELECT * FROM  DEPENDENCY join test_schema2.not_dependency1;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency = mockViewDependency("DEPENDENCY", "TEST_SCHEMA");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesTestJoin() {
        String content = "CREATE OR REPLACE VIEW VIEW1 AS SELECT * FROM  DEPENDENCY JOIN test_schema.JOIN_DEPENDENCY;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);

        SchemaScript dependency1 = mockViewDependency("DEPENDENCY", "TEST_SCHEMA");
        SchemaScript dependency2 = mockViewDependency("JOIN_DEPENDENCY", "TEST_SCHEMA");

        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency2,
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesTestWithQuotedObjects() {
        String content = "CREATE OR REPLACE VIEW VIEW1 AS SELECT * FROM  \"TEST_SCHEMA2\".\"DEPENDENCY\" join \"TEST_SCHEMA2\".\"NOT_DEPENDENCY1\"";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);

        SchemaScript dependency1 = mockViewDependency("DEPENDENCY", "TEST_SCHEMA2");

        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesTestWithComments() throws IOException {
        String content = "CREATE OR REPLACE VIEW VIEW1 COMMENT='SOME COMMENTS' AS SELECT * FROM -- NOT_DEPENDENCY1\n" +
                "DEPENDENCY1 T1\n" +
                "JOIN  \n" +
                "// NOT_DEPENDENCY2 TC2 ON T1.ID = TC2.ID\n" +
                "DEPENDENCY2 TC3 ON T1.ID = TC3.ID\n" +
                "JOIN /*\n" +
                "ADDITIONAL COMMENTS HERE\n" +
                "SELECT * FROM NOT_DEPENDENCY2;\n" +
                "*/\n" +
                "DEPENDENCY3 T4 ON T4.ID = T1.ID;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);

        SchemaScript dependency1 = mockViewDependency("DEPENDENCY1", "TEST_SCHEMA");
        SchemaScript dependency2 = mockViewDependency("DEPENDENCY2", "TEST_SCHEMA");
        SchemaScript dependency3 = mockViewDependency("DEPENDENCY3", "TEST_SCHEMA");

        SchemaScript notDependency1 = mockViewDependency("NOT_DEPENDENCY1", "TEST_SCHEMA");
        SchemaScript notDependency2 = mockViewDependency("NOT_DEPENDENCY2", "TEST_SCHEMA");


        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        changedScript.add(dependency3);
        changedScript.add(notDependency1);
        changedScript.add(notDependency2);

        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency2,
                dependency1,
                dependency3
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");

    }

    @Test
    void extractScriptDependenciesTestWithCTE() {
        String content = "CREATE OR REPLACE VIEW VIEW1 AS wITH CTE1 AS (\n" +
                "        SELECT * FROM DEPENDENCY1\n" +
                "        ),\n" +
                "       CTE2 AS(\n" +
                "             SELECT B.*, JLS.VALUE::STRING AS JOURNAL_LINE\n" +
                "             FROM DEPENDENCY2 B\n" +
                "                      JOIN JOIN_DEPENDENCY2\n" +
                "         ),CTE3 AS (\n" +
                "             SELECT * FROM CTE1 WHERE ID IN (SELECT ID FROM CTE2) \n" +
                "         ), CTE4 AS (\n" +
                "             SELECT * FROM DEPENDENCY3 D1 JOIN CTE3 C3 ON D1.ID=C3.ID\n" +
                "         ), CTE5 AS (SELECT * FROM CTE4 JOIN JOIN_DEPENDENCY3)\n" +
                "    SELECT  * FROM CTE1 C1 JOIN CTE5 CT5 ON CT1.ID=CT5.ID JOIN JOIN_DEPENDENCY4 ON CT1.ID=CT5.ID;";

        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);

        SchemaScript dependency1 = mockViewDependency("DEPENDENCY1", "TEST_SCHEMA");
        SchemaScript dependency2 = mockViewDependency("DEPENDENCY2", "TEST_SCHEMA");
        SchemaScript dependency3 = mockViewDependency("DEPENDENCY3", "TEST_SCHEMA");
        SchemaScript joinDependency2 = mockViewDependency("JOIN_DEPENDENCY2", "TEST_SCHEMA");
        SchemaScript joinDependency3 = mockViewDependency("JOIN_DEPENDENCY3", "TEST_SCHEMA");
        SchemaScript joinDependency4 = mockViewDependency("JOIN_DEPENDENCY4", "TEST_SCHEMA");

        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        changedScript.add(dependency3);
        changedScript.add(joinDependency2);
        changedScript.add(joinDependency3);
        changedScript.add(joinDependency4);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1,
                dependency2,
                dependency3,
                joinDependency2,
                joinDependency3,
                joinDependency4
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");

    }

    @Test
    void extractScriptDependenciesTestWithQuoted() {
        String content = "CREATE OR REPLACE VIEW VIEW1 AS SELECT 'SELECT * FROM NOT_DEPENDENCY1', * FROM  DEPENDENCY1";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency1 = mockViewDependency("DEPENDENCY1", "TEST_SCHEMA");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        //TODO:Fix this
//        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesTestWithAliases() {
        String content = "CREATE OR REPLACE VIEW VIEW1 AS SELECT  * FROM  DEPENDENCY1 as dp1, DEPENDENCY2 dp2, DEPENDENCY3 AS dp3 where dp1.id=dp2.id and dp2.id=dp3.id;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency1 = mockViewDependency("DEPENDENCY1", "TEST_SCHEMA");
        SchemaScript dependency2 = mockViewDependency("DEPENDENCY2", "TEST_SCHEMA");
        SchemaScript dependency3 = mockViewDependency("DEPENDENCY3", "TEST_SCHEMA");

        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        changedScript.add(dependency3);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency2,
                dependency1,
                dependency3
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");

    }
    @Test
    void extractScriptDependenciesWithSameObjectName() {
        String content = "CREATE OR REPLACE VIEW TEST_SCHEMA.VIEW1 AS SELECT * FROM  TEST_SCHEMA2.VIEW1 join test_schema2.not_dependency1;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency = mockViewDependency("VIEW1", "TEST_SCHEMA2");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesWithUdf() {
        String content = "CREATE OR REPLACE VIEW TEST_SCHEMA.VIEW1 AS SELECT COL1, COL2, TEST_SCHEMA1.UDF1(COL3) AS COL4 FROM  TEST_SCHEMA2.VIEW1 join test_schema2.not_dependency1;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency1 = mockViewDependency("VIEW1", "TEST_SCHEMA2");
        SchemaScript dependency2 = mockUdfDependency("UDF1", "TEST_SCHEMA1");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1,
                dependency2
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesTableWithViewDependency() {
        String content =
        "---version: 0, author: dlsync\n" +
        "CREATE OR REPLACE TABLE TEST_SCHEMA.TABLE1 AS SELECT * FROM TEST_SCHEMA.VIEW1;\n";

        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.TABLES, "TABLE1", content);
        SchemaScript dependency1 = mockViewDependency("VIEW1", "TEST_SCHEMA");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesWithStringNames() {
        String content =
                "---version: 1, author: dlsync\n" +
                "INSERT INTO TEST_SCHEMA.TABLE1 values(1, 'not_dependency1');\n";

        Script script = ScriptFactory.getSchemaMigrationScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.TABLES, "TABLE1", content, 1L, "dlsync", "", "");
        MigrationScript dependency1 = mockTableDependency("TABLE1", "TEST_SCHEMA");
        SchemaScript not_dependency1 = mockViewDependency("NOT_DEPENDENCY1", "TEST_SCHEMA");
        List<Script> changedScript = mockScripts().stream().map(schemaScript -> (Script)schemaScript).collect(Collectors.toList());
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(not_dependency1);
        dependencyExtractor.addScripts(changedScript);
        Set<Script> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesIdentifierAfterEquals() {
        String content = "CREATE OR REPLACE VIEW TEST_SCHEMA.VIEW1 AS SELECT COL1, COL2=FUNC1(COL3) FROM  TEST_SCHEMA2.VIEW1 join test_schema2.not_dependency1;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency1 = mockViewDependency("VIEW1", "TEST_SCHEMA2");
        SchemaScript dependency2 = mockUdfDependency("FUNC1", "TEST_SCHEMA");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1,
                dependency2
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    // TODO: Fix this failing test
    void extractScriptDependenciesWithSameName() {
        String content = "CREATE OR REPLACE VIEW TEST_SCHEMA.VIEW1 AS SELECT COL1, COL2 FROM  TEST_SCHEMA2.VIEW1 join test_schema2.not_dependency1;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content);
        SchemaScript dependency1 = mockViewDependency("VIEW1", "TEST_SCHEMA2");
        SchemaScript notDependency = mockUdfDependency("VIEW1", "TEST_SCHEMA2");
        List<SchemaScript> changedScript = mockScripts();
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(notDependency);
        dependencyExtractor.addScripts(changedScript);
        Set<SchemaScript> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesIdentifierInQuotes() {
        String content = "create or replace streamlit TEST_SCHEMA1.STREAMLIT_1\n" +
                "root_location=@TEST_SCHEMA2.STAGE1\n" +
                "\tmain_file='/streamlit_app.py'\n" +
                "\tquery_warehouse='${MY_WAREHOUSE}'";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA1", ScriptObjectType.STREAMLITS, "STREAMLIT_1", content);
        Script dependency1 = ScriptFactory.getSchemaMigrationScript("TEST_DB", "TEST_SCHEMA2", ScriptObjectType.STAGES, "STAGE1", "---version: 1, author: dlsync\nCREATE OR REPLACE STAGE TEST_SCHEMA2.STAGE1;", 1L, "dlsync", "", "");
        List<Script> changedScript = mockScripts().stream().map(schemaScript -> (Script)schemaScript).collect(Collectors.toList());;
        changedScript.add(script);
        changedScript.add(dependency1);
        dependencyExtractor.addScripts(changedScript);
        Set<Script> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Dependency extractor failed:");

    }

    SchemaScript mockCortexSearchDependency(String name, String schema) {
        String content = "CREATE OR REPLACE CORTEX SEARCH SERVICE " + name + "\n" +
                "  ON search_column\n" +
                "  ATTRIBUTES attr1, attr2\n" +
                "  WAREHOUSE = MY_WH\n" +
                "  TARGET_LAG = '1 hour'\n" +
                "AS (SELECT * FROM SOURCE_TABLE);";
        return ScriptFactory.getSchemaScript("TEST_DB", schema, ScriptObjectType.CORTEX_SEARCH_SERVICES, name, content);
    }

    @Test
    void extractScriptDependenciesCortexSearchService() {
        String content = "CREATE OR REPLACE CORTEX SEARCH SERVICE TEST_SCHEMA.PRODUCT_SEARCH\n" +
                "  ON product_description\n" +
                "  ATTRIBUTES product_name, category\n" +
                "  WAREHOUSE = MY_WH\n" +
                "  TARGET_LAG = '1 hour'\n" +
                "AS (\n" +
                "  SELECT product_name, product_description, category\n" +
                "  FROM TEST_SCHEMA.PRODUCTS\n" +
                ");";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.CORTEX_SEARCH_SERVICES, "PRODUCT_SEARCH", content);
        MigrationScript dependency1 = mockTableDependency("PRODUCTS", "TEST_SCHEMA");
        List<Script> changedScript = mockScripts().stream().map(schemaScript -> (Script)schemaScript).collect(Collectors.toList());
        changedScript.add(script);
        changedScript.add(dependency1);
        dependencyExtractor.addScripts(changedScript);
        Set<Script> expected = Set.of(
                dependency1
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Cortex Search Service dependency extractor failed:");
    }

    @Test
    void extractScriptDependenciesCortexSearchServiceWithMultipleTables() {
        String content = "CREATE OR REPLACE CORTEX SEARCH SERVICE TEST_SCHEMA.UNIFIED_SEARCH\n" +
                "  ON content\n" +
                "  ATTRIBUTES title, category\n" +
                "  WAREHOUSE = MY_WH\n" +
                "  TARGET_LAG = '1 hour'\n" +
                "AS (\n" +
                "  SELECT title, content, category FROM TEST_SCHEMA.ARTICLES\n" +
                "  UNION ALL\n" +
                "  SELECT name AS title, description AS content, type AS category FROM TEST_SCHEMA.PRODUCTS\n" +
                ");";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.CORTEX_SEARCH_SERVICES, "UNIFIED_SEARCH", content);
        MigrationScript dependency1 = mockTableDependency("ARTICLES", "TEST_SCHEMA");
        MigrationScript dependency2 = mockTableDependency("PRODUCTS", "TEST_SCHEMA");
        List<Script> changedScript = mockScripts().stream().map(schemaScript -> (Script)schemaScript).collect(Collectors.toList());
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        dependencyExtractor.addScripts(changedScript);
        Set<Script> expected = Set.of(
                dependency1,
                dependency2
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Cortex Search Service with multiple tables dependency extractor failed:");
    }

    @Test
    void testCortexSearchServiceObjectType() {
        assertEquals("CORTEX SEARCH SERVICE", ScriptObjectType.CORTEX_SEARCH_SERVICES.getSingular());
        assertEquals("CORTEX_SEARCH_SERVICE", ScriptObjectType.CORTEX_SEARCH_SERVICES.getEscapedSingular());
        assertEquals(ScriptObjectType.ObjectLevel.SCHEMA, ScriptObjectType.CORTEX_SEARCH_SERVICES.getLevel());
        assertFalse(ScriptObjectType.CORTEX_SEARCH_SERVICES.isMigration());
    }

    SchemaScript mockSemanticViewDependency(String name, String schema) {
        String content = "CREATE OR REPLACE SEMANTIC VIEW " + name + "\n" +
                "  TABLES (t AS SOURCE_TABLE PRIMARY KEY (ID))\n" +
                "  DIMENSIONS (t.dim1 AS t.COL1)\n" +
                "  METRICS (t.metric1 AS SUM(t.COL2));";
        return ScriptFactory.getSchemaScript("TEST_DB", schema, ScriptObjectType.SEMANTIC_VIEWS, name, content);
    }

    @Test
    void extractScriptDependenciesSemanticView() {
        String content = "CREATE OR REPLACE SEMANTIC VIEW TEST_SCHEMA.SALES_ANALYTICS\n" +
                "  TABLES (\n" +
                "    products AS TEST_SCHEMA.PRODUCTS PRIMARY KEY (PRODUCT_ID),\n" +
                "    orders AS TEST_SCHEMA.ORDERS PRIMARY KEY (ORDER_ID)\n" +
                "  )\n" +
                "  RELATIONSHIPS (orders (PRODUCT_ID) REFERENCES products)\n" +
                "  DIMENSIONS (products.name AS products.NAME)\n" +
                "  METRICS (orders.revenue AS SUM(orders.PRICE));";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.SEMANTIC_VIEWS, "SALES_ANALYTICS", content);
        MigrationScript dependency1 = mockTableDependency("PRODUCTS", "TEST_SCHEMA");
        MigrationScript dependency2 = mockTableDependency("ORDERS", "TEST_SCHEMA");
        List<Script> changedScript = mockScripts().stream().map(schemaScript -> (Script)schemaScript).collect(Collectors.toList());
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        dependencyExtractor.addScripts(changedScript);
        Set<Script> expected = Set.of(
                dependency1,
                dependency2
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Semantic View dependency extractor failed:");
    }

    @Test
    void testSemanticViewObjectType() {
        assertEquals("SEMANTIC VIEW", ScriptObjectType.SEMANTIC_VIEWS.getSingular());
        assertEquals("SEMANTIC_VIEW", ScriptObjectType.SEMANTIC_VIEWS.getEscapedSingular());
        assertEquals(ScriptObjectType.ObjectLevel.SCHEMA, ScriptObjectType.SEMANTIC_VIEWS.getLevel());
        assertFalse(ScriptObjectType.SEMANTIC_VIEWS.isMigration());
    }

    SchemaScript mockAgentDependency(String name, String schema) {
        String content = "CREATE OR REPLACE AGENT " + name + "\n" +
                "  COMMENT = 'Test agent'\n" +
                "  FROM SPECIFICATION\n" +
                "  $$\n" +
                "  tools:\n" +
                "    - tool_spec:\n" +
                "        type: \"cortex_search\"\n" +
                "        name: \"Search1\"\n" +
                "  $$;";
        return ScriptFactory.getSchemaScript("TEST_DB", schema, ScriptObjectType.AGENTS, name, content);
    }

    @Test
    void extractScriptDependenciesAgent() {
        String content = "CREATE OR REPLACE AGENT TEST_SCHEMA.SALES_ASSISTANT\n" +
                "  COMMENT = 'Sales assistant agent'\n" +
                "  FROM SPECIFICATION\n" +
                "  $$\n" +
                "  tools:\n" +
                "    - tool_spec:\n" +
                "        type: \"cortex_analyst_text_to_sql\"\n" +
                "        name: \"Analyst\"\n" +
                "    - tool_spec:\n" +
                "        type: \"cortex_search\"\n" +
                "        name: \"Search\"\n" +
                "  tool_resources:\n" +
                "    Analyst:\n" +
                "      semantic_view: \"TEST_SCHEMA.SALES_ANALYTICS\"\n" +
                "    Search:\n" +
                "      name: \"TEST_SCHEMA.PRODUCT_SEARCH\"\n" +
                "  $$;";
        SchemaScript script = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.AGENTS, "SALES_ASSISTANT", content);
        SchemaScript dependency1 = mockSemanticViewDependency("SALES_ANALYTICS", "TEST_SCHEMA");
        SchemaScript dependency2 = mockCortexSearchDependency("PRODUCT_SEARCH", "TEST_SCHEMA");
        List<Script> changedScript = mockScripts().stream().map(schemaScript -> (Script)schemaScript).collect(Collectors.toList());
        changedScript.add(script);
        changedScript.add(dependency1);
        changedScript.add(dependency2);
        dependencyExtractor.addScripts(changedScript);
        Set<Script> expected = Set.of(
                dependency1,
                dependency2
        );
        Set<Script> actual = dependencyExtractor.extractScriptDependencies(script);
        assertEquals(expected, actual, "Agent dependency extractor failed:");
    }

    @Test
    void testAgentObjectType() {
        assertEquals("AGENT", ScriptObjectType.AGENTS.getSingular());
        assertEquals("AGENT", ScriptObjectType.AGENTS.getEscapedSingular());
        assertEquals(ScriptObjectType.ObjectLevel.SCHEMA, ScriptObjectType.AGENTS.getLevel());
        assertFalse(ScriptObjectType.AGENTS.isMigration());
    }

}