package com.snowflake.dlsync;

import com.snowflake.dlsync.dependency.DependencyGraph;
import com.snowflake.dlsync.doa.ScriptRepo;
import com.snowflake.dlsync.doa.ScriptSource;
import com.snowflake.dlsync.models.*;
import com.snowflake.dlsync.parser.ParameterInjector;
import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
public class ChangeManager {
    private Config config;
    private ScriptSource scriptSource;
    private ScriptRepo scriptRepo;
    private DependencyGraph dependencyGraph;
    private ParameterInjector parameterInjector;

    public ChangeManager(Config config, ScriptSource scriptSource, ScriptRepo scriptRepo, DependencyGraph dependencyGraph, ParameterInjector parameterInjector) {
        this.config= config;
        this.scriptSource = scriptSource;
        this.scriptRepo = scriptRepo;
        this.dependencyGraph = dependencyGraph;
        this.parameterInjector = parameterInjector;
    }

    private void validateScript(Script script) {
        if(script instanceof MigrationScript && scriptRepo.isScriptVersionDeployed(script)) {
            log.error("Migration type script changed. Script for the object {} has changed from previous deployments.", script.getId());
            throw new RuntimeException("Migration type scripts should not change.");
        }
    }
    public void deploy(boolean onlyHashes) throws SQLException, IOException, NoSuchAlgorithmException{
        log.info("Started Deploying {}", onlyHashes?"Only Hashes":"scripts");
        startSync(ChangeType.DEPLOY);
        scriptRepo.loadScriptHash();
        List<Script> changedScripts = scriptSource.getAllScripts()
                .stream()
                .filter(script -> !config.isScriptExcluded(script))
                .filter(script -> scriptRepo.isScriptChanged(script))
                .collect(Collectors.toList());
        dependencyGraph.addNodes(changedScripts);
        List<Script> sequencedScript = dependencyGraph.topologicalSort();
        log.info("Deploying {} change scripts to db.", sequencedScript.size());
        int size = sequencedScript.size();
        int index = 1;
        for(Script script: sequencedScript) {
            log.info("{} of {}: Deploying object: {}", index++, size, script);
            parameterInjector.injectParameters(script);
            validateScript(script);
            scriptRepo.createScriptObject(script, onlyHashes);
        }
        endSyncSuccess(ChangeType.DEPLOY, (long)sequencedScript.size());
    }

    public void rollback() throws SQLException, IOException {
        log.info("Starting ROLLBACK scripts.");
        startSync(ChangeType.ROLLBACK);
        Set<String> deployedScriptIds = new HashSet<>(scriptRepo.loadScriptHash());
        scriptSource.getAllScripts().forEach(script -> deployedScriptIds.remove(script.getId()));
        List<MigrationScript> migrations = scriptRepo.getMigrationScripts(deployedScriptIds);
        dependencyGraph.addNodes(migrations);

        List<Script> changedScripts = scriptSource.getAllScripts()
                .stream()
                .filter(script -> !config.isScriptExcluded(script))
                .filter(script -> !script.getObjectType().isMigration())
                .filter(script -> scriptRepo.isScriptChanged(script))
                .collect(Collectors.toList());
        dependencyGraph.addNodes(changedScripts);

        List<Script> sequencedScript = dependencyGraph.topologicalSort();
        int size = sequencedScript.size();
        int index = 1;
        for(int i = sequencedScript.size() - 1; i >= 0; i--) {
            Script script = sequencedScript.get(i);
            if(script instanceof MigrationScript) {
                MigrationScript migration = (MigrationScript)script;
                log.info("{} of {}: Rolling-back object: {}", index++, size, migration);
                parameterInjector.injectParametersAll(migration);
                scriptRepo.executeRollback(migration);
            }
            else {
                log.info("{} of {}: Rolling-back object: {}", index++, size, script);
                parameterInjector.injectParameters(script);
                scriptRepo.createScriptObject(script, false);
            }

        }
        endSyncSuccess(ChangeType.ROLLBACK, 0L);
    }

