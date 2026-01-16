package com.snowflake.dlsync.models;

public enum ScriptObjectType {
    VIEWS("VIEW", false),
    FUNCTIONS("FUNCTION", false),
    PROCEDURES("PROCEDURE", false),
    FILE_FORMATS("FILE FORMAT", false),
    STREAMLITS("STREAMLIT", false),
    PIPES("PIPE", false),
    MASKING_POLICIES("MASKING POLICY", false),
    RESOURCE_MONITORS("RESOURCE MONITOR", false),
    NETWORK_POLICIES("NETWORK POLICY", false),
    SESSION_POLICIES("SESSION POLICY", false),
    PASSWORD_POLICIES("PASSWORD POLICY", false),
    AUTHENTICATION_POLICIES("AUTHENTICATION POLICY", false),
    API_INTEGRATIONS("API INTEGRATION", false),
    NOTIFICATION_INTEGRATIONS("NOTIFICATION INTEGRATION", false),
    SECURITY_INTEGRATIONS("SECURITY INTEGRATION", false),
    STORAGE_INTEGRATIONS("STORAGE INTEGRATION", false),
    WAREHOUSES("WAREHOUSE", false),

    TABLES("TABLE", true),
    STREAMS("STREAM", true),
    SEQUENCES("SEQUENCE", true),
    STAGES("STAGE", true),
    TASKS("TASK", true),
    ALERTS("ALERT", true),
    DYNAMIC_TABLES("DYNAMIC TABLE", true),
    DATABASES("DATABASE", true),
    SCHEMAS("SCHEMA", true),
    ROLES("ROLE", true);

    private final String singular;
    private final boolean isMigration;
    private ScriptObjectType(String type, boolean isMigration) {
        this.singular = type;
        this.isMigration = isMigration;

    }
    public String getSingular() {
        return singular;
    }
    public String getEscapedSingular() {
        return singular.replace(" ", "_");
    }
    public boolean isMigration() {
        return isMigration;
    }
}
