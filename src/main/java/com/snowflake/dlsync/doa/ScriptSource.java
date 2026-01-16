package com.snowflake.dlsync.doa;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.models.*;
import com.snowflake.dlsync.parser.SqlTokenizer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
public class ScriptSource {
    private String scriptRoot;
    private String mainScriptDir;
    private String testScriptDir;
    private final String accountDir = "ACCOUNT";

    public ScriptSource(String scriptRoot) {
        this.scriptRoot = scriptRoot;
        mainScriptDir = Files.exists(Path.of(scriptRoot, "main")) ? Path.of(scriptRoot, "main").toString(): scriptRoot;
        testScriptDir = Path.of(scriptRoot, "test").toString();
        log.debug("Script file reader initialized with scriptRoot: {}", scriptRoot);
    }

    private List<String> readDatabase() {
        File scriptFiles = new File(mainScriptDir);
        List<String> dbs = new ArrayList<>();
        if(scriptFiles.exists()) {
            File[] allDbs = scriptFiles.listFiles();
            for(File file: allDbs) {
                if(file.isDirectory() && !file.getName().equalsIgnoreCase(accountDir)) {
                    dbs.add(file.getName());
                }
            }
        }
        else {
            log.error("Invalid path for script provided: {}", scriptFiles.getAbsolutePath());
            throw new RuntimeException("No valid script source path provided");
        }
        return dbs;
    }

    private List<String> readSchemas(String database) {
        log.info("Reading all schema from database {}", database);
        List<String> schemas = new ArrayList<>();
        File dbFile = Path.of(mainScriptDir, database).toFile();
        if(dbFile.exists()) {
            File[] allFiles = dbFile.listFiles();
            for(File file: allFiles) {
                if(file.isDirectory()) {
                    schemas.add(file.getName());
                }
            }
        }
        return schemas;
    }

    public List<Script> getAllScripts() throws IOException {
        return getAllFileScripts()
                .stream()
                .flatMap(script -> {
                    if (script.isMigration() && script.getMigrations() != null) {
                        return script.getMigrations().stream();
                    } else {
                        return List.of(script).stream();
                    }
                }).collect(Collectors.toList());
    }

    public List<Script> getAllFileScripts() throws IOException {
        List<Script> allScripts = new ArrayList<>();
        List<AccountScript> accountScripts = getScriptsInAccount();
        for(String database: readDatabase()) {
            for(String schema: readSchemas(database)) {
                allScripts.addAll(getScriptsInSchema(database, schema));
            }
        }
        allScripts.addAll(accountScripts);
        return allScripts;
    }

    public List<AccountScript> getScriptsInAccount() throws IOException {
        List<AccountScript> allScripts = new ArrayList<>();
        log.info("Reading all Account objects from {}", accountDir);
        File accountFile = Path.of(mainScriptDir, accountDir).toFile();
        if(!accountFile.exists()) {
            return allScripts;
        }
        File[] scriptTypeDirectories = accountFile.listFiles();
        for(File scriptType: scriptTypeDirectories) {
            if(scriptType.isDirectory() ) {
                File[] scriptFiles = scriptType.listFiles();
                for(File file: scriptFiles) {
                    if(file.getName().toLowerCase().endsWith(".sql")){
                        AccountScript scriptFromFile = SqlTokenizer.parseAccountScript(file.getPath(), file.getName(), scriptType.getName(), Files.readString(file.toPath()));
                        allScripts.add(scriptFromFile);
                    }
                    else {
                        log.warn("Script Skipped, File not SQL: {} ", file.getName());
                    }
                }
            }
            else {
                log.warn("Script file found outside object type directory: {} ", scriptType.getName());
            }
        }
        return allScripts;
    }

