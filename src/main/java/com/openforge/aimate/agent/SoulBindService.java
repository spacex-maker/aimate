package com.openforge.aimate.agent;

import com.openforge.aimate.domain.User;
import com.openforge.aimate.memory.SoulMemoryProperties;
import com.openforge.aimate.memory.SoulPlatformClient;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Chat 用户必须绑定 Soul 影子账号（Account sub）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SoulBindService {

    private final SoulPlatformClient soulPlatformClient;
    private final SoulMemoryProperties props;
    private final UserRepository userRepository;

    public void ensureSoulUser(User user) {
        if (user == null) return;
        if (!StringUtils.hasText(user.getAimatexAccountSub())) {
            log.debug("[SoulBind] skip user {} — no account sub", user.getId());
            return;
        }
        if (!props.isEnabled() && !soulPlatformClient.isConfigured()) {
            return;
        }
        if (!soulPlatformClient.isConfigured()) {
            log.warn("[SoulBind] Soul platform not configured, skip ensureUser");
            return;
        }
        try {
            soulPlatformClient.ensureUser(
                    user.getAimatexAccountSub(),
                    user.getDisplayName(),
                    user.getEmail()
            );
        } catch (Exception e) {
            log.warn("[SoulBind] ensureUser failed user={}: {}", user.getId(), e.toString());
        }
    }

    public void ensureSoulUserById(Long userId) {
        if (userId == null) return;
        userRepository.findById(userId).ifPresent(this::ensureSoulUser);
    }
}
