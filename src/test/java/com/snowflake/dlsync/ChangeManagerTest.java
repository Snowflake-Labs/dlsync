package com.snowflake.dlsync;

import com.snowflake.dlsync.dependency.DependencyGraph;
import com.snowflake.dlsync.doa.ScriptRepo;
import com.snowflake.dlsync.doa.ScriptSource;
import com.snowflake.dlsync.models.*;
import com.snowflake.dlsync.parser.ParameterInjector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ChangeManagerTest {

    private ChangeManager changeManager;

    @Mock
    private Config mockConfig;

    @Mock
    private ScriptSource mockScriptSource;

    @Mock
    private ScriptRepo mockScriptRepo;

    @Mock
    private DependencyGraph mockDependencyGraph;

    @Mock
    private ParameterInjector mockParameterInjector;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        changeManager = new ChangeManager(
            mockConfig,
            mockScriptSource,
            mockScriptRepo,
            mockDependencyGraph,
            mockParameterInjector
        );
    }

    @Test
    void testDeployMultipleChangedScriptsWithDependencyOrdering() throws SQLException, IOException, NoSuchAlgorithmException {
        SchemaScript tableScript = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.TABLES,
            "MY_TABLE", "CREATE TABLE MY_TABLE(id INT);"
        );

        SchemaScript viewScript = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "MY_VIEW", "CREATE VIEW MY_VIEW AS SELECT * FROM MY_TABLE;"
        );

        SchemaScript functionScript = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.FUNCTIONS,
            "MY_FUNC", "CREATE FUNCTION MY_FUNC() RETURNS INT AS 'SELECT COUNT(*) FROM MY_VIEW';"
        );

        List<Script> allScripts = Arrays.asList(tableScript, viewScript, functionScript);

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);

        List<Script> orderedScripts = Arrays.asList(tableScript, viewScript, functionScript);
        when(mockDependencyGraph.topologicalSort()).thenReturn(orderedScripts);

        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        changeManager.deploy(false);

        verify(mockScriptRepo, times(3)).createScriptObject(any(), eq(false));

        ArgumentCaptor<Script> scriptCaptor = ArgumentCaptor.forClass(Script.class);
        verify(mockScriptRepo, times(3)).createScriptObject(scriptCaptor.capture(), eq(false));

        List<Script> executedScripts = scriptCaptor.getAllValues();
        assertEquals("MY_TABLE", executedScripts.get(0).getObjectName(), "TABLE should be deployed first");
        assertEquals("MY_VIEW", executedScripts.get(1).getObjectName(), "VIEW should be deployed second");
        assertEquals("MY_FUNC", executedScripts.get(2).getObjectName(), "FUNCTION should be deployed third");

        verify(mockParameterInjector, times(3)).injectParameters(isA(Script.class));
    }

    @Test
    void testDeployWithNoChanges() throws SQLException, IOException, NoSuchAlgorithmException {
        SchemaScript script1 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW1", "CREATE VIEW VIEW1 AS SELECT 1;"
        );

        List<Script> allScripts = Collections.singletonList(script1);

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(false);
        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        changeManager.deploy(false);

        verify(mockScriptRepo, never()).createScriptObject(any(), anyBoolean());
        verify(mockParameterInjector, never()).injectParameters(isA(Script.class));
    }

    @Test
    void testDeployWithManyScripts() throws SQLException, IOException, NoSuchAlgorithmException {
        List<Script> allScripts = new ArrayList<>();
        List<Script> changedScripts = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            SchemaScript script = ScriptFactory.getSchemaScript(
                "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
                "VIEW_" + i, "CREATE VIEW VIEW_" + i + " AS SELECT " + i + ";"
            );
            allScripts.add(script);
            changedScripts.add(script);
        }

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(changedScripts);
        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        changeManager.deploy(false);

        verify(mockScriptRepo, times(10)).createScriptObject(any(), eq(false));
        verify(mockParameterInjector, times(10)).injectParameters(isA(Script.class));
    }

    @Test
    void testDeployOnlyHashesCallsCreateScriptObjectWithOnlyHashesTrue() throws SQLException, IOException, NoSuchAlgorithmException {
        SchemaScript script1 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW1", "CREATE VIEW VIEW1 AS SELECT 1;"
        );
        SchemaScript script2 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW2", "CREATE VIEW VIEW2 AS SELECT 2;"
        );
        SchemaScript script3 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW3", "CREATE VIEW VIEW3 AS SELECT 3;"
        );

        List<Script> allScripts = Arrays.asList(script1, script2, script3);

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(allScripts);
        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        changeManager.deploy(true);

        verify(mockScriptRepo, times(3)).createScriptObject(any(), eq(true));
        verify(mockScriptRepo, never()).createScriptObject(any(), eq(false));
        verify(mockParameterInjector, times(3)).injectParameters(isA(Script.class));
    }

    @Test
    void testNormalDeployCallsCreateScriptObjectWithOnlyHashesFalse() throws SQLException, IOException, NoSuchAlgorithmException {
        SchemaScript script1 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW1", "CREATE VIEW VIEW1 AS SELECT 1;"
        );
        SchemaScript script2 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW2", "CREATE VIEW VIEW2 AS SELECT 2;"
        );

        List<Script> allScripts = Arrays.asList(script1, script2);

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(allScripts);
        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        changeManager.deploy(false);

        verify(mockScriptRepo, times(2)).createScriptObject(any(), eq(false));
        verify(mockScriptRepo, never()).createScriptObject(any(), eq(true));
        verify(mockParameterInjector, times(2)).injectParameters(isA(Script.class));
    }

    @Test
    void testDeployWithContinueOnFailureFalseThrowsOnFirstError() throws SQLException, IOException, NoSuchAlgorithmException {
        SchemaScript script1 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW1", "CREATE VIEW VIEW1 AS SELECT 1;"
        );
        SchemaScript script2 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW2", "CREATE VIEW VIEW2 AS SELECT 2;"
        );
        SchemaScript script3 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW3", "CREATE VIEW VIEW3 AS SELECT 3;"
        );

        List<Script> allScripts = Arrays.asList(script1, script2, script3);
        List<Script> orderedScripts = new ArrayList<>(allScripts);

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(orderedScripts);
        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        doNothing()
            .doThrow(new SQLException("View already exists"))
            .doNothing()
            .when(mockScriptRepo).createScriptObject(any(), eq(false));

        assertThrows(SQLException.class, () -> {
            changeManager.deploy(false);
        }, "Should throw exception when continueOnFailure is false");

        verify(mockScriptRepo, times(2)).createScriptObject(any(), eq(false));
        verify(mockParameterInjector, atMost(2)).injectParameters(isA(Script.class));
    }

    @Test
    void testDeployWithContinueOnFailureTrueContinuesDespiteErrors() throws SQLException, IOException, NoSuchAlgorithmException {
        SchemaScript script1 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW1", "CREATE VIEW VIEW1 AS SELECT 1;"
        );
        SchemaScript script2 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW2", "CREATE VIEW VIEW2 AS SELECT 2;"
        );
        SchemaScript script3 = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "VIEW3", "CREATE VIEW VIEW3 AS SELECT 3;"
        );

        List<Script> allScripts = Arrays.asList(script1, script2, script3);
        List<Script> orderedScripts = new ArrayList<>(allScripts);

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(orderedScripts);
        when(mockConfig.isContinueOnFailure()).thenReturn(true);

        doNothing()
            .doThrow(new SQLException("View already exists"))
            .doNothing()
            .when(mockScriptRepo).createScriptObject(any(), eq(false));

        assertThrows(RuntimeException.class, () -> {
            changeManager.deploy(false);
        }, "Should throw exception after attempting all scripts");

        verify(mockScriptRepo, times(3)).createScriptObject(any(), eq(false));
        verify(mockParameterInjector, times(3)).injectParameters(isA(Script.class));
    }

    @Test
    void testDeployMultipleErrorsWithContinueOnFailureTrue() throws SQLException, IOException, NoSuchAlgorithmException {
        List<Script> allScripts = new ArrayList<>();
        List<Script> orderedScripts = new ArrayList<>();

        for (int i = 1; i <= 4; i++) {
            SchemaScript script = ScriptFactory.getSchemaScript(
                "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
                "VIEW_" + i, "CREATE VIEW VIEW_" + i + " AS SELECT " + i + ";"
            );
            allScripts.add(script);
            orderedScripts.add(script);
        }

        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(orderedScripts);
        when(mockConfig.isContinueOnFailure()).thenReturn(true);

        doNothing()
            .doThrow(new SQLException("Error 1"))
            .doNothing()
            .doThrow(new SQLException("Error 2"))
            .when(mockScriptRepo).createScriptObject(any(), eq(false));

        assertThrows(RuntimeException.class, () -> {
            changeManager.deploy(false);
        }, "Should throw aggregated exception after all attempts");

        verify(mockScriptRepo, times(4)).createScriptObject(any(), eq(false));
    }

    @Test
    void testRollbackDeployedMigrationScriptsInReverseOrder() throws SQLException, IOException {
        MigrationScript migration1 = ScriptFactory.getMigrationScript(
            ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.TABLES, "MY_TABLE", ""),
            "CREATE TABLE TEST_DB.TEST_SCHEMA.MY_TABLE(id INT);",
            0L,
            "admin",
            "DROP TABLE IF EXISTS MY_TABLE;",
            "SELECT COUNT(*) FROM MY_TABLE;"
        );

        MigrationScript migration2 = ScriptFactory.getMigrationScript(
            ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "MY_VIEW", ""),
            "CREATE VIEW TEST_DB.TEST_SCHEMA.MY_VIEW AS SELECT * FROM TEST_DB.TEST_SCHEMA.MY_TABLE;",
            0L,
            "admin",
            "DROP VIEW IF EXISTS MY_VIEW;",
            "SELECT COUNT(*) FROM MY_VIEW;"
        );

        List<MigrationScript> deployedMigrations = Arrays.asList(migration1, migration2);

        when(mockScriptSource.getAllScripts()).thenReturn(Collections.emptyList());
        when(mockScriptRepo.getDeployedMigrationScripts(any())).thenReturn(deployedMigrations);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(false);
        when(mockConfig.isContinueOnFailure()).thenReturn(false);
        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptRepo.loadScriptHash()).thenReturn(new HashSet<>(Arrays.asList("TABLE:MY_TABLE", "VIEW:MY_VIEW")));

        List<Script> rollbackOrder = Arrays.asList(migration1, migration2);
        when(mockDependencyGraph.topologicalSort()).thenReturn(rollbackOrder);

        changeManager.rollback();

        ArgumentCaptor<MigrationScript> scriptCaptor = ArgumentCaptor.forClass(MigrationScript.class);
        verify(mockScriptRepo, times(2)).executeRollback(scriptCaptor.capture());

        List<MigrationScript> rollbackScripts = scriptCaptor.getAllValues();
        assertEquals("MY_VIEW", rollbackScripts.get(0).getObjectName(),
            "VIEW should be rolled back first (reverse order)");
        assertEquals("MY_TABLE", rollbackScripts.get(1).getObjectName(),
            "TABLE should be rolled back second");

        verify(mockParameterInjector, times(2)).injectParametersAll(isA(MigrationScript.class));
    }

    @Test
    void testRollbackWithMissingRollbackScripts() throws SQLException, IOException {
        SchemaScript viewScript = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "MY_VIEW", "CREATE VIEW MY_VIEW AS SELECT 1;"
        );

        List<Script> allScripts = Collections.singletonList(viewScript);

        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.getDeployedMigrationScripts(any())).thenReturn(Collections.emptyList());
        when(mockConfig.isContinueOnFailure()).thenReturn(false);
        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);
        when(mockDependencyGraph.topologicalSort()).thenReturn(allScripts);

        changeManager.rollback();

        verify(mockScriptRepo, times(1)).createScriptObject(any(), eq(false));
    }

    @Test
    void testRollbackWithNoDeployedScripts() throws SQLException, IOException {
        when(mockScriptSource.getAllScripts()).thenReturn(Collections.emptyList());
        when(mockScriptRepo.getDeployedMigrationScripts(any())).thenReturn(Collections.emptyList());
        when(mockConfig.isContinueOnFailure()).thenReturn(false);

        changeManager.rollback();

        verify(mockScriptRepo, never()).createScriptObject(any(), anyBoolean());
    }

    @Test
    void testRollbackWithDeploymentChanges() throws SQLException, IOException {
        SchemaScript newView = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "NEW_VIEW", "CREATE VIEW NEW_VIEW AS SELECT 1;"
        );

        SchemaScript changedView = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "CHANGED_VIEW", "CREATE VIEW CHANGED_VIEW AS SELECT 2;"
        );

        MigrationScript deployedTable = ScriptFactory.getMigrationScript(
            ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.TABLES, "MY_TABLE", ""),
            "CREATE TABLE MY_TABLE(id INT);",
            0L,
            "admin",
            "DROP TABLE IF EXISTS MY_TABLE;",
            null
        );

        List<Script> allScripts = Arrays.asList(newView, changedView);
        List<MigrationScript> deployedMigrations = Collections.singletonList(deployedTable);

        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.getDeployedMigrationScripts(any())).thenReturn(deployedMigrations);
        when(mockScriptRepo.isScriptChanged(any())).thenAnswer(invocation -> {
            Script script = invocation.getArgument(0);
            return script.getObjectName().equals("CHANGED_VIEW");
        });
        when(mockConfig.isContinueOnFailure()).thenReturn(false);
        when(mockConfig.isScriptExcluded(any())).thenReturn(false);

        List<Script> rollbackOrder = Arrays.asList(deployedTable, changedView);
        when(mockDependencyGraph.topologicalSort()).thenReturn(rollbackOrder);

        changeManager.rollback();

        verify(mockScriptRepo, times(1)).createScriptObject(any(), eq(false));
    }

    @Test
    @DisplayName("Plan command should NOT make any database changes")
    void testPlanCommandMakesNoDatabaseChanges() throws SQLException, IOException, NoSuchAlgorithmException {
        // Setup: Create test scripts with changes
        SchemaScript tableScript = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.TABLES,
            "MY_TABLE", "CREATE TABLE MY_TABLE(id INT);"
        );

        SchemaScript viewScript = ScriptFactory.getSchemaScript(
            "TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS,
            "MY_VIEW", "CREATE VIEW MY_VIEW AS SELECT * FROM MY_TABLE;"
        );

        List<Script> allScripts = Arrays.asList(tableScript, viewScript);

        // Mock the behavior
        when(mockConfig.isScriptExcluded(any())).thenReturn(false);
        when(mockScriptSource.getAllScripts()).thenReturn(allScripts);
        when(mockScriptRepo.isScriptChanged(any())).thenReturn(true);

        List<Script> orderedScripts = Arrays.asList(tableScript, viewScript);
        when(mockDependencyGraph.topologicalSort()).thenReturn(orderedScripts);

        // Execute plan command
        changeManager.plan();

        // Assert: Verify NO database modifications were made
        verify(mockScriptRepo, never()).createScriptObject(any(), anyBoolean());
        verify(mockScriptRepo, never()).insertChangeSync(any(), any(), anyString());
        verify(mockScriptRepo, never()).updateChangeSync(any(), any(), anyString(), any());
        verify(mockScriptRepo, never()).executeRollback(any());

        // Verify that only read-only operations were called
        verify(mockScriptRepo, times(1)).loadScriptHash();
        verify(mockParameterInjector, times(2)).injectParameters(isA(Script.class));
    }
}

