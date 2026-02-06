package com.snowflake.dlsync.models;

public enum ScriptObjectType {
    VIEWS("VIEW", ObjectLevel.SCHEMA, false),
    FUNCTIONS("FUNCTION", ObjectLevel.SCHEMA, false),
    PROCEDURES("PROCEDURE", ObjectLevel.SCHEMA, false),
    FILE_FORMATS("FILE FORMAT", ObjectLevel.SCHEMA, false),
    STREAMLITS("STREAMLIT", ObjectLevel.SCHEMA, false),
    PIPES("PIPE", ObjectLevel.SCHEMA, false),
    MASKING_POLICIES("MASKING POLICY", ObjectLevel.SCHEMA, false),
    CORTEX_SEARCH_SERVICES("CORTEX SEARCH SERVICE", ObjectLevel.SCHEMA, false),
    SEMANTIC_VIEWS("SEMANTIC VIEW", ObjectLevel.SCHEMA, false),
    AGENTS("AGENT", ObjectLevel.SCHEMA, false),
    NETWORK_RULES("NETWORK RULE", ObjectLevel.SCHEMA, false),
    RESOURCE_MONITORS("RESOURCE MONITOR", ObjectLevel.ACCOUNT, false),
    NETWORK_POLICIES("NETWORK POLICY", ObjectLevel.ACCOUNT, false),
    SESSION_POLICIES("SESSION POLICY", ObjectLevel.ACCOUNT, false),
    PASSWORD_POLICIES("PASSWORD POLICY", ObjectLevel.ACCOUNT, false),
    AUTHENTICATION_POLICIES("AUTHENTICATION POLICY", ObjectLevel.ACCOUNT, false),
    API_INTEGRATIONS("API INTEGRATION", ObjectLevel.ACCOUNT, false),
    NOTIFICATION_INTEGRATIONS("NOTIFICATION INTEGRATION", ObjectLevel.ACCOUNT, false),
    SECURITY_INTEGRATIONS("SECURITY INTEGRATION", ObjectLevel.ACCOUNT, false),
    STORAGE_INTEGRATIONS("STORAGE INTEGRATION", ObjectLevel.ACCOUNT, false),
    WAREHOUSES("WAREHOUSE", ObjectLevel.ACCOUNT, false),

    // Migration-enabled objects
    TABLES("TABLE", ObjectLevel.SCHEMA, true),
    STREAMS("STREAM", ObjectLevel.SCHEMA, true),
    SEQUENCES("SEQUENCE", ObjectLevel.SCHEMA, true),
    STAGES("STAGE", ObjectLevel.SCHEMA, true),
    TASKS("TASK", ObjectLevel.SCHEMA, true),
    ALERTS("ALERT", ObjectLevel.SCHEMA, true),
    DYNAMIC_TABLES("DYNAMIC TABLE", ObjectLevel.SCHEMA, true),
    DATABASES("DATABASE", ObjectLevel.ACCOUNT, true),
    SCHEMAS("SCHEMA", ObjectLevel.ACCOUNT, true),
    ROLES("ROLE", ObjectLevel.ACCOUNT, true);

    private final String singular;
    private final ObjectLevel level;
    private final boolean isMigration;

    ScriptObjectType(String type, ObjectLevel level, boolean isMigration) {
        this.singular = type;
        this.level = level;
        this.isMigration = isMigration;
    }

    public String getSingular() {
        return singular;
    }

    public String getEscapedSingular() {
        return singular.replace(" ", "_");
    }

    public ObjectLevel getLevel() {
        return level;
    }

    public boolean isMigration() {
        return isMigration;
    }

    public enum ObjectLevel {
        ACCOUNT,
        SCHEMA
    }
}
