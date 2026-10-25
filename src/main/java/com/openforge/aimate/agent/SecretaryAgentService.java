package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.openforge.aimate.domain.AgentPromptVersion;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.domain.UserProfile;
import com.openforge.aimate.memory.SoulPlatformClient;
import com.openforge.aimate.repository.AgentPromptVersionRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserProfileRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 默认秘书 Agent：画像完成后创建，购 LIFETIME Soul 席 + USER 魂，生成 v1 提示词。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SecretaryAgentService {

    public static final String EXT_KEY_SECRETARY = "secretary";

    private final UserRepository userRepository;
    private final UserProfileRepository profileRepository;
    private final UserAgentRepository userAgentRepository;
    private final AgentPromptVersionRepository promptVersionRepository;
    private final SoulPlatformClient soulPlatformClient;
    private final AgentPromptService agentPromptService;

    @Transactional(readOnly = true)
    public Optional<UserAgent> findSecretary(Long userId) {
        return userAgentRepository.findByUserIdAndKind(userId, UserAgent.Kind.SECRETARY);
    }

    @Transactional
    public UserAgent ensureSecretaryAfterProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserProfile profile = profileRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalStateException("请先完善个人画像"));
        if (!profile.isCompleted()) {
            throw new IllegalStateException("个人画像未完成");
        }

        Optional<UserAgent> existing = userAgentRepository.findByUserIdAndKind(userId, UserAgent.Kind.SECRETARY);
        if (existing.isPresent()) {
            UserAgent ua = existing.get();
            if (ua.getCurrentPromptVersion() <= 0 || ua.getStatus() == UserAgent.Status.PENDING_PROFILE) {
                agentPromptService.generateInitialPrompt(ua, profile);
            }
            return ua;
        }

        if (!StringUtils.hasText(user.getAimatexAccountSub())) {
            throw new IllegalStateException("账号未绑定 AIMATEX，无法创建秘书灵魂");
        }
        if (!soulPlatformClient.isConfigured()) {
            // 无 Soul 时仍创建本地秘书占位，后续配置好再补魂
            return createLocalSecretary(user, profile, null, null, null, null);
        }

        String orderRef = "sec-" + userId + "-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode seat = soulPlatformClient.purchaseSeat(
                user.getAimatexAccountSub(),
                "LIFETIME",
                "CHAT_SECRETARY",
                orderRef,
                user.getDisplayName(),
                user.getEmail()
        );
        long seatId = seat.path("seatId").asLong();
        JsonNode soul = soulPlatformClient.createUserSoul(
                user.getAimatexAccountSub(),
                seatId,
                EXT_KEY_SECRETARY,
                secretaryName(profile),
                "Chat 默认秘书",
                true
        );

        UserAgent ua = createLocalSecretary(
                user,
                profile,
                seatId,
                soul.path("agentId").asLong(0) > 0 ? soul.path("agentId").asLong() : null,
                soul.path("keyId").asText(null),
                soul.path("accessToken").asText(null)
        );
        agentPromptService.generateInitialPrompt(ua, profile);
        return ua;
    }

    private UserAgent createLocalSecretary(
            User user,
            UserProfile profile,
            Long seatId,
            Long soulAgentId,
            String soulKeyId,
            String soulToken
    ) {
        UserAgent ua = new UserAgent();
        ua.setUserId(user.getId());
        ua.setKind(UserAgent.Kind.SECRETARY);
        String secName = secretaryName(profile);
        ua.setName(secName);
        ua.setOriginalName(secName);
        ua.setGender(profile.getGender());
        ua.setBirthday(java.time.LocalDate.now());
        ua.setPersonality("贴合用户职业与生活的专属秘书");
        ua.setExternalAgentKey(EXT_KEY_SECRETARY);
        ua.setSoulSeatId(seatId);
        ua.setSoulAgentId(soulAgentId);
        ua.setSoulKeyId(soulKeyId);
        ua.setSoulAccessToken(soulToken);
        ua.setExpiresAt(null);
        ua.setStatus(UserAgent.Status.ACTIVE);
        ua.setCurrentPromptVersion(0);
        return userAgentRepository.save(ua);
    }

    private static String secretaryName(UserProfile profile) {
        String occ = StringUtils.hasText(profile.getOccupation()) ? profile.getOccupation().trim() : "专属";
        return occ + "秘书";
    }

    @Transactional(readOnly = true)
    public List<AgentPromptVersion> listPromptVersions(Long userId, Long userAgentId) {
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        return promptVersionRepository.findByUserAgentIdOrderByVersionNoDesc(ua.getId());
    }
}
