package com.snowflake.dlsync.models;

import com.snowflake.dlsync.Util;

import java.util.List;
import java.util.Objects;

public abstract class Script {
    private String scriptPath;
    private String objectName;
    private ScriptObjectType objectType;
    private String content;
    private String hash;
    private List<MigrationScript> migrations;

    public Script(String scriptPath, String objectName, ScriptObjectType objectType, String content) {
        this.scriptPath = scriptPath;
        this.objectName = objectName.toUpperCase();
        this.objectType = objectType;
        this.content = content.trim();
        this.hash = hash = Util.getMd5Hash(this.content);
    }

    public Script(String scriptPath, String objectName, ScriptObjectType objectType, String content, List<MigrationScript> migrations) {
        this(scriptPath, objectName, objectType, content);
        this.migrations = migrations;
    }

    public String getScriptPath() {
        return scriptPath;
    }

    public String getObjectName() {
        return objectName;
    }

    public void setObjectName(String objectName) {
        this.objectName = objectName.toUpperCase();
    }

    public ScriptObjectType getObjectType() {
        return objectType;
    }

    public void setObjectType(ScriptObjectType objectType) {
        this.objectType = objectType;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content.trim();
    }

    public List<MigrationScript> getMigrations() {
        if (isMigration()) {
            return migrations;
        }
        throw new UnsupportedOperationException("Migrations are only available for migration type script.");
    }

    public  void setMigrations(List<MigrationScript> migrations) {
        if (isMigration()) {
            this.migrations = migrations;
        }
        else {
            throw new UnsupportedOperationException("Migrations are only available for migration type script.");
        }
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public boolean isMigration() {
        return objectType.isMigration();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Script script = (Script) o;
        return Objects.equals(getObjectType(), script.getObjectType()) && Objects.equals(getId(), script.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

    @Override
    public String toString() {
        return getId();
    }

    public abstract String getId();

    public abstract String getFullObjectName();

    public abstract String resolveObjectReference(String partialName);
}
