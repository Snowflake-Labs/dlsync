package com.snowflake.dlsync.doa;

import com.snowflake.dlsync.models.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.sql.*;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ScriptRepoTest {

    @Mock
    private Connection mockConnection;

    @Mock
    private Statement mockStatement;

    @Mock
    private PreparedStatement mockPreparedStatement;

    @Mock
    private ResultSet mockResultSet;

    private ScriptRepo scriptRepo;

    @BeforeEach
    void setUp() throws SQLException {
        MockitoAnnotations.openMocks(this);

        when(mockConnection.createStatement()).thenReturn(mockStatement);
        when(mockConnection.prepareStatement(anyString())).thenReturn(mockPreparedStatement);
        when(mockConnection.getAutoCommit()).thenReturn(true);

        when(mockStatement.executeQuery(anyString()))
            .thenReturn(mockResultSet);

        when(mockResultSet.next())
            .thenReturn(true)
            .thenReturn(true)
            .thenReturn(false);

        when(mockResultSet.getString(1)).thenReturn("TEST_DB");
        when(mockResultSet.getString(2)).thenReturn("PUBLIC");
        when(mockResultSet.getLong(1)).thenReturn(0L);

        when(mockStatement.executeUpdate(anyString())).thenReturn(1);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo = createScriptRepoWithMockedConnection();

        scriptRepo.insertChangeSync(ChangeType.DEPLOY, Status.IN_PROGRESS, "Test deployment");
    }

    @Test
    void testCreateScriptObjectOnlyHashesFalseExecutesScript() throws SQLException {
        SchemaScript script = new SchemaScript(
            "test/MY_TABLE.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "MY_TABLE",
            ScriptObjectType.TABLES,
            "CREATE TABLE MY_TABLE (id INT, name VARCHAR);"
        );
        script.setHash("abc123def456");

        when(mockStatement.execute(anyString())).thenReturn(false);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(script, false);

        ArgumentCaptor<String> executeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStatement, times(1)).execute(executeCaptor.capture());
        assertEquals("CREATE TABLE MY_TABLE (id INT, name VARCHAR);", executeCaptor.getValue(),
            "Script content should be executed when onlyHashes=false");

        verify(mockConnection, atLeastOnce()).setAutoCommit(false);
        verify(mockConnection, atLeastOnce()).setAutoCommit(true);
        verify(mockConnection, atLeastOnce()).commit();

        verify(mockPreparedStatement, atLeastOnce()).executeUpdate();
    }

    @Test
    void testCreateScriptObjectOnlyHashesTrueSkipsScriptExecution() throws SQLException {
        SchemaScript script = new SchemaScript(
            "test/MY_VIEW.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "MY_VIEW",
            ScriptObjectType.VIEWS,
            "CREATE VIEW MY_VIEW AS SELECT * FROM MY_TABLE;"
        );
        script.setHash("xyz789uvw123");

        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(script, true);

        verify(mockStatement, never()).execute(anyString());

        verify(mockConnection, atLeastOnce()).setAutoCommit(false);
        verify(mockConnection, atLeastOnce()).setAutoCommit(true);
        verify(mockConnection, atLeastOnce()).commit();

        verify(mockPreparedStatement, atLeastOnce()).executeUpdate();
    }

    @Test
    void testOnlyHashesParameterControls() throws SQLException {
        SchemaScript scriptWithExecution = new SchemaScript(
            "test/TABLE1.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "TABLE1",
            ScriptObjectType.TABLES,
            "CREATE TABLE TABLE1 (id INT);"
        );
        scriptWithExecution.setHash("hash001");

        SchemaScript scriptHashOnly = new SchemaScript(
            "test/TABLE2.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "TABLE2",
            ScriptObjectType.TABLES,
            "CREATE TABLE TABLE2 (id INT);"
        );
        scriptHashOnly.setHash("hash002");

        when(mockStatement.execute(anyString())).thenReturn(false);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(scriptWithExecution, false);
        scriptRepo.createScriptObject(scriptHashOnly, true);

        verify(mockStatement, times(1)).execute(anyString());

        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(mockStatement, times(1)).execute(captor.capture());
        assertEquals("CREATE TABLE TABLE1 (id INT);", captor.getValue(),
            "Only the first script (onlyHashes=false) should be executed");
    }

    @Test
    void testBothModeUpdateHashes() throws SQLException {
        MigrationScript migrationScript = new MigrationScript(
            new SchemaScript("test/MY_TABLE.sql", "TEST_DB", "TEST_SCHEMA", "MY_TABLE", ScriptObjectType.TABLES, ""),
            "CREATE TABLE MY_TABLE (id INT);",
            0L,
            "admin",
            "DROP TABLE MY_TABLE;",
            "SELECT COUNT(*) FROM MY_TABLE;"
        );
        migrationScript.setHash("migration_hash");

        when(mockStatement.execute(anyString())).thenReturn(false);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(migrationScript, true);

        verify(mockStatement, never()).execute(anyString());

        verify(mockConnection, atLeastOnce()).prepareStatement(anyString());
        verify(mockPreparedStatement, atLeastOnce()).executeUpdate();

        verify(mockConnection, atLeastOnce()).commit();
    }

    @Test
    void testCreateCortexSearchServiceScript() throws SQLException {
        SchemaScript script = new SchemaScript(
            "test/PRODUCT_SEARCH.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "PRODUCT_SEARCH",
            ScriptObjectType.CORTEX_SEARCH_SERVICES,
            "CREATE OR REPLACE CORTEX SEARCH SERVICE TEST_DB.TEST_SCHEMA.PRODUCT_SEARCH\n" +
            "  ON product_description\n" +
            "  ATTRIBUTES product_name, category\n" +
            "  WAREHOUSE = MY_WH\n" +
            "  TARGET_LAG = '1 hour'\n" +
            "AS (SELECT * FROM TEST_DB.TEST_SCHEMA.PRODUCTS);"
        );
        script.setHash("cortex_search_hash");

        when(mockStatement.execute(anyString())).thenReturn(false);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(script, false);

        ArgumentCaptor<String> executeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStatement, times(1)).execute(executeCaptor.capture());
        assertTrue(executeCaptor.getValue().contains("CORTEX SEARCH SERVICE"),
            "Cortex Search Service DDL should be executed");

        verify(mockConnection, atLeastOnce()).commit();
    }

    @Test
    void testCreateSemanticViewScript() throws SQLException {
        SchemaScript script = new SchemaScript(
            "test/SALES_ANALYTICS.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "SALES_ANALYTICS",
            ScriptObjectType.SEMANTIC_VIEWS,
            "CREATE OR REPLACE SEMANTIC VIEW TEST_DB.TEST_SCHEMA.SALES_ANALYTICS\n" +
            "  TABLES (products AS TEST_DB.TEST_SCHEMA.PRODUCTS PRIMARY KEY (PRODUCT_ID))\n" +
            "  DIMENSIONS (products.name AS products.NAME)\n" +
            "  METRICS (products.revenue AS SUM(products.PRICE));"
        );
        script.setHash("semantic_view_hash");

        when(mockStatement.execute(anyString())).thenReturn(false);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(script, false);

        ArgumentCaptor<String> executeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStatement, times(1)).execute(executeCaptor.capture());
        assertTrue(executeCaptor.getValue().contains("SEMANTIC VIEW"),
            "Semantic View DDL should be executed");

        verify(mockConnection, atLeastOnce()).commit();
    }

    @Test
    void testCreateAgentScript() throws SQLException {
        SchemaScript script = new SchemaScript(
            "test/SALES_ASSISTANT.sql",
            "TEST_DB",
            "TEST_SCHEMA",
            "SALES_ASSISTANT",
            ScriptObjectType.AGENTS,
            "CREATE OR REPLACE AGENT TEST_DB.TEST_SCHEMA.SALES_ASSISTANT\n" +
            "  COMMENT = 'Sales assistant agent'\n" +
            "  FROM SPECIFICATION\n" +
            "  $$\n" +
            "  tools:\n" +
            "    - tool_spec:\n" +
            "        type: \"cortex_search\"\n" +
            "        name: \"Search\"\n" +
            "  $$;"
        );
        script.setHash("agent_hash");

        when(mockStatement.execute(anyString())).thenReturn(false);
        when(mockPreparedStatement.executeUpdate()).thenReturn(1);

        scriptRepo.createScriptObject(script, false);

        ArgumentCaptor<String> executeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mockStatement, times(1)).execute(executeCaptor.capture());
        assertTrue(executeCaptor.getValue().contains("AGENT"),
            "Agent DDL should be executed");

        verify(mockConnection, atLeastOnce()).commit();
    }

    private ScriptRepo createScriptRepoWithMockedConnection() throws SQLException {
        Properties props = new Properties();
        props.setProperty("account", "test_account");
        return new ScriptRepo(mockConnection, props);
    }
}



