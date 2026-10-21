package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentPromptVersion;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentPromptVersionRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UserAgentService {

    private final UserAgentRepository userAgentRepository;
    private final AgentPromptVersionRepository promptVersionRepository;
    private final AgentPromptService agentPromptService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listMine(Long userId) {
        refreshExpired(userId);
        return userAgentRepository.findByUserIdOrderByUpdateTimeDesc(userId).stream()
                .map(this::briefView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> detail(Long userId, Long id) {
        UserAgent ua = require(userId, id);
        Map<String, Object> m = briefView(ua);
        m.put("personality", ua.getPersonality());
        m.put("soulKeyId", ua.getSoulKeyId());
        m.put("currentPromptVersion", ua.getCurrentPromptVersion());
        List<Map<String, Object>> versions = promptVersionRepository
                .findByUserAgentIdOrderByVersionNoDesc(ua.getId())
                .stream()
                .map(this::versionView)
                .toList();
        m.put("promptVersions", versions);
        return m;
    }

    @Transactional
    public Map<String, Object> updatePersona(Long userId, Long id, PersonaBody body) {
        UserAgent ua = require(userId, id);
        if (body.name() != null) ua.setName(trim(body.name(), 128));
        if (body.avatarUrl() != null) ua.setAvatarUrl(trim(body.avatarUrl(), 512));
        if (body.gender() != null) ua.setGender(trim(body.gender(), 32));
        if (body.personality() != null) ua.setPersonality(trim(body.personality(), 512));
        userAgentRepository.save(ua);
        return briefView(ua);
    }

    @Transactional
    public Map<String, Object> excludePromptAtoms(Long userId, Long id, List<String> atomIds) {
        AgentPromptVersion ver = agentPromptService.excludeAtoms(userId, id, atomIds);
        return versionView(ver);
    }

    @Transactional(readOnly = true)
    public UserAgent requireActive(Long userId, Long userAgentId) {
        UserAgent ua = require(userId, userAgentId);
        refreshOne(ua);
        if (ua.getStatus() != UserAgent.Status.ACTIVE) {
            throw new IllegalStateException("该 Agent 不可用（已过期或未激活）");
        }
        return ua;
    }

    @Transactional(readOnly = true)
    public UserAgent requireSecretaryOrDefault(Long userId, Long userAgentId) {
        if (userAgentId != null) {
            return requireActive(userId, userAgentId);
        }
        return userAgentRepository.findByUserIdAndKind(userId, UserAgent.Kind.SECRETARY)
                .map(ua -> {
                    refreshOne(ua);
                    return ua;
                })
                .filter(ua -> ua.getStatus() == UserAgent.Status.ACTIVE)
                .orElseThrow(() -> new IllegalStateException("请先完善个人画像以生成秘书 Agent"));
    }

    private void refreshExpired(Long userId) {
        for (UserAgent ua : userAgentRepository.findByUserIdOrderByUpdateTimeDesc(userId)) {
            refreshOne(ua);
        }
    }

    private void refreshOne(UserAgent ua) {
        if (ua.getKind() == UserAgent.Kind.SECRETARY) return;
        if (ua.getExpiresAt() != null && ua.getExpiresAt().isBefore(LocalDateTime.now())
                && ua.getStatus() == UserAgent.Status.ACTIVE) {
            ua.setStatus(UserAgent.Status.EXPIRED);
            userAgentRepository.save(ua);
        }
    }

    private UserAgent require(Long userId, Long id) {
        return userAgentRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
    }

    private Map<String, Object> briefView(UserAgent ua) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", ua.getId());
        m.put("kind", ua.getKind().name());
        m.put("catalogId", ua.getCatalogId());
        m.put("tier", ua.getTier() != null ? ua.getTier().name() : null);
        m.put("name", ua.getName());
        m.put("avatarUrl", ua.getAvatarUrl());
        m.put("gender", ua.getGender());
        m.put("status", ua.getStatus().name());
        m.put("expiresAt", ua.getExpiresAt() != null ? ua.getExpiresAt().toString() : null);
        m.put("currentPromptVersion", ua.getCurrentPromptVersion());
        m.put("defaultSystemModelId", ua.getDefaultSystemModelId());
        return m;
    }

    private Map<String, Object> versionView(AgentPromptVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("versionNo", v.getVersionNo());
        m.put("changeSummary", v.getChangeSummary());
        m.put("atomsJson", v.getAtomsJson());
        m.put("active", v.isActive());
        m.put("promptText", v.getPromptText());
        m.put("createTime", v.getCreateTime() != null ? v.getCreateTime().toString() : null);
        return m;
    }

    private static String trim(String s, int max) {
        if (!StringUtils.hasText(s)) return s;
        String t = s.trim();
        return t.length() > max ? t.substring(0, max) : t;
    }

    public record PersonaBody(String name, String avatarUrl, String gender, String personality) {}
}
