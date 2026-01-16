package com.snowflake.dlsync.dependency;

import com.snowflake.dlsync.models.MigrationScript;
import com.snowflake.dlsync.models.Script;
import com.snowflake.dlsync.parser.SqlTokenizer;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class DependencyExtractor {
    private static final String[] VIEW_DEPENDENCY_REGEX = {"\\s(?i)FROM\\s+([^\\(\\s]+?)(\\s+(as\\s+)?\\w\\s*,|[\\s;\\)]|$)", "\\s(?i)JOIN\\s+([^\\(\\s]+?)(?:[\\s;\\),]|$)","TOKEN\\s*:\\s*(?:/\\*.*\\*/*)\\n\\{(?:[^\\}\\{]+|\\{(?:[^\\}\\{]+|\\{[^\\}\\{]*\\})*\\})*\\}"};
    private static final String CROSS_JOIN_DEPENDENCY = "";
    private static final String FUNCTION_WITH_DEPENDENCY_REGEX = "(?i)LANGUAGE\\s+(?i)SQL\\s+";
    private static final String[] VIEW_FALSE_POSITIVE_DEPENDENCY_REGEX = {"(?i)WITH\\s+(\\w+)\\s+(?i)as\\s+\\(", "\\)\\s*,\\s*(\\w+)\\s+(?i)as\\s*\\("};
    private static final String[] COMMENT_REGEX = {"(?:'[^']*')|--.*$|\\/\\*[\\s\\S]*?\\*\\/|(?i)comment\\s*=\\s*'[^']*'\\s*(?i)(?=as)", ""};
//            {"--.*?\\n|\\/\\/.*?\\n", "\\/\\*[\\s\\S]*?\\*\\/", "\\'([^\\']*)\\'"};
//            {"((--)|(\\/\\/)).*[\\r\\n]+", "\\/\\*([^*]|[\\r\\n]|(\\*+([^*\\/]|[\\r\\n])))*\\*+\\/"};

    private static final String DEPENDENCY_START_REGEX = "([()\\[\\],\\.\\s\\\"])";
    private static final String DEPENDENCY_END_REGEX = "([()\\[\\],\\.\\s\\'\\\";])";
    private List<Script> scripts = new ArrayList<>();
    public DependencyExtractor() {
        log.debug("Dependency extractor started.");
    }


    public void addScripts(List<? extends Script> scripts) {
        this.scripts.addAll(scripts);
    }

    public Set<Script> extractScriptDependencies(Script script) {
        Set<Script> dependencies = new HashSet<>();
        dependencies = scripts.parallelStream()
                .filter(s -> !s.getFullObjectName().equals(script.getFullObjectName()))
                .filter(s -> isDependencyOf(s, script))
                .collect(Collectors.toSet());
        if(script instanceof MigrationScript) {
            MigrationScript migrationScript = (MigrationScript)script;
            Set<Script> versionDependencies  = scripts.stream()
                    .filter(s -> s.getFullObjectName().equals(script.getFullObjectName()) && s.getObjectType().equals(script.getObjectType()))
                    .map(s -> (MigrationScript) s)
                    .filter(s -> s.getVersion() <  migrationScript.getVersion()).collect(Collectors.toSet());
            dependencies.addAll(versionDependencies);

        }
        log.debug("For the object {} found the following dependencies: {}", script.getId(), dependencies);
        return dependencies;
    }

    private boolean isDependencyOf(Script dependency, Script target) {
        if(target.getObjectName().equals("USER_ROLE") && dependency.getObjectName().equals("{EXAMPLE_DB}.${MAIN_SCHEMA}")) {
            log.debug("User-role dependency found: {}", dependency);
        }
        if(dependency.getObjectName().equals(target.getObjectName())) {
            log.debug("Found same object name with different schema: {}, {}", dependency, target);
        }
        Set<String> fullIdentifiers = SqlTokenizer.getFullIdentifiers(dependency.getObjectName(), target.getContent());
        if(fullIdentifiers.isEmpty()) {
            return false;
        }
        for(String identifier: fullIdentifiers) {
            String fullObjectName = target.resolveObjectReference(identifier);
            if (fullObjectName.equals(dependency.getFullObjectName())) {
                return true;
            }
        }
        return false;
    }

}
