package com.snowflake.dlsync.dependency;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.models.Config;
import com.snowflake.dlsync.models.SchemaScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.models.ScriptObjectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DependencyGraphTest {

    private DependencyGraph dependencyGraph;

    @BeforeEach
    void setUp() throws IOException {
        DependencyExtractor dependencyExtractor = new DependencyExtractor();
        dependencyGraph = new DependencyGraph(dependencyExtractor, new Config());
    }

    @AfterEach
    void tearDown() {
    }

    @Test
    void topologicalSortTest() {
        String content1 = "CREATE OR REPLACE VIEW VIEW1 AS SELECT * FROM TABLE1;";
        String content2 = "create OR REPLACE VIEW VIEW2 AS SELECT * FROM VIEW1;";
        String content3 = "CREATE OR replace VIEW VIEW3 AS SELECT * FROM VIEW2;";
        String content4 = "create or replace view VIEW4 AS SELECT * FROM VIEW3;";

        SchemaScript script1 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content1);
        SchemaScript script2 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW2", content2);
        SchemaScript script3 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW3", content3);
        SchemaScript script4 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW4", content4);

        dependencyGraph.addNodes(List.of(script1, script2, script3, script4));
        List<Script> expected = List.of(script1, script2, script3, script4);

        List<Script> actual = dependencyGraph.topologicalSort();

        assertEquals(expected, actual);
    }

    @Test
    void topologicalSortMultipleDependencyTest() {
        String content1 = "CREATE OR REPLACE VIEW VIEW1 AS SELECT * FROM TABLE1;";
        String content2 = "CREATE OR REPLACE VIEW VIEW2 AS SELECT * FROM VIEW1 JOIN TABLE2 TB2 ON V1.ID=TB2.ID;";
        String content3 = "CREATE OR REPLACE VIEW VIEW3 AS SELECT * FROM VIEW2 v2 JOIN VIEW1 V1 ON V1.ID=V2.ID;";
        String content4 = "CREATE OR REPLACE VIEW VIEW4 AS SELECT * FROM TABLE3 WHERE ID = (SELECT ID FROM VIEW3);";
        String content5 = "CREATE OR REPLACE VIEW VIEW5 AS SELECT * FROM VIEW4 V3 JOIN TABLE4 TB4 ON V3.ID=TB3.ID;";
        String content6 = "CREATE OR REPLACE VIEW VIEW6 AS SELECT * FROM VIEW5 V5 JOIN VIEW2 V2 ON V2.ID=V5.ID;";
        String content7 = "CREATE OR REPLACE VIEW VIEW7 AS SELECT * FROM VIEW6 V5 JOIN VIEW1 V1 ON V5.ID=V1.ID;";
        String content8 = "CREATE OR REPLACE VIEW VIEW7 AS SELECT * FROM VIEW1 WHERE ID NOT IN (SELECT ID FROM VIEW7);";

        SchemaScript script1 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW1", content1);
        SchemaScript script2 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW2", content2);
        SchemaScript script3 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW3", content3);
        SchemaScript script4 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW4", content4);
        SchemaScript script5 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW5", content5);
        SchemaScript script6 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW6", content6);
        SchemaScript script7 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW7", content7);
        SchemaScript script8 = ScriptFactory.getSchemaScript("TEST_DB", "TEST_SCHEMA", ScriptObjectType.VIEWS, "VIEW8", content8);

        List<SchemaScript> scripts = new ArrayList<>(List.of(script1, script2, script3, script4, script5, script6, script7, script8));
        Collections.shuffle(scripts);
        dependencyGraph.addNodes(scripts);
        List<SchemaScript> expected = List.of(script1, script2, script3, script4, script5, script6, script7, script8);

        List<Script> actual = dependencyGraph.topologicalSort();

        assertEquals(expected, actual);
    }
}