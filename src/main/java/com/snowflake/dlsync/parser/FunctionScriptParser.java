package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.models.SchemaScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.parser.antlr.SnowflakeParser;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FunctionScriptParser extends ScriptParser {
    private boolean isSql = false;
    public FunctionScriptParser(Script script) {
        super(script.getContent());
    }

    public boolean isSql() {
        return isSql;
    }

    @Override
    public void enterCreate_function(SnowflakeParser.Create_functionContext ctx) {
        if(ctx.SQL() != null) {
            isSql = true;
        }
        else {
            log.error("Error in parsing {}, Only sql function are supported in testing", content);
            throw new UnsupportedOperationException("Only SQL functions are supported");
        }
        objectName = ctx.object_name().getText();
    }

    @Override
    public void enterFunction_definition(SnowflakeParser.Function_definitionContext ctx) {
        mainQuery = content.substring(ctx.getStart().getStartIndex(), ctx.getStop().getStopIndex() + 1);
        if(ctx.getChild(0) instanceof SnowflakeParser.StringContext) {

            mainQuery = mainQuery.replace("''", "'");
            mainQuery = mainQuery.substring(1, mainQuery.length() - 1);
        }
        else {
            mainQuery = mainQuery.replace("$$", "");
        }
        mainQuery = String.format("SELECT (%S) AS RETURN FROM MOCK_DATA", mainQuery);

    }

    @Override
    public void exitSnowflake_file(SnowflakeParser.Snowflake_fileContext ctx) {
        ScriptParser scriptParser = new ScriptParser(mainQuery);
        scriptParser.parse();
        objectReferences.addAll(scriptParser.getObjectReferences());
    }
}
