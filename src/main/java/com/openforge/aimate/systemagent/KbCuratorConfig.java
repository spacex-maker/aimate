package com.openforge.aimate.systemagent;

import java.util.ArrayList;
import java.util.List;

/**
 * 知识库策展 Agent 的可配置参数（存 system_agents.config_json）。
 */
public class KbCuratorConfig {

    /** 限定分类；空=全部启用知识库 */
    public List<Long> categoryIds = new ArrayList<>();

    /** 每轮最多维护几条 */
    public int maxBasesPerRun = 5;

    /** 正文短于该字数优先策展 */
    public int thinContentChars = 800;

    /** 超过 N 天未更新才策展；0=不按时间过滤（仍会优先旧库） */
    public int staleDays = 7;

    /** 是否启用 Tavily 联网检索 */
    public boolean useTavily = true;

    public int tavilyMaxResults = 5;

    /** basic | advanced */
    public String tavilySearchDepth = "basic";

    /** 每条知识库发起几次检索 */
    public int researchQueriesPerBase = 2;

    public double temperature = 0.35;

    public int maxTokens = 8192;

    /** 额外风格/领域指令，拼进 system prompt */
    public String styleGuide = "";

    /** 是否允许改写 summary / tags */
    public boolean updateSummaryAndTags = true;
}
