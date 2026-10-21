package com.openforge.aimate.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.openforge.aimate.billing.PointsService;
import com.openforge.aimate.domain.AgentCatalog;
import com.openforge.aimate.domain.AgentRentOrder;
import com.openforge.aimate.domain.AgentTier;
import com.openforge.aimate.domain.AgentTierModel;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.RentPeriod;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.domain.UserAgent;
import com.openforge.aimate.memory.SoulPlatformClient;
import com.openforge.aimate.repository.AgentCatalogRepository;
import com.openforge.aimate.repository.AgentRentOrderRepository;
import com.openforge.aimate.repository.AgentTierModelRepository;
import com.openforge.aimate.repository.UserAgentRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AgentHireService {

    private final AgentCatalogRepository catalogRepository;
    private final AgentTierModelRepository tierModelRepository;
    private final UserAgentRepository userAgentRepository;
    private final AgentRentOrderRepository rentOrderRepository;
    private final UserRepository userRepository;
    private final PointsService pointsService;
    private final SoulPlatformClient soulPlatformClient;

    @Transactional(readOnly = true)
    public Map<String, Object> quote(Long catalogId, RentPeriod period) {
        AgentCatalog c = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        if (!c.isEnabled()) throw new IllegalStateException("该 Agent 未上架");
        long agentPts = priceAgent(c, period);
        long soulPts = priceSoul(c, period);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("catalogId", c.getId());
        m.put("name", c.getName());
        m.put("tier", c.getTier().name());
        m.put("period", period.name());
        m.put("agentPoints", agentPts);
        m.put("soulPoints", soulPts);
        m.put("totalPoints", agentPts + soulPts);
        m.put("lines", java.util.List.of(
                Map.of("label", "Agent 租用", "points", agentPts),
                Map.of("label", "Soul 席位", "points", soulPts)
        ));
        return m;
    }

    @Transactional
    public Map<String, Object> hire(Long userId, Long catalogId, RentPeriod period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        if (!StringUtils.hasText(user.getAimatexAccountSub())) {
            throw new IllegalStateException("请使用 AIMATEX 统一账号登录后再招聘");
        }
        AgentCatalog c = catalogRepository.findById(catalogId)
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));
        if (!c.isEnabled()) throw new IllegalStateException("该 Agent 未上架");

        long agentPts = priceAgent(c, period);
        long soulPts = priceSoul(c, period);
        long total = agentPts + soulPts;
        if (total <= 0) throw new IllegalStateException("价格未配置");

        if (!soulPlatformClient.isConfigured()) {
            throw new IllegalStateException("Soul 平台未配置，无法完成招聘");
        }
        if (pointsService.balance(userId) < total) {
            throw new IllegalStateException("积分不足");
        }

        String orderNo = "AR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        JsonNode seat = soulPlatformClient.purchaseSeat(
                user.getAimatexAccountSub(),
                period.name(),
                "CHAT_AGENT",
                orderNo,
                user.getDisplayName(),
                user.getEmail()
        );
        long seatId = seat.path("seatId").asLong();

        String extKey = "hire-" + c.getId() + "-" + UUID.randomUUID().toString().substring(0, 8);
        JsonNode soul = soulPlatformClient.createUserSoul(
                user.getAimatexAccountSub(),
                seatId,
                extKey,
                c.getName(),
                c.getDescription(),
                true
        );

        pointsService.deduct(userId, agentPts, PointLedger.AGENT_RENT, "AGENT_RENT", orderNo, null, null,
                c.getName() + " · " + periodLabel(period));
        pointsService.deduct(userId, soulPts, PointLedger.SOUL_SEAT, "SOUL_SEAT", orderNo, null, null,
                "Soul 席位 · " + periodLabel(period));

        AgentRentOrder order = new AgentRentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setCatalogId(c.getId());
        order.setOrderType(AgentRentOrder.OrderType.HIRE);
        order.setPeriod(period);
        order.setAgentPoints(agentPts);
        order.setSoulPoints(soulPts);
        order.setTotalPoints(total);
        order.setSoulSeatId(seatId);
        order.setStatus(AgentRentOrder.Status.PAID);

        UserAgent ua = new UserAgent();
        ua.setUserId(userId);
        ua.setKind(UserAgent.Kind.HIRED);
        ua.setCatalogId(c.getId());
        ua.setTier(c.getTier());
        ua.setName(c.getName());
        ua.setAvatarUrl(c.getDefaultAvatarUrl());
        ua.setGender(c.getDefaultGender());
        ua.setPersonality(c.getPersonaTemplate() != null && c.getPersonaTemplate().length() > 512
                ? c.getPersonaTemplate().substring(0, 512)
                : c.getPersonaTemplate());
        ua.setExternalAgentKey(extKey);
        ua.setSoulSeatId(seatId);
        ua.setSoulAgentId(soul.path("agentId").asLong(0) > 0 ? soul.path("agentId").asLong() : null);
        ua.setSoulKeyId(soul.path("keyId").asText(null));
        ua.setSoulAccessToken(soul.path("accessToken").asText(null));
        ua.setExpiresAt(expiresAt(period));
        ua.setStatus(UserAgent.Status.ACTIVE);
        ua.setDefaultSystemModelId(defaultModelId(c.getTier()));
        ua = userAgentRepository.save(ua);

        order.setUserAgentId(ua.getId());
        rentOrderRepository.save(order);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("orderNo", orderNo);
        out.put("userAgentId", ua.getId());
        out.put("expiresAt", ua.getExpiresAt() != null ? ua.getExpiresAt().toString() : null);
        out.put("agentPoints", agentPts);
        out.put("soulPoints", soulPts);
        out.put("totalPoints", total);
        return out;
    }

    @Transactional
    public Map<String, Object> renew(Long userId, Long userAgentId, RentPeriod period) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("用户不存在"));
        UserAgent ua = userAgentRepository.findByIdAndUserId(userAgentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Agent 不存在"));
        if (ua.getKind() != UserAgent.Kind.HIRED || ua.getCatalogId() == null) {
            throw new IllegalStateException("仅已招聘 Agent 可续费");
        }
        if (ua.getSoulSeatId() == null) {
            throw new IllegalStateException("缺少 Soul 席位，无法续费");
        }
        AgentCatalog c = catalogRepository.findById(ua.getCatalogId())
                .orElseThrow(() -> new IllegalArgumentException("商品不存在"));

        long agentPts = priceAgent(c, period);
        long soulPts = priceSoul(c, period);
        long total = agentPts + soulPts;
        String orderNo = "RN" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);

        pointsService.deduct(userId, agentPts, PointLedger.AGENT_RENT, "AGENT_RENT", orderNo, null, null,
                c.getName() + " 续费 · " + periodLabel(period));
        pointsService.deduct(userId, soulPts, PointLedger.SOUL_SEAT, "SOUL_SEAT", orderNo, null, null,
                "Soul 席位续费 · " + periodLabel(period));

        soulPlatformClient.renewSeat(user.getAimatexAccountSub(), ua.getSoulSeatId(), period.name(), orderNo);

        LocalDateTime base = ua.getExpiresAt() != null && ua.getExpiresAt().isAfter(LocalDateTime.now())
                ? ua.getExpiresAt()
                : LocalDateTime.now();
        ua.setExpiresAt(addPeriod(base, period));
        ua.setStatus(UserAgent.Status.ACTIVE);
        userAgentRepository.save(ua);

        AgentRentOrder order = new AgentRentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setCatalogId(c.getId());
        order.setUserAgentId(ua.getId());
        order.setOrderType(AgentRentOrder.OrderType.RENEW);
        order.setPeriod(period);
        order.setAgentPoints(agentPts);
        order.setSoulPoints(soulPts);
        order.setTotalPoints(total);
        order.setSoulSeatId(ua.getSoulSeatId());
        order.setStatus(AgentRentOrder.Status.PAID);
        rentOrderRepository.save(order);

        return Map.of(
                "orderNo", orderNo,
                "userAgentId", ua.getId(),
                "expiresAt", ua.getExpiresAt().toString(),
                "totalPoints", total
        );
    }

    private Long defaultModelId(AgentTier tier) {
        return tierModelRepository.findFirstByTierAndDefaultModelTrue(tier)
                .map(AgentTierModel::getSystemModelId)
                .orElse(null);
    }

    private static long priceAgent(AgentCatalog c, RentPeriod p) {
        return switch (p) {
            case DAY -> c.getPriceDayPoints();
            case MONTH -> c.getPriceMonthPoints();
            case YEAR -> c.getPriceYearPoints();
        };
    }

    private static long priceSoul(AgentCatalog c, RentPeriod p) {
        return switch (p) {
            case DAY -> c.getSoulPriceDayPoints();
            case MONTH -> c.getSoulPriceMonthPoints();
            case YEAR -> c.getSoulPriceYearPoints();
        };
    }

    private static LocalDateTime expiresAt(RentPeriod p) {
        return addPeriod(LocalDateTime.now(), p);
    }

    private static LocalDateTime addPeriod(LocalDateTime base, RentPeriod p) {
        return switch (p) {
            case DAY -> base.plusDays(1);
            case MONTH -> base.plusDays(30);
            case YEAR -> base.plusDays(365);
        };
    }

    private static String periodLabel(RentPeriod p) {
        return switch (p) {
            case DAY -> "包日";
            case MONTH -> "包月";
            case YEAR -> "包年";
        };
    }
}
