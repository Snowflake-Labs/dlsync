package com.snowflake.dlsync.models;

public class MigrationScript extends Script {

    private Script parentScript;
    private Long version;
    private String author;
    private String rollback;
    private String verify;

    public MigrationScript(Script parentScript, String content, Long version, String author, String rollback, String verify) {
        super(parentScript.getScriptPath(), parentScript.getObjectName(), parentScript.getObjectType(), content);
        this.parentScript = parentScript;
        this.version = version;
        this.author = author;
        this.rollback = rollback;
        this.verify = verify;
    }


    @Override
    public String getId() {
        return String.format("%s:%s", parentScript.getFullObjectName(), version);
    }

    @Override
    public String getFullObjectName() {
        return parentScript.getFullObjectName();
    }

    @Override
    public String resolveObjectReference(String partialName) {
        return parentScript.resolveObjectReference(partialName);
    }

    public Script getParentScript() {
        return parentScript;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getRollback() {
        return rollback;
    }

    public void setRollback(String rollback) {
        this.rollback = rollback;
    }

    public String getVerify() {
        return verify;
    }

    public void setVerify(String verify) {
        this.verify = verify;
    }

}
