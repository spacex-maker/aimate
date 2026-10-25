package com.openforge.aimate.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.domain.ChatGroup;
import com.openforge.aimate.domain.GroupChatShare;
import com.openforge.aimate.repository.GroupChatShareRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GroupChatShareService {

    private static final int CODE_LEN = 12;
    private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
    private static final int SNAPSHOT_LIMIT = 200;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final GroupAccessService access;
    private final GroupChatShareRepository shareRepository;
    private final GroupMessageService messageService;
    private final ObjectMapper objectMapper;

    @Transactional
    public Map<String, Object> create(Long userId, String groupPublicId) {
        ChatGroup g = access.requireActiveGroup(groupPublicId);
        access.requireUserMember(g, userId);

        List<Map<String, Object>> messages = messageService.snapshotForShare(g.getId(), SNAPSHOT_LIMIT);
        // 分享页不需要已读回执
        for (Map<String, Object> m : messages) {
            m.remove("reads");
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("groupTitle", g.getTitle());
        payload.put("groupPublicId", g.getPublicId());
        payload.put("snapshotAt", LocalDateTime.now().toString());
        payload.put("messages", messages);

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "快照序列化失败");
        }

        String code = newUniqueCode();
        GroupChatShare row = new GroupChatShare();
        row.setShareCode(code);
        row.setGroupId(g.getId());
        row.setGroupPublicId(g.getPublicId());
        row.setGroupTitle(g.getTitle());
        row.setCreatedByUserId(userId);
        row.setMessageCount(messages.size());
        row.setPayloadJson(json);
        shareRepository.save(row);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("code", code);
        out.put("path", "/s/" + code);
        out.put("messageCount", messages.size());
        return out;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getPublic(String code) {
        if (code == null || !code.matches("^[a-z]{6,32}$")) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "分享不存在");
        }
        GroupChatShare row = shareRepository.findByShareCode(code.trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "分享不存在或已失效"));
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(row.getPayloadJson(), Map.class);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("ok", true);
            out.put("code", row.getShareCode());
            out.put("groupTitle", row.getGroupTitle());
            out.put("groupPublicId", row.getGroupPublicId());
            out.put("messageCount", row.getMessageCount());
            out.put("createTime", row.getCreateTime() != null ? row.getCreateTime().toString() : null);
            out.put("snapshotAt", payload.get("snapshotAt"));
            out.put("messages", payload.get("messages"));
            return out;
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "分享内容损坏");
        }
    }

    private String newUniqueCode() {
        for (int i = 0; i < 20; i++) {
            StringBuilder sb = new StringBuilder(CODE_LEN);
            for (int j = 0; j < CODE_LEN; j++) {
                sb.append(LETTERS.charAt(RANDOM.nextInt(LETTERS.length())));
            }
            String code = sb.toString();
            if (!shareRepository.existsByShareCode(code)) return code;
        }
        throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "无法生成分享码");
    }
}
