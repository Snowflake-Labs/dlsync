package com.snowflake.dlsync;


import com.snowflake.dlsync.models.ChangeType;
import org.apache.commons.cli.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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


}
