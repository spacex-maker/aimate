package com.openforge.aimate.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.agent.dto.SessionResponse;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentPromptVersion;
import com.openforge.aimate.domain.AgentSession;
import com.openforge.aimate.domain.LocalBridgeDevice;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.localbridge.LocalBridgeService;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentPromptVersionRepository;
import com.openforge.aimate.repository.AgentSessionRepository;
import com.openforge.aimate.repository.LocalBridgeDeviceRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.storage.ChatCosStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserAgentService {

    private final UserAgentRepository userAgentRepository;
    private final AgentCatalogRepository agentCatalogRepository;
    private final AgentPromptVersionRepository promptVersionRepository;
    private final AgentPromptService agentPromptService;
    private final ChatCosStorageService chatCosStorageService;
    private final LocalBridgeDeviceRepository localBridgeDeviceRepository;
    private final AgentSessionRepository agentSessionRepository;
    private final UserAgentIdentityService identityService;
    private final ObjectMapper objectMapper;

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
        // 真名 / 性别：招聘时写入后原则上不可改；若当时未选定（空 / UNSPECIFIED）允许补全一次
        if (body.name() != null) {
            String nick = trim(body.name(), 128);
            if (StringUtils.hasText(nick) && !nick.equals(ua.getName())) {
                if (!identityIncomplete(ua)) {
                    throw new IllegalArgumentException("伙伴的名字在招聘时已写入 Soul，不可更改");
                }
                ua.setName(nick);
                if (!StringUtils.hasText(ua.getOriginalName())) {
                    ua.setOriginalName(nick);
                }
            }
        }
        if (body.gender() != null) {
            String g = trim(body.gender(), 32);
            if (StringUtils.hasText(g)) {
                String normalized = normalizeGenderLoose(g);
                boolean canSet = isUnspecifiedGender(ua.getGender())
                        || !StringUtils.hasText(ua.getGender())
                        || identityIncomplete(ua)
                        || normalized.equalsIgnoreCase(ua.getGender());
                if (!canSet) {
                    throw new IllegalArgumentException("伙伴的性别在招聘时已选定，不可更改");
                }
                ua.setGender(normalized);
            }
        }
        if (body.avatarUrl() != null) ua.setAvatarUrl(trim(body.avatarUrl(), 512));
        if (body.personality() != null) ua.setPersonality(trim(body.personality(), 512));
        if (body.dailyPointsLimit() != null) {
            long lim = body.dailyPointsLimit();
            ua.setDailyPointsLimit(lim <= 0 ? null : lim);
        }
        if (body.localAssistDm() != null) {
            ua.setLocalAssistDm(body.localAssistDm());
        }
        if (body.localAssistGroup() != null) {
            ua.setLocalAssistGroup(body.localAssistGroup());
        }
        boolean toolDefaultsChanged = false;
        if (body.memoryEnabled() != null) {
            ua.setToolMemoryEnabled(body.memoryEnabled());
            toolDefaultsChanged = true;
        }
        if (body.webSearchEnabled() != null) {
            ua.setToolWebSearchEnabled(body.webSearchEnabled());
            toolDefaultsChanged = true;
        }
        if (body.createToolEnabled() != null) {
            ua.setToolCreateToolEnabled(body.createToolEnabled());
            toolDefaultsChanged = true;
        }
        if (body.scriptExecEnabled() != null) {
            ua.setToolScriptExecEnabled(body.scriptExecEnabled());
            toolDefaultsChanged = true;
        }
        userAgentRepository.save(ua);
        // Agent 默认变更 → 所有单聊取消覆盖，重新跟随
        if (toolDefaultsChanged) {
            agentSessionRepository.clearToolSettingsOverrides(ua.getId());
        }
        return briefView(ua);
    }

    /** 名字可补全：性别未定，或名字仍是岗位商品名（含 · 档位） */
    private static boolean identityIncomplete(UserAgent ua) {
        if (isUnspecifiedGender(ua.getGender()) || !StringUtils.hasText(ua.getGender())) return true;
        String n = ua.getName();
        return n != null && (n.contains("·") || n.endsWith("秘书"));
    }

    private static boolean isUnspecifiedGender(String gender) {
        if (!StringUtils.hasText(gender)) return true;
        String g = gender.trim().toUpperCase(java.util.Locale.ROOT);
        return "UNSPECIFIED".equals(g) || "UNKNOWN".equals(g) || "NONE".equals(g);
    }

    private static String normalizeGenderLoose(String raw) {
        String g = raw.trim().toUpperCase(java.util.Locale.ROOT);
        return switch (g) {
            case "女", "F", "WOMAN", "FEMALE" -> "FEMALE";
            case "男", "M", "MAN", "MALE" -> "MALE";
            case "其他", "OTHER" -> "OTHER";
            default -> g;
        };
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

    /**
     * 打开与该 Agent 的一对一会话（微信式：一人一聊）。
     * 复用最近未归档会话，并隐藏同 Agent 的其它会话；无则新建 IDLE 空会话。
     */
    @Transactional
    public SessionResponse openChat(Long userId, Long userAgentId) {
        UserAgent ua = requireActive(userId, userAgentId);
        List<AgentSession> existing = agentSessionRepository
                .findByUserIdAndUserAgentIdAndHiddenFalseOrderByLastUserMessageAtDescUpdateTimeDesc(
                        userId, ua.getId(), PageRequest.of(0, 50));
        if (!existing.isEmpty()) {
            AgentSession keep = existing.get(0);
            // 一人一会话：其余未归档会话收入归档/隐藏
            for (int i = 1; i < existing.size(); i++) {
                AgentSession other = existing.get(i);
                if (!other.isHidden()) {
                    other.setHidden(true);
                    agentSessionRepository.save(other);
                }
            }
            // 列表标题用伙伴真名（对齐微信联系人）
            if (!ua.getName().equals(keep.getTaskDescription())) {
                keep.setTaskDescription(ua.getName());
                agentSessionRepository.save(keep);
            }
            return toSessionResponse(keep, ua);
        }
        AgentSession session = AgentSession.builder()
                .userId(userId)
                .userAgentId(ua.getId())
                .sessionId(UUID.randomUUID().toString())
                .taskDescription(ua.getName())
                .status(AgentSession.SessionStatus.IDLE)
                .iterationCount(0)
                .build();
        session = agentSessionRepository.save(session);
        return toSessionResponse(session, ua);
    }

    private SessionResponse toSessionResponse(AgentSession session, UserAgent ua) {
        return SessionResponse.from(
                session,
                ua.getName(),
                ua.getAvatarUrl(),
                identityService.careerName(ua),
                identityService.originalName(ua),
                identityService.displayTitle(ua)
        );
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
        String original = identityService.originalName(ua);
        String career = identityService.careerName(ua);
        m.put("originalName", original);
        m.put("careerName", career);
        m.put("displayTitle", identityService.displayTitle(ua));
        m.put("avatarUrl", ua.getAvatarUrl());
        m.put("gender", ua.getGender());
        m.put("genderLabel", genderLabel(ua.getGender()));
        java.time.LocalDate birthday = ua.getBirthday();
        if (birthday == null && ua.getCreateTime() != null) {
            birthday = ua.getCreateTime().toLocalDate();
        }
        m.put("birthday", birthday != null ? birthday.toString() : null);
        if (birthday != null) {
            java.time.Period age = java.time.Period.between(birthday, java.time.LocalDate.now());
            m.put("ageYears", age.getYears());
            m.put("ageMonths", age.getMonths());
            long ageDays = java.time.temporal.ChronoUnit.DAYS.between(birthday, java.time.LocalDate.now());
            m.put("ageDays", ageDays);
        } else {
            m.put("ageYears", null);
            m.put("ageMonths", null);
            m.put("ageDays", null);
        }
        m.put("createTime", ua.getCreateTime() != null ? ua.getCreateTime().toString() : null);
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
        m.put("localAssistDm", ua.getLocalAssistDm() == null || Boolean.TRUE.equals(ua.getLocalAssistDm()));
        m.put("localAssistGroup", ua.getLocalAssistGroup() == null || Boolean.TRUE.equals(ua.getLocalAssistGroup()));
        m.put("memoryEnabled", ua.getToolMemoryEnabled() == null || Boolean.TRUE.equals(ua.getToolMemoryEnabled()));
        m.put("webSearchEnabled", ua.getToolWebSearchEnabled() == null || Boolean.TRUE.equals(ua.getToolWebSearchEnabled()));
        m.put("createToolEnabled", ua.getToolCreateToolEnabled() == null || Boolean.TRUE.equals(ua.getToolCreateToolEnabled()));
        m.put("scriptExecEnabled", ua.getToolScriptExecEnabled() == null || Boolean.TRUE.equals(ua.getToolScriptExecEnabled()));
        m.put("localBridgeConfigured", StringUtils.hasText(ua.getLocalBridgeTokenHash()));
        m.put("localBridgeTokenPrefix", ua.getLocalBridgeTokenPrefix());
        LocalDateTime now = LocalDateTime.now();
        List<LocalBridgeDevice> devices = localBridgeDeviceRepository.findByUserAgentId(ua.getId()).stream()
                .sorted((a, b) -> {
                    if (a.getLastSeenAt() == null && b.getLastSeenAt() == null) return 0;
                    if (a.getLastSeenAt() == null) return 1;
                    if (b.getLastSeenAt() == null) return -1;
                    return b.getLastSeenAt().compareTo(a.getLastSeenAt());
                })
                .toList();
        List<Map<String, Object>> deviceViews = new ArrayList<>();
        int onlineCount = 0;
        for (LocalBridgeDevice d : devices) {
            Map<String, Object> dv = LocalBridgeService.deviceView(d, now);
            if (Boolean.TRUE.equals(dv.get("online"))) onlineCount++;
            deviceViews.add(dv);
        }
        m.put("localBridgeDevices", deviceViews);
        m.put("localBridgeOnlineCount", onlineCount);
        // 仅以设备表心跳为准，避免误用心跳残留 / 错连其它连接器
        m.put("localBridgeOnline", onlineCount > 0);
        LocalDateTime lastSeen = ua.getLocalBridgeLastSeenAt();
        if (!devices.isEmpty() && devices.get(0).getLastSeenAt() != null) {
            lastSeen = devices.get(0).getLastSeenAt();
        }
        m.put("localBridgeLastSeenAt", lastSeen != null ? lastSeen.toString() : null);
        Map<String, Object> primary = !deviceViews.isEmpty()
                ? deviceViews.get(0)
                : parseDeviceJson(ua.getLocalBridgeDeviceJson());
        m.put("localBridgeDevice", primary);
        return m;
    }

    private Map<String, Object> parseDeviceJson(String json) {
        if (!StringUtils.hasText(json)) return Collections.emptyMap();
        try {
            Map<String, Object> raw = objectMapper.readValue(json, new TypeReference<>() {});
            return raw != null ? raw : Collections.emptyMap();
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    /** 首次改昵称前锁定原始名；已有值不覆盖。 */
    private void ensureOriginalName(UserAgent ua) {
        if (StringUtils.hasText(ua.getOriginalName())) return;
        String fromCatalog = catalogName(ua.getCatalogId());
        ua.setOriginalName(StringUtils.hasText(fromCatalog) ? fromCatalog : ua.getName());
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

    private static String genderLabel(String gender) {
        if (gender == null || gender.isBlank()) return null;
        return switch (gender.trim().toUpperCase(java.util.Locale.ROOT)) {
            case "FEMALE", "女", "F" -> "女";
            case "MALE", "男", "M" -> "男";
            case "OTHER", "其他" -> "其他";
            case "UNSPECIFIED", "UNKNOWN", "NONE" -> null;
            default -> null;
        };
    }

    public record PersonaBody(
            String name,
            String avatarUrl,
            String gender,
            String personality,
            Long dailyPointsLimit,
            Boolean localAssistDm,
            Boolean localAssistGroup,
            Boolean memoryEnabled,
            Boolean webSearchEnabled,
            Boolean createToolEnabled,
            Boolean scriptExecEnabled
    ) {}
}
