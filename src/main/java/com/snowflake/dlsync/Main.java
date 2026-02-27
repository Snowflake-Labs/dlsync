package com.snowflake.dlsync;

import com.snowflake.dlsync.models.ChangeType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.cli.*;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

@Slf4j
public class Main {

    public static void main(String[] args) throws SQLException {
        log.info("DlSync change Manager started with args: {}", Arrays.toString(args));
        ChangeManager changeManager = null;
        ChangeType changeType = null;
        boolean onlyHashes = false;
        List<String> schemas = null;
        try {
            changeType = getChangeType(args);
            CommandLine commandLine = buildCommandOptions(args);
            onlyHashes = commandLine.hasOption("only-hashes");
            String scriptRoot = commandLine.getOptionValue("script-root");
            String profile = commandLine.getOptionValue("profile");
            String targetSchemas = commandLine.getOptionValue("target-schemas");
            changeManager = ChangeMangerFactory.createChangeManger(scriptRoot, profile);
            switch (changeType) {
                case DEPLOY:
                    changeManager.deploy(onlyHashes);
                    log.info("DLsync Changes deployed successfully.");
                    break;
                case ROLLBACK:
                    changeManager.rollback();
                    log.info("DLsync Changes rollback successfully.");
                    break;
                case VERIFY:
                    if(changeManager.verify()) {
                        log.info("DLsync Changes verified successfully.");
                    }
                    else {
                        log.error("DLsync Changes verification failed.");
                    }
                    break;
                case CREATE_SCRIPT:
                    changeManager.createAllScriptsFromDB(targetSchemas);
                    log.info("DLsync created all scripts from DB.");
                    break;
                case CREATE_LINEAGE:
                    changeManager.createLineage();
                    log.info("DLsync successfully created lineage to DB.");
                    break;
                case TEST:
                    changeManager.test();
                    log.info("DLsync successfully tested.");
                    break;
                case PLAN:
                    changeManager.plan();
                    log.info("DLSync plan completed successfully.");
                    break;
                default:
                    log.error("Change type not specified as an argument.");
            }

        } catch (IOException e) {
            e.printStackTrace();
            log.error("Error: {} ", e);
            changeManager.endSyncError(changeType, e.getMessage());
            System.exit(2);
        } catch (SQLException e) {
            e.printStackTrace();
            log.error("Error: {} ", e);
            changeManager.endSyncError(changeType, e.getMessage());
            System.exit(3);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            log.error("Error: {} ", e);
            changeManager.endSyncError(changeType, e.getMessage());
            System.exit(4);
        } catch (ParseException e) {
            log.error("Error: {} ", e);
            changeManager.endSyncError(changeType, e.getMessage());
            System.exit(5);
        }
        catch (Exception e) {
            e.printStackTrace();
            log.error("Error: {}", e.getMessage());
            changeManager.endSyncError(changeType, e.getMessage());
            System.exit(1111);
        }
    }

    public static CommandLine buildCommandOptions(String[] args) throws ParseException {
        Options options = new Options();
        try {
            String[] argsWithoutCommand = Arrays.copyOfRange(args, 1, args.length);
            Option onlyHashes = new Option("o", "only-hashes", false, "Deploy only hashes to database");
            options.addOption(onlyHashes);
            Option scriptRoot = new Option("s", "script-root", true, "Script root directory");
            options.addOption(scriptRoot);
            Option profile = new Option("p", "profile", true, "Profile to use");
            options.addOption(profile);
            Option targetSchemas = new Option("t", "target-schemas", true, "Comma separated list of schemas to use");
            options.addOption(targetSchemas);
            CommandLine commandLine = new DefaultParser().parse(options, argsWithoutCommand);
            return commandLine;
        } catch (ParseException e) {
            new HelpFormatter().printHelp("dlsync [deploy|rollback|verify|create-script|create-lineage|test|plan] [options]", "options:", options, "");
            throw e;
        }

    }

    public static ChangeType getChangeType(String[] args) {
        return args.length >= 1 ? ChangeType.valueOf(args[0].toUpperCase()) : ChangeType.VERIFY;
    }


}