    public boolean verify() throws IOException, NoSuchAlgorithmException, SQLException{
        log.info("Started verify scripts.");
        startSync(ChangeType.VERIFY);
        scriptRepo.loadDeployedHash();
        int failedCount = 0;
        Set<Script> sourceScripts = scriptSource.getAllScripts().stream()
                .filter(script -> !config.isScriptExcluded(script))
                .collect(Collectors.toSet());

        List<String> schemaNames = scriptRepo.getAllSchemasInDatabase(scriptRepo.getDatabaseName());
        for(String schema: schemaNames) {
            List<Script> stateScripts = scriptRepo.getStateScriptsInSchema(schema)
                    .stream()
                    .filter(script -> !config.isScriptExcluded(script))
                    .collect(Collectors.toList());

            for(Script script: stateScripts) {
                parameterInjector.parametrizeScript(script, true);
                Script sourceScript = sourceScripts.stream().filter(s -> s.equals(script)).findFirst().orElse(null);
                if(sourceScript == null) {
                    log.error("Script [{}] is not found in source.", script);
                    failedCount++;
                    continue;
                }
                if (!scriptRepo.compareScript(script, sourceScript)) {
                    failedCount++;
                    log.error("Script verification failed for {}. The source script is different from db object [{}] ", script, script.getContent());
                } else {
                    log.info("Verified Script {} is correct.", script);
                }
            }
        }

        Map<String, List<MigrationScript>> groupedMigrationScripts = sourceScripts.stream()
                .filter(script -> script instanceof MigrationScript)
                .map(script -> (MigrationScript)script)
                .collect(Collectors.groupingBy(Script::getObjectName));

        for(String objectName: groupedMigrationScripts.keySet()) {
            List<MigrationScript> sameObjectMigrations = groupedMigrationScripts.get(objectName);
            Optional<MigrationScript> lastMigration = sameObjectMigrations.stream().sorted(Comparator.comparing(MigrationScript::getVersion).reversed()).findFirst();
            if (lastMigration.isPresent()) {
                MigrationScript migrationScript = lastMigration.get();
                parameterInjector.injectParametersAll(migrationScript);
                if (!scriptRepo.executeVerify(migrationScript)) {
                    failedCount++;
                    log.error("Script verification failed for {}. The verify script [{}] failed to execute.", migrationScript, migrationScript.getVerify());
                } else {
                    log.info("Verified Script {} is correct.", migrationScript);
                }
            }
        }

        if(failedCount != 0) {
            log.error("Verification failed!");
            endSyncError(ChangeType.VERIFY, failedCount + " scripts failed to verify.");
            throw new RuntimeException("Verification failed!");
        }
        log.info("All scripts have been verified successfully.");
        endSyncSuccess(ChangeType.VERIFY, (long)sourceScripts.size());
        return true;
    }

    public void createAllScriptsFromDB() throws SQLException, IOException {
        log.info("Started create scripts.");
        startSync(ChangeType.CREATE_SCRIPT);
        HashSet<String> configTableWithParameter = new HashSet<>();
        if(config != null && config.getConfigTables() != null) {
            configTableWithParameter.addAll(config.getConfigTables());
        }
        Set<String> configTables = parameterInjector.injectParameters(configTableWithParameter);
        List<String> schemaNames = scriptRepo.getAllSchemasInDatabase(scriptRepo.getDatabaseName());
        int count = 0;
        for(String schema: schemaNames) {
            List<Script> scripts = scriptRepo.getAllScriptsInSchema(schema);
            for(Script script: scripts) {
                count++;
                if(configTables.contains(script.getFullObjectName())) {
                    scriptRepo.addConfig(script);
                }
                parameterInjector.parametrizeScript(script, false);
            }
            scriptSource.createScriptFiles(scripts);
        }
        endSyncSuccess(ChangeType.CREATE_SCRIPT, (long)count);

    }

    public void createLineage() throws IOException, SQLException {
        log.info("Started Lineage graph.");
        startSync(ChangeType.CREATE_LINEAGE);
        List<Script> scripts = scriptSource.getAllScripts();
        dependencyGraph.addNodes(scripts);
        List<ScriptDependency> manualDependencies = config.getDependencyOverride()
                .stream()
                .flatMap(dependencyOverride -> {
                    Script script = scripts.stream().filter(s -> s.getFullObjectName().equals(dependencyOverride.getScript())).findFirst().get();
                    List<Script> dependencies = dependencyOverride.getDependencies()
                            .stream()
                            .map(dependencyName -> scripts.stream().filter(s -> s.getFullObjectName().equals(dependencyName)).findFirst().get())
                            .collect(Collectors.toList());
                    return dependencies.stream().map(dependency -> new ScriptDependency(script, dependency));
                })
                .collect(Collectors.toList());

        List<ScriptDependency> dependencyList = dependencyGraph.getDependencyList();
        dependencyList.addAll(manualDependencies);
        scriptRepo.insertDependencyList(dependencyList);
        endSyncSuccess(ChangeType.CREATE_LINEAGE, (long)dependencyList.size());
    }

    public void startSync(ChangeType changeType) throws SQLException {
        scriptRepo.insertChangeSync(changeType, Status.IN_PROGRESS, changeType.toString() + " started.");
    }

    public void endSyncError(ChangeType changeType, String message) throws SQLException {
        scriptRepo.updateChangeSync(changeType, Status.ERROR, message, null);
    }

    public void endSyncSuccess(ChangeType changeType, Long changeCount) throws SQLException {
        scriptRepo.updateChangeSync(changeType, Status.SUCCESS, "Successfully completed " + changeType.toString() , changeCount);
    }


}

