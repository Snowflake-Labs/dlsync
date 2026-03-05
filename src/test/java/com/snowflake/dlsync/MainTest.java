package com.snowflake.dlsync;


import com.snowflake.dlsync.models.ChangeType;
import org.apache.commons.cli.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class MainTest {

    @Test
    public void testBuildCommandOnlyHashes() throws ParseException {
        String[] args = {"deploy", "--only-hashes"};
        CommandLine commandLine = Main.buildCommandOptions(args);
        assertTrue(commandLine.hasOption("--only-hashes"));

        String[] args2 = {"deploy", "-o"};
        commandLine = Main.buildCommandOptions(args2);
        assertTrue(commandLine.hasOption("--only-hashes"));

        String[] args3 = {"deploy"};
        commandLine = Main.buildCommandOptions(args3);
        assertTrue(!commandLine.hasOption("--only-hashes"));
    }

    @Test
    public void testBuildCommandScriptRoot() throws ParseException {
        String[] args = {"deploy", "--only-hashes", "--script-root", "test"};
        CommandLine commandLine = Main.buildCommandOptions(args);
        assertTrue(commandLine.hasOption("--only-hashes"));
        assertTrue(commandLine.getOptionValue("script-root").equals("test"));

        String[] invalidArgs = new String[]{"deploy", "--script-root"};
        assertThrows(MissingArgumentException.class,  () -> Main.buildCommandOptions(invalidArgs));
    }

    @Test
    public void testBuildCommandProfile() throws ParseException {
        String[] args = {"deploy", "--script-root", "test/scripts", "--profile", "prod"};
        CommandLine commandLine = Main.buildCommandOptions(args);
        assertTrue(!commandLine.hasOption("--only-hashes"));
        assertTrue(commandLine.getOptionValue("script-root").equals("test/scripts"));
        assertTrue(commandLine.getOptionValue("profile").equals("prod"));

        String[] invalidArgs = new String[]{"deploy", "--profile"};
        assertThrows(MissingArgumentException.class,  () -> Main.buildCommandOptions(invalidArgs));
    }

    @Test
    public void testGetChangeType() {
        String[] args = {"deploy"};
        ChangeType changeType = Main.getChangeType(args);
        assertTrue(changeType == ChangeType.DEPLOY);

        String[] args2 = {"rollback"};
        changeType = Main.getChangeType(args2);
        assertTrue(changeType == ChangeType.ROLLBACK);

        String[] args3 = {"verify"};
        changeType = Main.getChangeType(args3);
        assertTrue(changeType == ChangeType.VERIFY);

        String[] args4 = {"create_script"};
        changeType = Main.getChangeType(args4);
        assertTrue(changeType == ChangeType.CREATE_SCRIPT);

        String[] args5 = {"create_lineage"};
        changeType = Main.getChangeType(args5);
        assertTrue(changeType == ChangeType.CREATE_LINEAGE);

        String[] args6 = {"invalid"};
        assertThrows(IllegalArgumentException.class, () -> Main.getChangeType(args6));

        String[] args7 = {};
        changeType = Main.getChangeType(args7);
        assertTrue(changeType == ChangeType.VERIFY);

        String[] args8 = {"DEploy", "--only-hashes"};
        changeType = Main.getChangeType(args8);
        assertTrue(changeType == ChangeType.DEPLOY);
    }

    @Test
    void testGetChangeTypeForPlan() {
        String[] args = {"plan"};
        ChangeType changeType = Main.getChangeType(args);
        assertEquals(ChangeType.PLAN, changeType, "Should recognize 'plan' as PLAN command");

        String[] args2 = {"PLAN"};
        changeType = Main.getChangeType(args2);
        assertEquals(ChangeType.PLAN, changeType, "Should recognize 'PLAN' (uppercase) as PLAN command");

        String[] args3 = {"Plan"};
        changeType = Main.getChangeType(args3);
        assertEquals(ChangeType.PLAN, changeType, "Should recognize 'Plan' (mixed case) as PLAN command");
    }

    @Test
    void testTargetSchema() throws ParseException {
        String[] args = {"create_script", "--target-schemas", "schema1,schema2"};
        CommandLine commandLine = Main.buildCommandOptions(args);
        assertTrue(commandLine.hasOption("--target-schemas"));
        assertTrue(commandLine.getOptionValue("target-schemas").equals("schema1,schema2"));

        String[] args1 = {"create_script", "-t", "schema1,schema2"};
        commandLine = Main.buildCommandOptions(args);
        assertTrue(commandLine.hasOption("--target-schemas"));
        assertTrue(commandLine.getOptionValue("target-schemas").equals("schema1,schema2"));

        String[] args2 = {"create_script", "--script-root", "test/scripts", "--profile", "prod", "--target-schemas", "schema1,schema2"};
        commandLine = Main.buildCommandOptions(args2);
        assertTrue(commandLine.hasOption("--target-schemas"));
        assertTrue(commandLine.getOptionValue("target-schemas").equals("schema1,schema2"));

        String[] args3 = {"create_script", "--target-schemas"};
        assertThrows(MissingArgumentException.class,  () -> Main.buildCommandOptions(args3));

        String[] args4 = {"create_script", "--script-root", "test/scripts", "--profile", "prod"};
        commandLine = Main.buildCommandOptions(args4);
        assertTrue(!commandLine.hasOption("--target-schemas"));
        assertTrue(commandLine.getOptionValue("--target-schemas") == null);

        String[] args5 = {"create_script", "-s", "captured_scripts", "-p", "qa", "-t", "schema1,schema2"};
        commandLine = Main.buildCommandOptions(args5);
        assertTrue(commandLine.hasOption("--target-schemas"));
        assertEquals(commandLine.getOptionValue("--target-schemas"), "schema1,schema2");
    }


}
