package com.openforge.aimate.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.AssistantMessageVersion;
import com.openforge.aimate.domain.SessionMessage;
import com.openforge.aimate.llm.model.Message;
import com.openforge.aimate.llm.model.ToolCall;
import com.openforge.aimate.repository.AssistantMessageVersionRepository;
import com.openforge.aimate.repository.SessionMessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.openforge.aimate.agent.dto.ChatMessageDto;
import com.openforge.aimate.agent.dto.ToolCallDisplayDto;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话内消息表读写：将 {@link Message} 与 {@link SessionMessage} 互转，支持按会话加载、追加与全量替换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SessionMessageService {

    private static final TypeReference<List<ToolCall>> TOOL_CALLS_TYPE = new TypeReference<>() {};

    private final SessionMessageRepository messageRepository;
    private final AssistantMessageVersionRepository versionRepository;
    private final ObjectMapper objectMapper;

    /**
     * 按会话顺序加载所有消息，转为 {@link Message} 列表。
     */
    @Transactional(readOnly = true)
    public List<Message> load(AgentSession session) {
        List<SessionMessage> rows = messageRepository.findByAgentSession_IdOrderBySeqAsc(session.getId());
        List<Message> out = new ArrayList<>(rows.size());
        for (SessionMessage row : rows) {
            out.add(toMessage(row));
        }
        return out;
    }

    /**
     * 加载上下文截止到某 seq（仅主序消息），用于重试时只取该条 user 之前的上下文。
     */
    @Transactional(readOnly = true)
    public List<Message> loadContextUpToSeq(AgentSession session, int maxSeq) {
        List<SessionMessage> rows = messageRepository.findContextUpToSeq(session.getId(), maxSeq);
        List<Message> out = new ArrayList<>(rows.size());
        for (SessionMessage row : rows) {
            out.add(toMessage(row));
        }
        return out;
    }

    /**
     * 加载「某条回复」的上下文：主序消息 seq≤占位条 seq，或归属该占位条的消息（工具等），按 seq 升序。
     * 用于单条消息独立 run、多轮并行。
     */
    @Transactional(readOnly = true)
    public List<Message> loadContextForReply(AgentSession session, SessionMessage placeholder) {
        List<SessionMessage> rows = messageRepository.findContextForReply(
                session.getId(), placeholder.getSeq(), placeholder.getId());
        List<Message> out = new ArrayList<>(rows.size());
        for (SessionMessage row : rows) {
            out.add(toMessage(row));
        }
        return out;
    }

    /**
     * 向某条回复的上下文中追加消息（工具结果等），写入消息表并标记 reply_to_message_id。
     */
    @Transactional
    public void appendToRun(AgentSession session, long placeholderId, Message... messages) {
        int next = nextSeq(session.getId());
        for (Message m : messages) {
            SessionMessage entity = toEntity(session, next++, m, placeholderId);
            messageRepository.save(entity);
        }
    }

    /**
     * 按会话顺序加载消息，转为前端 DTO（含 messageStatus，用于展示「回答中」等状态）。
     * 仅返回 user 与「主」assistant（reply_to_message_id 为 null 的占位条），不返回 tool，
     * 也不返回多轮 tool call 时追加的仅含 tool_calls、content 为空的 assistant，避免最后出现「(无内容)」。
     */
    @Transactional(readOnly = true)
    public List<ChatMessageDto> loadDtos(AgentSession session) {
        List<SessionMessage> rows = messageRepository.findByAgentSession_IdOrderBySeqAsc(session.getId());
        return rows.stream()
                .filter(r -> "user".equals(r.getRole())
                        || ("assistant".equals(r.getRole()) && r.getReplyToMessageId() == null))
                .map(r -> {
                    List<ToolCallDisplayDto> toolCalls = "assistant".equals(r.getRole())
                            ? loadToolCallsForAssistant(r.getId()) : null;
                    String createTimeStr = r.getCreateTime() != null
                            ? r.getCreateTime().toString()
                            : null;
                    return new ChatMessageDto(
                            r.getId(),
                            r.getRole(),
                            r.getContent() != null ? r.getContent() : "",
                            r.getMessageStatus(),
                            "assistant".equals(r.getRole()) ? r.getThinkingContent() : null,
                            toolCalls,
                            createTimeStr
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * 加载某条主 assistant 回复过程中的工具调用列表（从 reply_to_message_id 指向它的附属消息中解析）。
     */
    @Transactional(readOnly = true)
    public List<ToolCallDisplayDto> loadToolCallsForAssistant(long mainAssistantMessageId) {
        List<SessionMessage> chain = messageRepository.findByReplyToMessageIdOrderBySeqAsc(mainAssistantMessageId);
        if (chain.isEmpty()) return List.of();
        List<ToolCallDisplayDto> out = new ArrayList<>();
        for (SessionMessage m : chain) {
            if ("assistant".equals(m.getRole()) && m.getToolCallsJson() != null && !m.getToolCallsJson().isBlank()) {
                try {
                    List<ToolCall> calls = objectMapper.readValue(m.getToolCallsJson(), TOOL_CALLS_TYPE);
                    if (calls != null) {
                        for (ToolCall tc : calls) {
                            out.add(new ToolCallDisplayDto(
                                    tc.function() != null ? tc.function().name() : "",
                                    tc.function() != null && tc.function().arguments() != null ? tc.function().arguments() : "",
                                    null));
                        }
                    }
                } catch (JsonProcessingException e) {
                    log.warn("[SessionMessage] Failed to deserialize tool_calls for reply chain id {}: {}", m.getId(), e.getMessage());
                }
            } else if ("tool".equals(m.getRole())) {
                String result = m.getContent() != null ? m.getContent() : "";
                for (int i = 0; i < out.size(); i++) {
                    if (out.get(i).result() == null) {
                        out.set(i, new ToolCallDisplayDto(out.get(i).name(), out.get(i).arguments(), result));
                        break;
                    }
                }
            }
        }
        return out;
    }

    /**
     * 用给定消息列表全量替换该会话在消息表中的记录（先删后插）。
     */
    @Transactional
    public void replaceAll(AgentSession session, List<Message> messages) {
        messageRepository.deleteByAgentSessionId(session.getId());
        if (messages.isEmpty()) return;
        for (int i = 0; i < messages.size(); i++) {
            SessionMessage entity = toEntity(session, i, messages.get(i));
            messageRepository.save(entity);
        }
        log.debug("[SessionMessage] Replaced {} messages for session {}", messages.size(), session.getSessionId());
    }

    /**
     * 删除某个会话下的所有消息记录。
     */
    @Transactional
    public void deleteAllForSession(AgentSession session) {
        messageRepository.deleteByAgentSessionId(session.getId());
        log.debug("[SessionMessage] Deleted all messages for session {}", session.getSessionId());
    }

    /**
     * 在会话末尾追加若干条消息。
     */
    @Transactional
    public void append(AgentSession session, Message... messages) {
        int nextSeq = nextSeq(session.getId());
        for (int i = 0; i < messages.length; i++) {
            SessionMessage entity = toEntity(session, nextSeq + i, messages[i]);
            messageRepository.save(entity);
        }
        log.debug("[SessionMessage] Appended {} messages for session {}", messages.length, session.getSessionId());
    }

    /**
     * 创建一条「回答中」的 assistant 占位记录，返回其 id 供后续更新/中断。
     */
    @Transactional
    public SessionMessage createPlaceholderAssistant(AgentSession session) {
        int seq = nextSeq(session.getId());
        SessionMessage placeholder = SessionMessage.builder()
                .agentSession(session)
                .seq(seq)
                .role("assistant")
                .content(null)
                .toolCallId(null)
                .toolCallsJson(null)
                .messageStatus(SessionMessage.STATUS_ANSWERING)
                .build();
        placeholder = messageRepository.save(placeholder);
        log.debug("[SessionMessage] Created placeholder assistant seq={} id={} for session {}", seq, placeholder.getId(), session.getSessionId());
        return placeholder;
    }

    /**
     * 更新占位 assistant 消息的内容与状态（已完成 / 已中断）。
     */
    @Transactional
    public void updateAssistantMessage(Long messageId, String content, String status) {
        if (messageId == null) return;
        messageRepository.findById(messageId).ifPresent(msg -> {
            msg.setContent(content);
            msg.setMessageStatus(status);
            messageRepository.save(msg);
        });
    }

    /**
     * 更新某条 assistant 消息的思考过程（思考流结束后写入）。
     */
    @Transactional
    public void updateAssistantThinking(Long messageId, String thinkingContent) {
        if (messageId == null) return;
        messageRepository.findById(messageId).ifPresent(msg -> {
            msg.setThinkingContent(thinkingContent != null && !thinkingContent.isBlank() ? thinkingContent : null);
            messageRepository.save(msg);
        });
    }

    /**
     * 重试后保存新版本：写入 versions 表并更新 session_message.content（最新版本给上下文用）。
     * @param thinkingContent 思考过程，可选，写入该条 assistant 的 thinking_content
     */
    @Transactional
    public void saveNewVersion(long assistantMessageId, String content, String thinkingContent) {
        SessionMessage msg = messageRepository.findById(assistantMessageId).orElse(null);
        if (msg == null) return;
        int nextVersion = versionRepository.findByAgentSessionMessage_IdOrderByVersionDesc(assistantMessageId)
                .stream()
                .mapToInt(AssistantMessageVersion::getVersion)
                .max()
                .orElse(0) + 1;
        String contentToStore = (content != null && !content.isBlank()) ? content : "(无文字回复)";
        AssistantMessageVersion v = AssistantMessageVersion.builder()
                .agentSessionMessage(msg)
                .version(nextVersion)
                .content(contentToStore)
                .build();
        versionRepository.save(v);
        msg.setContent(contentToStore);
        msg.setMessageStatus(SessionMessage.STATUS_DONE);
        if (thinkingContent != null && !thinkingContent.isBlank()) msg.setThinkingContent(thinkingContent);
        messageRepository.save(msg);
        log.debug("[SessionMessage] Saved version {} for assistant message {}", nextVersion, assistantMessageId);
    }

    /** 兼容：无思考内容时调用。 */
    @Transactional
    public void saveNewVersion(long assistantMessageId, String content) {
        saveNewVersion(assistantMessageId, content, null);
    }

    /** 某条 assistant 回复的历史版本列表（版本号降序，供前端查看）。 */
    @Transactional(readOnly = true)
    public List<AssistantMessageVersion> getVersions(long assistantMessageId) {
        return versionRepository.findByAgentSessionMessage_IdOrderByVersionDesc(assistantMessageId);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<SessionMessage> findById(long id) {
        return messageRepository.findById(id);
    }

    @Transactional(readOnly = true)
    public java.util.Optional<SessionMessage> findBySessionAndSeq(long agentSessionId, int seq) {
        return messageRepository.findByAgentSession_IdAndSeq(agentSessionId, seq);
    }

    /** 会话内是否还有 ANSWERING 的回复（用于决定是否置会话为 IDLE）。 */
    @Transactional(readOnly = true)
    public boolean hasAnyAnswering(long agentSessionId) {
        return messageRepository.countAnswering(agentSessionId) > 0;
    }

    private int nextSeq(Long agentSessionId) {
        List<SessionMessage> existing = messageRepository.findByAgentSession_IdOrderBySeqAsc(agentSessionId);
        return existing.isEmpty() ? 0 : existing.get(existing.size() - 1).getSeq() + 1;
    }

    private SessionMessage toEntity(AgentSession session, int seq, Message m) {
        return toEntity(session, seq, m, null);
    }

    private SessionMessage toEntity(AgentSession session, int seq, Message m, Long replyToMessageId) {
        String toolCallsJson = null;
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            try {
                toolCallsJson = objectMapper.writeValueAsString(m.toolCalls());
            } catch (JsonProcessingException e) {
                log.warn("[SessionMessage] Failed to serialize tool_calls for seq {}: {}", seq, e.getMessage());
            }
        }
        SessionMessage e = SessionMessage.builder()
                .agentSession(session)
                .seq(seq)
                .role(m.role())
                .content(m.content())
                .toolCallId(m.toolCallId())
                .toolCallsJson(toolCallsJson)
                .build();
        if (replyToMessageId != null) e.setReplyToMessageId(replyToMessageId);
        return e;
    }

    private Message toMessage(SessionMessage row) {
        List<ToolCall> toolCalls = null;
        if (row.getToolCallsJson() != null && !row.getToolCallsJson().isBlank()) {
            try {
                toolCalls = objectMapper.readValue(row.getToolCallsJson(), TOOL_CALLS_TYPE);
            } catch (JsonProcessingException e) {
                log.warn("[SessionMessage] Failed to deserialize tool_calls for id {}: {}", row.getId(), e.getMessage());
            }
        }
        String content = row.getContent();
        if ("assistant".equals(row.getRole())
                && (content == null || content.isBlank())
                && (toolCalls == null || toolCalls.isEmpty())) {
            content = "(无内容)";
        }
        String reasoningContent = "assistant".equals(row.getRole())
                ? (row.getThinkingContent() != null ? row.getThinkingContent() : "")
                : null;
        return Message.builder()
                .role(row.getRole())
                .content(content)
                .toolCalls(toolCalls)
                .toolCallId(row.getToolCallId())
                .reasoningContent(reasoningContent)
                .build();
    }
}
