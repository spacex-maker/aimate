package com.openforge.aimate.group;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.SystemModelChatHelper;
import com.openforge.aimate.agent.UserToolSettingsService;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.SystemModel;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.llm.LlmClient;
import com.openforge.aimate.llm.LlmProperties;
import com.openforge.aimate.llm.LlmRouter;
import com.openforge.aimate.llm.model.ChatRequest;
import com.openforge.aimate.llm.model.ChatResponse;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.Tool;
import com.openforge.aimate.llm.model.ToolCall;
import com.openforge.aimate.llm.model.ToolFunction;
import com.openforge.aimate.memory.GroupMemoryCaptureService;
import com.openforge.aimate.memory.MemoryRecord;
import com.openforge.aimate.memory.SoulMemoryBridge;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 群聊云端回复可用的系统工具：查群资料 / 查成员 / 长期记忆读写。
 * 成员会变动，Agent 应在需要时主动调用，而不是只靠进群那一次快照。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GroupAgentToolService {

    private static final int MAX_TOOL_ROUNDS = 3;

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final SystemModelChatHelper systemModelChatHelper;
    private final LlmRouter llmRouter;
    private final GroupMemoryCaptureService groupMemoryCaptureService;
    private final SoulMemoryBridge soulMemoryBridge;
    private final UserToolSettingsService userToolSettingsService;

    /**
     * 带工具循环的群回复生成；结束后返回最终正文。
     */
    public String chatWithGroupTools(
            SystemModel modelOrNull,
            List<Message> seedMessages,
            ChatGroup group,
            UserAgent ua
    ) {
        List<Message> msgs = new ArrayList<>(seedMessages);
        List<Tool> tools = buildTools(ua);
        for (int round = 0; round < MAX_TOOL_ROUNDS; round++) {
            ChatResponse resp = callModel(modelOrNull, msgs, tools, true);
            Message msg = resp.firstMessage();
            if (msg == null) return "";
            if (!resp.hasToolCalls()) {
                return msg.content() != null ? msg.content().trim() : "";
            }
            msgs.add(Message.assistantToolCalls(msg.toolCalls()));
            for (ToolCall tc : msg.toolCalls()) {
                String result = execute(tc, group, ua);
                msgs.add(Message.toolResult(tc.id(), result));
            }
        }
        // 收尾：强制纯文本
        ChatResponse finalResp = callModel(modelOrNull, msgs, null, true);
        Message last = finalResp.firstMessage();
        return last != null && last.content() != null ? last.content().trim() : "";
    }

    public List<Tool> buildTools(UserAgent ua) {
        List<Tool> out = new ArrayList<>();
        out.add(tool(
                "get_group_info",
                "查看当前群的基本信息（群名、公告、群主、成员人数等）。花名册可能已变，不确定时先调这个。",
                """
                {"type":"object","properties":{}}
                """
        ));
        out.add(tool(
                "list_group_members",
                "列出当前群成员的最新基本信息（人类与 Agent）。可选 keyword 按名字过滤。成员变动后请重新调用，不要死记旧名单。",
                """
                {"type":"object","properties":{
                  "keyword":{"type":"string","description":"可选，按显示名/真名/职业模糊过滤"}
                }}
                """
        ));
        out.add(tool(
                "get_group_member",
                "查看当前群里某一位成员的详细资料。name 填对方在群里的名字或昵称。",
                """
                {"type":"object","properties":{
                  "name":{"type":"string","description":"成员显示名、昵称或 Agent 名"}
                },"required":["name"]}
                """
        ));
        if (userToolSettingsService.isMemoryEnabled(ua.getUserId(), ua.getId())) {
            out.add(tool(
                    "recall_memory",
                    "从你自己的长期记忆（含单聊与以往群聊）检索相关事实。问群里旧事、某人是谁、以前约过什么时先用。",
                    """
                    {"type":"object","properties":{
                      "query":{"type":"string"},
                      "top_k":{"type":"integer","minimum":1,"maximum":12}
                    },"required":["query"]}
                    """
            ));
            out.add(tool(
                    "store_memory",
                    "把群里值得长期记住的事写入你的记忆（决定、约定、新成员印象、重要偏好）。写成第三人称清晰短句；一次性闲聊不要存。",
                    """
                    {"type":"object","properties":{
                      "content":{"type":"string"},
                      "importance":{"type":"number","minimum":0,"maximum":1}
                    },"required":["content"]}
                    """
            ));
        }
        return out;
    }

    public String execute(ToolCall toolCall, ChatGroup group, UserAgent ua) {
        String name = toolCall.function() != null ? toolCall.function().name() : null;
        if (!StringUtils.hasText(name)) return "[ToolError] missing tool name";
        JsonNode args;
        try {
            String raw = toolCall.function().arguments();
            args = objectMapper.readTree(StringUtils.hasText(raw) ? raw : "{}");
        } catch (Exception e) {
            args = objectMapper.createObjectNode();
        }
        try {
            return switch (name) {
                case "get_group_info" -> toJson(groupMemoryCaptureService.groupInfoMap(group.getId()));
                case "list_group_members" -> {
                    String kw = text(args, "keyword");
                    yield toJson(Map.of(
                            "group", group.getTitle() != null ? group.getTitle() : group.getPublicId(),
                            "members", groupMemoryCaptureService.memberInfoList(group.getId(), kw)
                    ));
                }
                case "get_group_member" -> {
                    String who = text(args, "name");
                    if (!StringUtils.hasText(who)) {
                        yield "[ToolError] name is required";
                    }
                    Map<String, Object> one = groupMemoryCaptureService.findMemberInfo(group.getId(), who);
                    yield one.isEmpty()
                            ? "未在当前群找到名为「" + who + "」的成员，请用 list_group_members 核对。"
                            : toJson(one);
                }
                case "recall_memory" -> {
                    String q = text(args, "query");
                    int topK = args.path("top_k").asInt(8);
                    if (!StringUtils.hasText(q)) {
                        yield "[ToolError] query is required";
                    }
                    List<MemoryRecord> hits = soulMemoryBridge.recall(
                            q, Math.max(1, Math.min(12, topK)), ua.getUserId(), ua.getId());
                    if (hits.isEmpty()) yield "（没有召回到相关记忆）";
                    StringBuilder sb = new StringBuilder();
                    for (MemoryRecord r : hits) {
                        sb.append("- ").append(r.content()).append('\n');
                    }
                    yield sb.toString().trim();
                }
                case "store_memory" -> {
                    String content = text(args, "content");
                    if (!StringUtils.hasText(content)) {
                        yield "[ToolError] content is required";
                    }
                    float imp = (float) args.path("importance").asDouble(0.7);
                    String project = "group:" + group.getPublicId();
                    soulMemoryBridge.store(
                            content.trim(), "FACT", Math.max(0.4f, Math.min(1f, imp)),
                            ua.getUserId(), ua.getId(), project);
                    yield "已记住。";
                }
                default -> "[ToolError] unknown tool: " + name;
            };
        } catch (Exception e) {
            log.warn("[GroupTools] {} failed: {}", name, e.getMessage());
            return "[ToolError] " + e.getMessage();
        }
    }

    private ChatResponse callModel(
            SystemModel modelOrNull,
            List<Message> messages,
            List<Tool> tools,
            boolean disableThinking
    ) {
        if (modelOrNull != null) {
            LlmProperties.ProviderConfig config = systemModelChatHelper.buildConfig(modelOrNull);
            LlmClient client = new LlmClient(httpClient, objectMapper, config);
            ChatRequest.ChatRequestBuilder b = ChatRequest.builder()
                    .model(config.model())
                    .messages(messages)
                    .temperature(0.7)
                    .maxTokens(tools != null && !tools.isEmpty() ? 2048 : 1024);
            if (tools != null && !tools.isEmpty()) {
                b.tools(tools).toolChoice("auto");
            }
            if (disableThinking) {
                b.thinking(ChatRequest.Thinking.disabled());
            }
            return client.chat(b.build());
        }
        ChatRequest.ChatRequestBuilder b = ChatRequest.builder()
                .messages(messages)
                .temperature(0.7)
                .maxTokens(tools != null && !tools.isEmpty() ? 2048 : 1024);
        if (tools != null && !tools.isEmpty()) {
            b.tools(tools).toolChoice("auto");
        }
        if (disableThinking) {
            b.thinking(ChatRequest.Thinking.disabled());
        }
        return llmRouter.chat(b.build());
    }

    private Tool tool(String name, String description, String schemaJson) {
        try {
            return Tool.ofFunction(new ToolFunction(name, description, objectMapper.readTree(schemaJson)));
        } catch (Exception e) {
            throw new IllegalStateException("bad tool schema: " + name, e);
        }
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(o);
        } catch (Exception e) {
            return String.valueOf(o);
        }
    }

    private static String text(JsonNode args, String field) {
        if (args == null || !args.has(field) || args.get(field).isNull()) return null;
        String s = args.get(field).asText(null);
        return StringUtils.hasText(s) ? s.trim() : null;
    }
}
