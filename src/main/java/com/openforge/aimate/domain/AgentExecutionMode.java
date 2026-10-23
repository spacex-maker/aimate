package com.openforge.aimate.domain;

/**
 * Agent 执行方式：云端 LLM 直出，或由本机 Cursor/Codex 等通过 Local Bridge 领取任务。
 */
public enum AgentExecutionMode {
    CLOUD_LLM,
    LOCAL_BRIDGE
}
