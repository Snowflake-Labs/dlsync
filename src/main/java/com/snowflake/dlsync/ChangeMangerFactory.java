package com.snowflake.dlsync;

import com.snowflake.dlsync.dependency.DependencyExtractor;
import com.snowflake.dlsync.dependency.DependencyGraph;
import com.snowflake.dlsync.doa.ScriptRepo;
import com.snowflake.dlsync.doa.ScriptSource;
import com.snowflake.dlsync.parser.ParameterInjector;

import java.io.IOException;

public class ChangeMangerFactory {
    public static ChangeManager createChangeManger() throws IOException {
        ConfigManager configManager = new ConfigManager();
        return createChangeManger(configManager);
    }

    public static ChangeManager createChangeManger(String scriptRoot, String profile) throws IOException {
        ConfigManager configManager = new ConfigManager(scriptRoot, profile);
        return createChangeManger(configManager);
    }

    public static ChangeManager createChangeManger(ConfigManager configManager) throws IOException {
        configManager.init();
        ScriptSource scriptSource = new ScriptSource(configManager.getScriptRoot());
        ScriptRepo scriptRepo = new ScriptRepo(configManager.getJdbcProperties());
        ParameterInjector parameterInjector = new ParameterInjector(configManager.getScriptParameters());
        DependencyExtractor dependencyExtractor = new DependencyExtractor();
        DependencyGraph dependencyGraph = new DependencyGraph(dependencyExtractor, configManager.getConfig());
        return new ChangeManager(configManager.getConfig(), scriptSource, scriptRepo, dependencyGraph, parameterInjector);
    }
}
