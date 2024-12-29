package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.models.MigrationScript;
import com.snowflake.dlsync.models.Script;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
public class ParameterInjector {
    private static final String PARAMETER_FORMAT = "${%s}";
    private static final String PARAMETRIZATION_START_REGEX = "([(),\\.\\s\\'\\\"@])";
    private static final String PARAMETRIZATION_END_REGEX = "([(),;\\.\\s\\'\\\"])";


    private Properties parameters;

    public ParameterInjector(Properties parameters) {
        log.debug("Parameter injector initialized with parameters: {}", parameters);
        this.parameters = parameters;
    }

    private String injectParameters(String content) {
        if(content == null) {
            return null;
        }
        for(String parameter: parameters.stringPropertyNames()) {
            String parameterPlaceholder = String.format(PARAMETER_FORMAT, parameter);
            String regex = "(?i)" + Pattern.quote(parameterPlaceholder);
            String replacement =  Matcher.quoteReplacement(parameters.getProperty(parameter));
            content = content.replaceAll(regex, replacement);
        }
        return content;
    }

    public void injectParameters(Script script) {
        log.debug("Injecting parameter for: {}", script.getObjectName());
        String injectedScript = injectParameters(script.getContent());
        script.setContent(injectedScript);
        log.debug("Script for {} after parameter injected: {}", script.getObjectName(), injectedScript);
    }

    public void injectParametersAll(MigrationScript migration) {
        log.debug("Injecting parameter for: {}", migration.getObjectName());
        String injectedContent = injectParameters(migration.getContent());
        String injectedRollback = injectParameters(migration.getRollback());
        String injectedVerify = injectParameters(migration.getVerify());

        migration.setContent(injectedContent);
        migration.setRollback(injectedRollback);
        migration.setVerify(injectedVerify);
        log.debug("Migration for {} after parameter injected: {}", migration.getObjectName(), migration);
    }




    public void parametrizeScript(Script script, boolean parametrizeObjectName){
        log.debug("Parametrizing script: {}", script.getObjectName());
        String parametrizedScript = script.getContent();
        List<String> parameterKeys = parameters.stringPropertyNames().stream().sorted().collect(Collectors.toList());
        for(String parameter: parameterKeys) {
            String parameterPlaceholder = String.format(PARAMETER_FORMAT, parameter);
            String regex = PARAMETRIZATION_START_REGEX + "(?i)" + Pattern.quote(parameters.getProperty(parameter)) + PARAMETRIZATION_END_REGEX;
            String replacement = "$1" + Matcher.quoteReplacement(parameterPlaceholder) + "$2";
            parametrizedScript = parametrizedScript.replaceAll(regex, replacement);
        }
        script.setContent(parametrizedScript);
        if(parametrizeObjectName) {
            parameterizeObjectName(script);
        }
        log.debug("Script for {} after parameterized: {}", script.getObjectName(), parametrizedScript);
    }

    public void parameterizeObjectName(Script script) {
//        String objectName = script.getObjectName();
        String schemaName = script.getSchemaName();
        String databaseName = script.getDatabaseName();

        List<String> parameterKeys = parameters.stringPropertyNames().stream().sorted().collect(Collectors.toList());
        for(String parameter: parameterKeys) {
            String parameterPlaceholder = String.format(PARAMETER_FORMAT, parameter);
            String regex = "(?i)" + Pattern.quote(parameters.getProperty(parameter));
            String replacement = Matcher.quoteReplacement(parameterPlaceholder);
//            objectName = objectName.replaceAll(regex, replacement);
            schemaName = schemaName.replaceAll(regex, replacement);
            databaseName = databaseName.replaceAll(regex, replacement);
        }

        String oldName = script.getFullObjectName();
//        script.setObjectName(objectName);
        script.setSchemaName(schemaName);
        script.setDatabaseName(databaseName);
        log.debug("Parametrize object name Changed from {} to {}", oldName, script.getFullObjectName());
    }

    public Set<String> injectParameters(Set<String> configs) {
        return configs.stream().map(config -> {
            for(String parameter: parameters.stringPropertyNames()) {
                String parameterPlaceholder = String.format(PARAMETER_FORMAT, parameter);
                String regex = "(?i)" + Pattern.quote(parameterPlaceholder);
                String replacement =  Matcher.quoteReplacement(parameters.getProperty(parameter));
                config = config.replaceAll(regex, replacement);
            }
            return config;
        }).collect(Collectors.toSet());
    }

}
