package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 伙伴展示名：真名 + 招聘时职业（便于区分同名/昵称后的 Agent）。
 */
@Service
@RequiredArgsConstructor
public class UserAgentIdentityService {

    private final AgentCatalogRepository agentCatalogRepository;
    private final AgentCareerRepository agentCareerRepository;

    public String originalName(UserAgent ua) {
        if (ua == null) return null;
        if (StringUtils.hasText(ua.getOriginalName())) return ua.getOriginalName().trim();
        if (ua.getCatalogId() != null) {
            String fromCatalog = agentCatalogRepository.findById(ua.getCatalogId())
                    .map(AgentCatalog::getName)
                    .orElse(null);
            if (StringUtils.hasText(fromCatalog)) return fromCatalog.trim();
        }
        if (ua.getKind() == UserAgent.Kind.SECRETARY) return "秘书";
        return StringUtils.hasText(ua.getName()) ? ua.getName().trim() : null;
    }

    /** 招聘时的职业名（不含档位）；秘书固定为「秘书」。 */
    public String careerName(UserAgent ua) {
        if (ua == null) return null;
        if (ua.getKind() == UserAgent.Kind.SECRETARY) return "秘书";
        if (ua.getCatalogId() == null) return originalName(ua);
        return agentCatalogRepository.findById(ua.getCatalogId())
                .map(AgentCatalog::getCareerId)
                .flatMap(agentCareerRepository::findById)
                .map(c -> c.getName())
                .filter(StringUtils::hasText)
                .map(String::trim)
                .orElseGet(() -> originalName(ua));
    }

    /** 列表紧凑标题：Orion · Cursor */
    public String displayTitle(UserAgent ua) {
        if (ua == null) return "Agent";
        String name = StringUtils.hasText(ua.getName()) ? ua.getName().trim() : null;
        String career = careerName(ua);
        if (StringUtils.hasText(name) && StringUtils.hasText(career) && !name.equals(career)) {
            return name + " · " + career;
        }
        return StringUtils.hasText(name) ? name : (StringUtils.hasText(career) ? career : "Agent");
    }
}
