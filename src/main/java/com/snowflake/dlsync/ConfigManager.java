package com.snowflake.dlsync;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.snowflake.dlsync.models.Config;
import com.snowflake.dlsync.models.Script;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class ConfigManager {
    private final static String CONFIG_FILE_NAME = "config.yaml";
    private final static String[] JDBC_KEY = {"user", "password", "account", "warehouse", "db", "schema", "role", "authenticator"};
    private final static String SCRIPT_ROOT_KEY = "SCRIPT_ROOT";
    private String scriptRoot;
    private String profile;
    private Properties scriptParameters;
    private Properties jdbcProperties;
    private Config config;
    private AtomicBoolean isInitialized = new AtomicBoolean(false);

    public ConfigManager() {
        this(System.getenv(SCRIPT_ROOT_KEY), StringUtils.isEmpty(System.getenv("profile")) ? "dev" : System.getenv("profile").toLowerCase());
    }

    public ConfigManager(String scriptRoot, String profile) {
        this.scriptRoot = scriptRoot == null ? System.getenv(SCRIPT_ROOT_KEY) : scriptRoot;
        String fallbackProfile = StringUtils.isEmpty(System.getenv("profile")) ? "dev" : System.getenv("profile").toLowerCase();
        this.profile = profile == null ? fallbackProfile : profile;
        log.info("Using [{}] as script root path.", this.scriptRoot);
        log.info("Using [{}] as profile.", this.profile);
    }

    public ConfigManager(String scriptRoot, String profile, Properties jdbcProperties, Properties scriptParameters, Config config) {
        this.scriptRoot = scriptRoot;
        this.profile = profile;
        this.jdbcProperties = jdbcProperties;
        this.scriptParameters = scriptParameters;
        this.config = config;
    }

    public void init() throws IOException {
        if(isInitialized.compareAndSet(false, true)) {
            readEnvVariables();
            readConfig();
            readParameters();
        }
    }

    public void readEnvVariables() {
        jdbcProperties = new Properties();
        for(String key: JDBC_KEY) {
            String jdbcConfigValue = System.getenv(key);
            if(jdbcConfigValue == null) {
                if(!key.equals("authenticator")) {
                    log.warn("JDBC connection property {} not found", key);
                }
            }
            else {
                jdbcProperties.put(key, jdbcConfigValue);
            }
        }
    }

    public void readConfig() {
        File configFile = Path.of(scriptRoot, CONFIG_FILE_NAME).toFile();
        try {
            if(configFile.exists()) {
                ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
                mapper.findAndRegisterModules();
                config = mapper.readValue(configFile, Config.class);
            }
            else {
                config = new Config();
            }
        } catch (IOException e) {
            log.error("Failed to parse config file [{}]", configFile.getAbsolutePath());
            throw new RuntimeException("Can not parse Config file. Please use yaml file with allowed properties", e);
        }
    }

    public void readParameters() throws IOException {
        scriptParameters = new Properties();
        String propertiesFilePath = "parameter-" + profile + ".properties";
        log.debug("Loading property file from [{}]", propertiesFilePath);
        InputStream input = new FileInputStream(Path.of(scriptRoot, propertiesFilePath).toFile());
        scriptParameters.load(input);
        Map<String, String> environmentVariables = System.getenv();
        for(String key: environmentVariables.keySet()) {
            if(scriptParameters.containsKey(key)) {
                scriptParameters.put(key, environmentVariables.get(key));
            }
        }
    }


    public Config getConfig() {
        return config;
    }

    public Properties getJdbcProperties() {
        return jdbcProperties;
    }

    public Properties getScriptParameters() {
        return scriptParameters;
    }

    public String getProfile() {
        return profile;
    }

    public String getScriptRoot() {
        return scriptRoot;
    }

}
