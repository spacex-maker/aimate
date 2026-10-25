package com.openforge.aimate.agent;

import com.openforge.aimate.domain.AgentCareer;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentExecutionMode;
import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.domain.RentPeriod;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.localbridge.LocalBridgeService;
import com.openforge.aimate.repository.AgentCareerRepository;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 桌面连接器：按 AI 产品（Cursor / Codex / Workbuddy…）开关，
 * 打开时自动招聘/唤醒对应 LOCAL_BRIDGE Agent，关闭时停用。
 */
@Service
@RequiredArgsConstructor
public class ConnectorLinkService {

    private final AgentCareerRepository careerRepository;
    private final AgentCatalogRepository catalogRepository;
    private final UserAgentRepository userAgentRepository;
    private final AgentHireService agentHireService;
    private final LocalBridgeService localBridgeService;
    private final UserAgentService userAgentService;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> list(Long userId) {
        List<AgentCareer> careers = careerRepository.findAll().stream()
                .filter(c -> c.isEnabled() && c.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE)
                .sorted(Comparator.comparingInt(AgentCareer::getSortOrder).thenComparing(AgentCareer::getId))
                .toList();
        List<UserAgent> mine = userAgentRepository.findByUserIdOrderByUpdateTimeDesc(userId);
        List<Map<String, Object>> out = new ArrayList<>();
        for (AgentCareer career : careers) {
            List<Long> catalogIds = catalogRepository.findByCareerIdOrderBySortOrderAscIdAsc(career.getId())
                    .stream().map(AgentCatalog::getId).toList();
            UserAgent linked = mine.stream()
                    .filter(ua -> ua.getCatalogId() != null && catalogIds.contains(ua.getCatalogId()))
                    .filter(ua -> ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE)
                    .findFirst()
                    .orElse(null);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("careerCode", career.getCode());
            row.put("careerId", career.getId());
            row.put("name", career.getName());
            row.put("description", career.getDescription());
            row.put("enabled", linked != null && linked.getStatus() == UserAgent.Status.ACTIVE);
            row.put("userAgentId", linked != null ? linked.getId() : null);
            row.put("agentName", linked != null ? linked.getName() : null);
            row.put("agentStatus", linked != null ? linked.getStatus().name() : null);
            row.put("localBridgeConfigured", linked != null && StringUtils.hasText(linked.getLocalBridgeTokenHash()));
            AgentCatalog prefer = preferCatalog(career.getId());
            row.put("catalogId", prefer != null ? prefer.getId() : null);
            row.put("catalogName", prefer != null ? prefer.getName() : null);
            out.add(row);
        }
        return out;
    }

    @Transactional
    public Map<String, Object> enable(Long userId, String careerCode) {
        AgentCareer career = requireLocalCareer(careerCode);
        AgentCatalog catalog = preferCatalog(career.getId());
        if (catalog == null || !catalog.isEnabled()) {
            throw new IllegalStateException("该连接器暂无上架商品");
        }

        List<Long> catalogIds = catalogRepository.findByCareerIdOrderBySortOrderAscIdAsc(career.getId())
                .stream().map(AgentCatalog::getId).toList();
        Set<Long> idSet = Set.copyOf(catalogIds);
        UserAgent linked = userAgentRepository.findByUserIdOrderByUpdateTimeDesc(userId).stream()
                .filter(ua -> ua.getCatalogId() != null && idSet.contains(ua.getCatalogId()))
                .filter(ua -> ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE)
                .findFirst()
                .orElse(null);

        boolean hired = false;
        if (linked == null) {
            // 连接器开通默认按「天」招聘初级，降低首次门槛
            Map<String, Object> hire = agentHireService.hire(userId, catalog.getId(), RentPeriod.DAY);
            Long uaId = ((Number) hire.get("userAgentId")).longValue();
            linked = userAgentRepository.findById(uaId)
                    .orElseThrow(() -> new IllegalStateException("招聘后未找到 Agent"));
            hired = true;
        } else if (linked.getStatus() != UserAgent.Status.ACTIVE) {
            linked.setStatus(UserAgent.Status.ACTIVE);
            if (linked.getExpiresAt() != null && linked.getExpiresAt().isBefore(LocalDateTime.now())) {
                // 过期则续一天（走续费逻辑需要席位；失败则提示）
                try {
                    agentHireService.renew(userId, linked.getId(), RentPeriod.DAY);
                } catch (Exception e) {
                    throw new IllegalStateException("Agent 已过期且续费失败：" + e.getMessage());
                }
                linked = userAgentRepository.findById(linked.getId()).orElse(linked);
            }
            userAgentRepository.save(linked);
        }

        // 连接器端必须拿到明文 token 才能本机验活 / 拉任务
        Map<String, Object> bundle = localBridgeService.issueConnectBundle(userId, linked.getId(), true);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("hired", hired);
        out.put("careerCode", career.getCode());
        out.put("userAgentId", linked.getId());
        out.put("agent", userAgentService.detail(userId, linked.getId()));
        out.put("bridge", bundle);
        return out;
    }

    @Transactional
    public Map<String, Object> disable(Long userId, String careerCode) {
        AgentCareer career = requireLocalCareer(careerCode);
        List<Long> catalogIds = catalogRepository.findByCareerIdOrderBySortOrderAscIdAsc(career.getId())
                .stream().map(AgentCatalog::getId).toList();
        Set<Long> idSet = Set.copyOf(catalogIds);
        UserAgent linked = userAgentRepository.findByUserIdOrderByUpdateTimeDesc(userId).stream()
                .filter(ua -> ua.getCatalogId() != null && idSet.contains(ua.getCatalogId()))
                .filter(ua -> ua.getExecutionMode() == AgentExecutionMode.LOCAL_BRIDGE)
                .findFirst()
                .orElse(null);
        if (linked == null) {
            return Map.of("ok", true, "careerCode", career.getCode(), "enabled", false);
        }
        linked.setStatus(UserAgent.Status.DISABLED);
        userAgentRepository.save(linked);
        // 关闭后立即清在线态，避免 Web 仍显示「已连接本地设备」
        localBridgeService.clearPresence(linked.getId());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("ok", true);
        out.put("careerCode", career.getCode());
        out.put("enabled", false);
        out.put("userAgentId", linked.getId());
        return out;
    }

    private AgentCareer requireLocalCareer(String careerCode) {
        if (!StringUtils.hasText(careerCode)) {
            throw new IllegalArgumentException("缺少 careerCode");
        }
        AgentCareer career = careerRepository.findByCodeIgnoreCase(careerCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("未知连接器：" + careerCode));
        if (!career.isEnabled() || career.getExecutionMode() != AgentExecutionMode.LOCAL_BRIDGE) {
            throw new IllegalStateException("该产品不支持本地连接器");
        }
        return career;
    }

    private AgentCatalog preferCatalog(Long careerId) {
        List<AgentCatalog> list = catalogRepository.findByCareerIdOrderBySortOrderAscIdAsc(careerId).stream()
                .filter(AgentCatalog::isEnabled)
                .toList();
        if (list.isEmpty()) return null;
        return list.stream()
                .filter(c -> c.getTier() == AgentTier.JUNIOR)
                .findFirst()
                .or(() -> list.stream().min(Comparator.comparingInt(c -> c.getTier().ordinal())))
                .orElse(list.get(0));
    }
}
