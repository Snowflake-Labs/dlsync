package com.snowflake.dlsync.models;

public enum ScriptObjectType {
    VIEWS("VIEW"),FUNCTIONS("FUNCTION"),PROCEDURES("PROCEDURE"),FILE_FORMATS("FILE FORMAT"),TABLES("TABLE"),STREAMS("STREAM"),SEQUENCES("SEQUENCE"),STAGES("STAGE"),TASKS("TASK");

    private final String singular;
    private ScriptObjectType(String type) {
        this.singular = type;
    }
    public String getSingular() {
        return singular;
    }
    public String getEscapedSingular() {
        return singular.replace(" ", "_");
    }
    public boolean isMigration() {
        switch (this) {
            case TABLES:
            case STREAMS:
            case SEQUENCES:
            case STAGES:
            case TASKS:
                return true;
            default:
                return false;
        }
    }
}
