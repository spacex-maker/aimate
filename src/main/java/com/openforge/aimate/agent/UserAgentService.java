package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentPromptVersion;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentPromptVersionRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.storage.ChatCosStorageService;
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
    private final AgentCatalogRepository agentCatalogRepository;
    private final AgentPromptVersionRepository promptVersionRepository;
    private final AgentPromptService agentPromptService;
    private final ChatCosStorageService chatCosStorageService;

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
        if (body.name() != null) {
            String nick = trim(body.name(), 128);
            if (!StringUtils.hasText(nick)) {
                throw new IllegalArgumentException("昵称不能为空");
            }
            ensureOriginalName(ua);
            ua.setName(nick);
        }
        if (body.avatarUrl() != null) ua.setAvatarUrl(trim(body.avatarUrl(), 512));
        if (body.gender() != null) ua.setGender(trim(body.gender(), 32));
        if (body.personality() != null) ua.setPersonality(trim(body.personality(), 512));
        if (body.dailyPointsLimit() != null) {
            long lim = body.dailyPointsLimit();
            ua.setDailyPointsLimit(lim <= 0 ? null : lim);
        }
        userAgentRepository.save(ua);
        return briefView(ua);
    }

    /**
     * 上传头像：data URL / https → COS 永久 URL；clear=true 清除自定义头像（前端回退到唯一默认图）。
     */
    @Transactional
    public Map<String, Object> updateAvatar(Long userId, Long id, String imageDataUrl, boolean clear) {
        UserAgent ua = require(userId, id);
        if (clear) {
            ua.setAvatarUrl(null);
            userAgentRepository.save(ua);
            return briefView(ua);
        }
        if (!StringUtils.hasText(imageDataUrl)) {
            throw new IllegalArgumentException("请选择图片");
        }
        List<String> urls = chatCosStorageService.persistUserImages(List.of(imageDataUrl.trim()));
        if (urls.isEmpty()) {
            throw new IllegalStateException("头像上传失败");
        }
        ua.setAvatarUrl(trim(urls.get(0), 512));
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
        String original = resolveOriginalName(ua);
        m.put("originalName", original);
        m.put("avatarUrl", ua.getAvatarUrl());
        m.put("gender", ua.getGender());
        m.put("status", ua.getStatus().name());
        m.put("expiresAt", ua.getExpiresAt() != null ? ua.getExpiresAt().toString() : null);
        m.put("currentPromptVersion", ua.getCurrentPromptVersion());
        m.put("defaultSystemModelId", ua.getDefaultSystemModelId());
        m.put("soulAgentId", ua.getSoulAgentId());
        m.put("soulBound", ua.getSoulAgentId() != null && StringUtils.hasText(ua.getSoulAccessToken()));
        m.put("externalAgentKey", ua.getExternalAgentKey());
        m.put("dailyPointsLimit", ua.getDailyPointsLimit());
        m.put("executionMode", ua.getExecutionMode() != null ? ua.getExecutionMode().name() : "CLOUD_LLM");
        m.put("localBridge", ua.getExecutionMode() == com.openforge.aimate.domain.AgentExecutionMode.LOCAL_BRIDGE);
        m.put("localBridgeConfigured", StringUtils.hasText(ua.getLocalBridgeTokenHash()));
        m.put("localBridgeTokenPrefix", ua.getLocalBridgeTokenPrefix());
        return m;
    }

    /** 首次改昵称前锁定原始名；已有值不覆盖。 */
    private void ensureOriginalName(UserAgent ua) {
        if (StringUtils.hasText(ua.getOriginalName())) return;
        String fromCatalog = catalogName(ua.getCatalogId());
        ua.setOriginalName(StringUtils.hasText(fromCatalog) ? fromCatalog : ua.getName());
    }

    private String resolveOriginalName(UserAgent ua) {
        if (StringUtils.hasText(ua.getOriginalName())) return ua.getOriginalName().trim();
        String fromCatalog = catalogName(ua.getCatalogId());
        if (StringUtils.hasText(fromCatalog)) return fromCatalog;
        return ua.getName();
    }

    private String catalogName(Long catalogId) {
        if (catalogId == null) return null;
        return agentCatalogRepository.findById(catalogId).map(AgentCatalog::getName).orElse(null);
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

    public record PersonaBody(String name, String avatarUrl, String gender, String personality, Long dailyPointsLimit) {}
}
