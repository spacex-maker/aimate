package com.openforge.aimate.agent;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Script execution: optional interpreter paths (python-path, node-path, shell-path).
 * Docker 配置见 ScriptDockerProperties (agent.script.docker.*)。
 */
@ConfigurationProperties(prefix = "agent.script")
public record ScriptToolProperties(
        @DefaultValue("") String pythonPath,
        @DefaultValue("") String nodePath,
        @DefaultValue("") String shellPath
) {
    public String getPythonPathOrNull() { return blankToNull(pythonPath); }
    public String getNodePathOrNull()  { return blankToNull(nodePath); }
    public String getShellPathOrNull() { return blankToNull(shellPath); }
    private static String blankToNull(String s) { return s != null && !s.isBlank() ? s.trim() : null; }
}