    public List<SchemaScript> getScriptsInSchema(String database, String schema) throws IOException {
        log.info("Reading script files from schema: {}", schema);
        List<SchemaScript> scripts = new ArrayList<>();
        File schemaDirectory = Path.of(mainScriptDir, database, schema).toFile();
        File[] scriptTypeDirectories = schemaDirectory.listFiles();

        for(File scriptType: scriptTypeDirectories) {
            if(scriptType.isDirectory() ) {
                File[] scriptFiles = scriptType.listFiles();
                for(File file: scriptFiles) {
                    if(file.getName().toLowerCase().endsWith(".sql")){
                       SchemaScript scriptFromFile = SqlTokenizer.parseSchemaScript(file.getPath(), file.getName(), scriptType.getName(), Files.readString(file.toPath()));
                       scripts.add(scriptFromFile);
                    }
                    else {
                        log.warn("Script Skipped, File not SQL: {} ", file.getName());
                    }
                }
            }
            else {
                log.warn("Script file found outside object type directory: {} ", scriptType.getName());
            }
        }
        return scripts;
    }

    public List<TestScript> getTestScripts(List<Script> scripts) throws IOException {
        List<TestScript> testScripts = scripts.stream()
                .map(script -> {
                    try {
                        return getTestScript(script);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .filter(testScript -> testScript != null)
                .collect(Collectors.toList());
        return testScripts;
    }

    public TestScript getTestScript(Script script) throws IOException {
        String objectName = script.getObjectName() + "_TEST";
        String testScriptPath = script.getScriptPath().replace(".SQL", "_TEST.SQL");
        testScriptPath = testScriptPath.replaceAll("^" + mainScriptDir, testScriptDir);
        File file = Path.of(testScriptPath).toFile();
        if(file.exists()) {
            log.info("Test script file found: {}", file.getPath());
            String content = Files.readString(file.toPath());
            TestScript testScript = ScriptFactory.getTestScript(file.getPath(), objectName, content, script);
            return testScript;
        }
        return null;

    }

    public void createSchemaScriptFiles(List<SchemaScript> scripts) {
        log.debug("Creating script files for the scripts: {}", scripts);
        for(SchemaScript script: scripts) {
            createSchemaScriptFile(script);
        }
    }

    public void createSchemaScriptFile(SchemaScript script) {
        try {
            String scriptFileName = script.getObjectName() + ".SQL";
            String scriptDirectoryPath = String.format("%s/%s/%s/%s", mainScriptDir, script.getDatabaseName(), script.getSchemaName(), script.getObjectType());
            File directory = new File(scriptDirectoryPath);
            directory.mkdirs();
            FileWriter fileWriter = new FileWriter(Path.of(scriptDirectoryPath,  scriptFileName).toFile());
            fileWriter.write(script.getContent());
            fileWriter.close();
            log.debug("File {} created successfully", Path.of(scriptDirectoryPath,  scriptFileName));
        } catch (IOException e) {
            log.error("Error in creating script: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    public void createAccountScriptFiles(List<AccountScript> scripts) {
        log.debug("Creating script files for the account scripts: {}", scripts);
        for(AccountScript script: scripts) {
            createAccountScriptFile(script);
        }
    }

    public void createAccountScriptFile(AccountScript script) {
        try {
            String scriptFileName = script.getObjectName() + ".SQL";
            String scriptDirectoryPath = String.format("%s/%s/%s", mainScriptDir, accountDir, script.getObjectType());
            File directory = new File(scriptDirectoryPath);
            directory.mkdirs();
            FileWriter fileWriter = new FileWriter(Path.of(scriptDirectoryPath,  scriptFileName).toFile());
            fileWriter.write(script.getContent());
            fileWriter.close();
            log.debug("File {} created successfully", Path.of(scriptDirectoryPath,  scriptFileName));
        } catch (IOException e) {
            log.error("Error in creating account script: {}", e.getMessage());
            throw new RuntimeException(e);
        }
    }

    private Script getScriptByName(List<Script> allScripts, String fullObjectName) {
        return allScripts.parallelStream().filter(script -> script.getFullObjectName().equals(fullObjectName)).findFirst().get();
    }
}

