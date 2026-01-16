package com.snowflake.dlsync.parser;

import com.snowflake.dlsync.ScriptFactory;
import com.snowflake.dlsync.models.*;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class SqlTokenizer {
    private static final String TOKEN_START_REGEX = "(?:[@=()\\[\\],\\.\\s\\\"\\'])";
    private static final String TOKEN_END_REGEX = "(?:[=()\\[\\],\\.\\s\\\"\\';]|$)";
    private static final char[] TOKENS = {'.', ',', ';', '"', '\'', '[', ']', '(', ')'};
    private static  final String MIGRATION_HEADER = "(\\s*---\\s*(?i)version\\s*:\\s*(?<version>\\d+)\\s*)(,\\s*(?i)author\\s*:\\s*(?<author>\\w+)\\s*)?";
    private static  final String VERSION_REGEX = "(?:^|\n)(--- *(?i)version *: *(?<version>\\d+) *)";
    private static final String AUTHOR_REGEX = "(, *(?i)author *: *(?<author>\\w+) *)?\n";
    private static final String CONTENT_REGEX = "([\\s\\S]+?(?=(\n---)|($)))";
    private static final String ROLL_BACK_REGEX = "((\n--- *(?i)rollback *: +)(?<rollback>[^\n]+))?";
    private static final String VERIFY_REGEX = "((\n--- *(?i)verify *: +)(?<verify>[^\n]+))?";
    private static final String IDENTIFIER_REGEX = "((?:\\\"[^\"]+\\\"\\.)|(?:[{}$a-zA-Z0-9_]+\\.))?((?:\\\"[^\"]+\\\"\\.)|(?:[{}$a-zA-Z0-9_]+\\.))?(?i)";
    private static final String MIGRATION_REGEX = VERSION_REGEX + AUTHOR_REGEX + CONTENT_REGEX + ROLL_BACK_REGEX + VERIFY_REGEX;

    private static final String DDL_REGEX = buildDdlRegex();

    private static final String STRING_LITERAL_REGEX = "(?<!as\\s{1,5})'([^'\\\\]*(?:\\\\.[^'\\\\]*)*(?:''[^'\\\\]*)*)'";

    private static final String VIEW_BODY_REGEX = "(CREATE\\s+OR\\s+REPLACE\\s+VIEW\\s+)(?<name>[\\w.${}]+)(\\s*\\([^\\)]+\\))?\\s+AS\\s+(?<body>[\\s\\S]+)$";
    private static final String FUNCTION_BODY_REGEX = "(CREATE\\s+OR\\s+REPLACE\\s+FUNCTION\\s+)(?<name>[\\w.${}]+)(?:[\\s\\S]*?AS\\s+('|\\$\\$)\\s*)(?<body>[\\s\\S]+)('|\\$\\$)\\s*;$";
    private static final String PROCEDURE_BODY_REGEX = "(CREATE\\s+OR\\s+REPLACE\\s+PROCEDURE\\s+)(?<name>[\\w.${}]+)(?:[\\s\\S]*?AS\\s+('|\\$\\$)\\s*)(?<body>[\\s\\S]+)('|\\$\\$)\\s*;$";
    private static final String FILE_FORMAT_BODY_REGEX = "(CREATE\\s+OR\\s+REPLACE\\s+FILE FORMAT\\s+)(?<body>[\\w.${}]+)([\\s\\S]+)$";

    /**
     * Dynamically builds DDL_REGEX pattern from ScriptObjectType enum values
     * This ensures that all supported object types are included in the regex
     */
    private static String buildDdlRegex() {
        StringBuilder objectTypes = new StringBuilder();
        ScriptObjectType[] types = ScriptObjectType.values();

        for (int i = 0; i < types.length; i++) {
            if (i > 0) {
                objectTypes.append("|");
            }
            // Escape spaces in object type names like "DYNAMIC TABLE" and "FILE FORMAT"
            objectTypes.append(types[i].getSingular().replace(" ", "\\s+"));
        }

        return ";\\n+(CREATE\\s+OR\\s+REPLACE\\s+(TRANSIENT\\s|HYBRID\\s|SECURE\\s)?(?<type>" + objectTypes.toString() + "|\\w+)\\s+(IF\\s+NOT\\s+EXISTS\\s+)?(?<name>[\\\"\\w.]+)([\\s\\S]+?)(?=(;\\nCREATE\\s+)|(;$)))";
    }

    public static List<Migration> parseMigrationScripts(String content) {
        List<Migration> migrations = new ArrayList<>();

        Pattern pattern = Pattern.compile(MIGRATION_REGEX);

        Matcher matcher = pattern.matcher(content);
        while (matcher.find()) {
            String versionedContent = matcher.group();
            Long version = Long.parseLong(matcher.group("version"));
            String author = matcher.group("author");
            String rollback = matcher.group("rollback");
            String verify = matcher.group("verify");
            Migration migration = Migration.builder()
                    .version(version)
                    .author(author)
                    .content(versionedContent)
                    .rollback(rollback)
                    .verify(verify)
                    .build();
            migrations.add(migration);
        }
        return migrations;
    }

    public static AccountScript parseAccountScript(String filePath, String name, String scriptType, String content) {
        String objectName = extractObjectName(name, content);
        Optional<ScriptObjectType> optionalObjectType = Arrays.stream(ScriptObjectType.values()).filter( type -> type.toString().equalsIgnoreCase(scriptType)).findFirst();
        if(!optionalObjectType.isPresent()) {
            log.error("Unsupported object type: {} found!", scriptType, name);
            throw new RuntimeException("Unknown script type of directory: " + scriptType);
        }
        ScriptObjectType objectType = optionalObjectType.get();
        AccountScript accountScript = ScriptFactory.getAccountScript(filePath, objectType, objectName, content);
        if(objectType.isMigration()) {
            List<Migration> migrations = SqlTokenizer.parseMigrationScripts(content);
            if(migrations.isEmpty()) {
                log.warn("Object type: {} is migration, but there are no migrations found in the file: {}", objectType, filePath);
            }
            List<MigrationScript> migrationScripts = ScriptFactory.getMigrationScripts(accountScript, migrations);
            accountScript.setMigrations(migrationScripts);
        }
        return accountScript;
    }

    public static SchemaScript parseSchemaScript(String filePath, String name, String scriptType, String content) {
        String objectName = SqlTokenizer.extractObjectName(name, content);
        Optional<ScriptObjectType> optionalObjectType = Arrays.stream(ScriptObjectType.values()).filter( type -> type.toString().equalsIgnoreCase(scriptType)).findFirst();
        if(!optionalObjectType.isPresent()) {
            log.error("Unsupported object type: {} found!", scriptType, name);
            throw new RuntimeException("Unknown script type of directory: " + scriptType);
        }
        ScriptObjectType objectType = optionalObjectType.get();
        String fullIdentifier = SqlTokenizer.getFirstFullIdentifier(objectName, content);
        if(fullIdentifier == null || fullIdentifier.isEmpty()) {
            log.error("Error reading script: {}, name and content mismatch", name);
            throw new RuntimeException("Object name and file name must match!");
        }
        String database = SqlTokenizer.extractDatabaseName(fullIdentifier);
        String schema = SqlTokenizer.extractSchemaName(fullIdentifier);
        if(database == null || schema == null) {
            log.error("Error reading script: {}, database or schema not specified", name);
            throw new RuntimeException("Database, schema and object name must be provided in the script file.");
        }
        SchemaScript schemaScript = ScriptFactory.getSchemaScript(filePath, database, schema, objectType, objectName, content);
        if(objectType.isMigration()) {
            List<Migration> migrations = SqlTokenizer.parseMigrationScripts(content);
            if(migrations.isEmpty()) {
                log.warn("Object type: {} is migration, but there are no migrations found in the file: {}", objectType, filePath);
            }
            List<MigrationScript> migrationScripts = ScriptFactory.getMigrationScripts(schemaScript, migrations);
            schemaScript.setMigrations(migrationScripts);
        }
        return schemaScript;
    }

    public static String removeSqlComments(String sql) {
        int index = 0, destinationIndex = 0;
        char[] sqlChar = sql.toCharArray();
        char[] withoutComments = new char[sqlChar.length];
        while(index < sqlChar.length) {
            char token = sqlChar[index];
            switch (token) {
                case '-':
                case '/':
                    if(index < sql.length() - 1 && token == sqlChar[index+1]) {
                        index += 2;
                        while(index < sqlChar.length && sqlChar[index] != '\n') {
                            index++;
                        }
                    }
                    else if (index < sql.length() - 1 && token == '/' && sqlChar[index+1] == '*'){
                        index += 2;
                        while(index < sqlChar.length) {
                            if(index < sqlChar.length - 1 && sqlChar[index] == '*' && sqlChar[index+1] == '/') {
                                index += 2;
                                break;
                            }
                            index++;
                        }
                    }
                    if(index < sqlChar.length)
                        withoutComments[destinationIndex++] = sqlChar[index++];
                    break;
                case '"':
                case '\'':
                    withoutComments[destinationIndex++] = sqlChar[index++];
                    while(index < sqlChar.length) {
                        if(sqlChar[index] == token && sqlChar[index - 1] != '\\') {
                            break;
                        }
                        withoutComments[destinationIndex++] = sqlChar[index++];
                    }
                    if(index < sqlChar.length)
                        withoutComments[destinationIndex++] = sqlChar[index++];
                    break;
                default:
                    withoutComments[destinationIndex++] = sqlChar[index++];
            }
        }
        return new String(withoutComments, 0, destinationIndex);
    }

    public static String removeSqlStringLiteralsManual(String sql) {
        int index = 0, destinationIndex = 0;
        char[] sqlChar = sql.toCharArray();
        char[] withoutLiterals = new char[sqlChar.length];
        boolean isOpened = false;
        while(index < sqlChar.length) {
            char token = sqlChar[index];
            if(isOpened) {
                if(token == '\\') {
                    index+=2;
                    continue;
                }
                else if(token == '\'') {
                    if(index+1 < sqlChar.length && sqlChar[index+1] == '\'') {
                        index+=2;
                        continue;
                    }
                    else {
                        isOpened = false;
                        withoutLiterals[destinationIndex++] = sqlChar[index++];
                    }
                }
                else {
                    index++;
                }
            }
            else {
                if(token == '\'') {
                    int current = index - 1, end = 0;
                    String prevString = sql.substring(0, index);
                    Matcher matcher = Pattern.compile("\\s+(?i)as\\s+$").matcher(prevString);
                    if(!matcher.find()) {
                        isOpened = true;
                    }
                    withoutLiterals[destinationIndex++] = sqlChar[index++];
                }
                else {
                    withoutLiterals[destinationIndex++] = sqlChar[index++];
                }
            }

        }
        return new String(withoutLiterals, 0, destinationIndex);
    }

    public static String removeSqlStringLiterals(String sql) {
        return Pattern.compile(STRING_LITERAL_REGEX, Pattern.CASE_INSENSITIVE).matcher(sql).replaceAll("''");
    }

    public static String getFirstFullIdentifier(String name, String content) {
        content = removeSqlComments(content);
        content = removeSqlStringLiterals(content);
        String regex = TOKEN_START_REGEX +  IDENTIFIER_REGEX + "(\"?" + Pattern.quote(name) + "\"?)" + TOKEN_END_REGEX;
        Matcher matcher = Pattern.compile(regex).matcher(content);
        while(matcher.find()) {
            String fullIdentifier = matcher.group(3);
            String schema = matcher.group(2);
            String db = matcher.group(1);
            if(schema != null) {
                fullIdentifier = schema + fullIdentifier;
            }
            if(db != null) {
                fullIdentifier = db + fullIdentifier;
            }
            return  fullIdentifier;
        }
        return null;
    }

    public static Set<String> getFullIdentifiers(String name, String content) {
        Set<String> fullIdentifiers = new HashSet<>();
        content = removeSqlComments(content);
        content = removeSqlStringLiterals(content);
        String regex = TOKEN_START_REGEX +  IDENTIFIER_REGEX + "(\"?" + Pattern.quote(name) + "\"?)" + TOKEN_END_REGEX;
        Matcher matcher = Pattern.compile(regex).matcher(content);
        while(matcher.find()) {
            String fullIdentifier = name;
            String schema = matcher.group(2);
            String db = matcher.group(1);
            if(schema != null) {
                fullIdentifier = schema + fullIdentifier;
            }
            if(db != null) {
                fullIdentifier = db + fullIdentifier;
            }
            fullIdentifier = fullIdentifier.replace("\"", "");
            fullIdentifiers.add(fullIdentifier);
        }
        return fullIdentifiers;
    }

    public static List<SchemaScript> parseDdlScripts(String ddl, String database, String schema) {
        Matcher matcher = Pattern.compile(DDL_REGEX, Pattern.CASE_INSENSITIVE).matcher(ddl);
        List<SchemaScript> scripts = new ArrayList<>();
        log.debug("parsing ddl scripts {}", ddl);
        while(matcher.find()) {
            String content = matcher.group(1) + ";";
            String type = matcher.group("type");
            if(type == null) {
                log.error("Unable to parse object type from DDL: {}", content);
                throw new RuntimeException("Unable to parse object type from DDL.");
            }
            Optional<ScriptObjectType> optionalObjectType = Arrays.stream(ScriptObjectType.values()).filter( ot -> ot.getSingular().equalsIgnoreCase(type)).findFirst();
            if(!optionalObjectType.isPresent()) {
                log.error("Unsupported object type: {} found in DDL!", type);
                throw new RuntimeException("Unknown object type found in DDL: " + type);
            }
            ScriptObjectType objectType = optionalObjectType.get();

            String fullObjectName = matcher.group("name");
            String scriptObjectName  = fullObjectName.split("\\.")[2];

            if (objectType.isMigration()) {
                Long version = 0L;
                String author = "DlSync";
                String rollback = String.format("DROP %s IF EXISTS %s;", objectType.getSingular(), fullObjectName);
                String verify = String.format("SHOW %s LIKE '%s';", objectType, fullObjectName);

                String migrationHeader = String.format("---version: %s, author: %s\n", version, author);
                String rollbackFormat = String.format("\n---rollback: %s", rollback);
                String verifyFormat = String.format("\n---verify: %s", verify);
                content = migrationHeader + content + rollbackFormat + verifyFormat;
                SchemaScript script = ScriptFactory.getSchemaScript(database, schema, objectType, scriptObjectName, content);
                scripts.add(script);
            } else {
                SchemaScript script = ScriptFactory.getSchemaScript(database, schema, objectType, scriptObjectName, content);
                scripts.add(script);
            }
        }
        return scripts;
    }

//    public static Set<String> getFullToken(String token, String content) {
//        Set<String> fullTokens = new HashSet<>();
//        content = removeSqlComments(content);
//        String regex = TOKEN_START_REGEX + "(?i)" + Pattern.quote(token) + TOKEN_END_REGEX;
//        Matcher matcher = Pattern.compile(regex).matcher(content);
//        while(matcher.find()) {
//            int index = matcher.start();
//            char startChar = content.charAt(index);
//            if(startChar == '.') {
//                int tokenEndIndex = index;
//                String previousToken = SqlTokenizer.getPreviousToken(content, tokenEndIndex);
//                fullTokens.add(previousToken + "." + token);
//            }
//            else if(startChar == '"' && index > 0 && content.charAt(index-1) == '.') {
//                int tokenEndIndex = index - 1;
//                if(index > 1 && content.charAt(index - 2) == '"') {
//                    tokenEndIndex = index - 2;
//                }
//                String previousToken = SqlTokenizer.getPreviousToken(content, tokenEndIndex);
//                fullTokens.add(previousToken + "." + token);
//            }
//            else {
//                fullTokens.add(token);
//            }
//        }
//        Set<String> identifiers = getFullIdentifiers(token, content);
//        if(!identifiers.equals(fullTokens)) {
//            log.error("identifier: {} is different from tokens {}", identifiers, fullTokens);
////            throw new RuntimeException("token mis match");
//            getFullIdentifiers(token, content);
//        }
//        return fullTokens;
//    }
//    public static String getPreviousToken(String content, int endIndex) {
//        int index = endIndex - 1;
//        while(index >= 0) {
//            char ch = content.charAt(index);
//            if(isTokenChar(ch)) {
//                return content.substring(index+1, endIndex);
//            }
//            index --;
//        }
//        return content.substring(0, endIndex);
//    }
//    private static boolean isTokenChar(char ch) {
//        if(Character.isWhitespace(ch)) {
//            return true;
//        }
//        for(char token: TOKENS) {
//            if(ch == token) {
//                return true;
//            }
//        }
//        return false;
//    }

    public static boolean compareScripts(Script script1, Script script2) {
        String content1 = removeSqlComments(script1.getContent());
        String content2 = removeSqlComments(script2.getContent());
        content1 = content1.replace("''", "'");
        content2 = content2.replace("''", "'");
        Pattern pattern;
        if(script1.getObjectType().equals(ScriptObjectType.VIEWS)) {
            pattern = Pattern.compile(VIEW_BODY_REGEX, Pattern.CASE_INSENSITIVE);
        }
        else if(script1.getObjectType().equals(ScriptObjectType.FUNCTIONS)) {
            pattern = Pattern.compile(FUNCTION_BODY_REGEX, Pattern.CASE_INSENSITIVE);
        }
        else if(script1.getObjectType().equals(ScriptObjectType.PROCEDURES)) {
            pattern = Pattern.compile(PROCEDURE_BODY_REGEX, Pattern.CASE_INSENSITIVE);
        }
        else if(script1.getObjectType().equals(ScriptObjectType.FILE_FORMATS)) {
            pattern = Pattern.compile(FILE_FORMAT_BODY_REGEX, Pattern.CASE_INSENSITIVE);
        }
        else {
            pattern = Pattern.compile("(?<body>[\\s\\S]+)$");
        }
        Matcher viewMatcher1 = pattern.matcher(content1);
        Matcher viewMatcher2 = pattern.matcher(content2);
        if(viewMatcher1.find() && viewMatcher2.find()) {
            String query1 = viewMatcher1.group("body");
            String query2 = viewMatcher2.group("body");
            return query1.equals(query2);
        }
        return content1.equals(content2);
    }

    public static String extractObjectName(String fileName, String content) {
        return fileName.replaceAll("(?i)\\.sql$", "").toUpperCase();
    }

    public static String extractDatabaseName(String fullIdentifier) {
        String[] names = fullIdentifier.split("\\.");
        if(names.length < 3) {
            return null;
        }
        return names[0];
    }

    public static String extractSchemaName(String fullIdentifier) {
        String[] names = fullIdentifier.split("\\.");
        if(names.length == 3) {
            return names[1];
        }
        else if(names.length == 2) {
            return names[0];
        }
        return null;
    }

    public static String extractObjectNameFromDdl(String ddlContent) {
        ddlContent = removeSqlComments(ddlContent);
        ddlContent = removeSqlStringLiterals(ddlContent);

        Matcher matcher = Pattern.compile(DDL_REGEX, Pattern.CASE_INSENSITIVE).matcher(ddlContent);
        if (matcher.find()) {
            String fullObjectName = matcher.group("name");
            if (fullObjectName != null && !fullObjectName.isEmpty()) {
                // Extract just the object name (last part after final dot)
                String[] parts = fullObjectName.split("\\.");
                String objectName = parts[parts.length - 1];
                // Remove quotes if present
                objectName = objectName.replace("\"", "");
                return objectName.toUpperCase();
            }
        }

        log.warn("Could not extract object name from DDL content");
        return null;
    }
}
