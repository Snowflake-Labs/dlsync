package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.models.SchemaScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.parser.antlr.SnowflakeParser;

public class ViewScriptParser extends ScriptParser {
    public ViewScriptParser(Script script) {
        super(script.getContent());
    }

    @Override
    public void enterCreate_view(SnowflakeParser.Create_viewContext ctx) {
        objectName = ctx.object_name().getText();
    }

    @Override
    public void enterObject_name(SnowflakeParser.Object_nameContext ctx) {
        if(ctx.getParent() instanceof SnowflakeParser.Create_viewContext) {
            objectName = ctx.getText();
        }
        else {
            objectReferences.add(ctx.getText());
        }
    }

    @Override
    public void enterQuery_statement(SnowflakeParser.Query_statementContext ctx) {
        mainQuery =  content.substring(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex()+1);
    }
}
