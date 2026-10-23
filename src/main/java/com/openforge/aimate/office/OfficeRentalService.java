package com.openforge.aimate.office;

import com.openforge.aimate.billing.PointsService;
import com.openforge.aimate.domain.OfficePlan;
import com.openforge.aimate.domain.OfficeRentOrder;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.RentPeriod;
import com.openforge.aimate.domain.UserOffice;
import com.openforge.aimate.repository.OfficePlanRepository;
import com.openforge.aimate.repository.OfficeRentOrderRepository;
import com.openforge.aimate.repository.UserOfficeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class OfficeRentalService {

    private final OfficePlanRepository planRepository;
    private final UserOfficeRepository officeRepository;
    private final OfficeRentOrderRepository orderRepository;
    private final PointsService pointsService;
    private final OfficeCvmClient cvmClient;
    private final OfficeCvmProperties props;

    @Transactional(readOnly = true)
    public List<Map<String, Object>> listPlans() {
        return planRepository.findByEnabledTrueOrderBySortOrderAscIdAsc().stream()
                .map(this::planView)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Object> quote(Long planId, RentPeriod period) {
        OfficePlan p = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("办公室规格不存在"));
        if (!p.isEnabled()) throw new IllegalStateException("该规格未上架");
        long pts = price(p, period);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("planId", p.getId());
        m.put("name", p.getName());
        m.put("areaSqm", p.getAreaSqm());
        m.put("period", period.name());
        m.put("totalPoints", pts);
        m.put("lines", List.of(Map.of("label", "办公室租用", "points", pts)));
        return m;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> myOffice(Long userId) {
        refreshExpired(userId);
        return officeRepository
                .findFirstByUserIdAndStatusInOrderByIdDesc(userId, List.of(
                        UserOffice.Status.PROVISIONING,
                        UserOffice.Status.ACTIVE,
                        UserOffice.Status.FAILED,
                        UserOffice.Status.STOPPED
                ))
                .map(this::officeView)
                .orElse(Map.of("status", "NONE"));
    }

    @Transactional
    public Map<String, Object> rent(Long userId, Long planId, RentPeriod period) {
        refreshExpired(userId);
        officeRepository.findFirstByUserIdAndStatusInOrderByIdDesc(userId, List.of(
                UserOffice.Status.PROVISIONING, UserOffice.Status.ACTIVE
        )).ifPresent(o -> {
            throw new IllegalStateException("已有办公室进行中，请先释放或等待开通完成");
        });

        OfficePlan plan = planRepository.findById(planId)
                .orElseThrow(() -> new IllegalArgumentException("办公室规格不存在"));
        if (!plan.isEnabled()) throw new IllegalStateException("该规格未上架");
        long total = price(plan, period);
        if (total <= 0) throw new IllegalStateException("价格未配置");
        if (pointsService.balance(userId) < total) {
            throw new IllegalStateException("积分不足");
        }

        String orderNo = "OR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "").substring(0, 16);

        UserOffice office = new UserOffice();
        office.setUserId(userId);
        office.setPlanId(plan.getId());
        office.setStatus(UserOffice.Status.PROVISIONING);
        office.setOfficeToken(token);
        office.setExpiresAt(LocalDateTime.now().plus(periodDuration(period)));
        office = officeRepository.save(office);

        pointsService.deduct(userId, total, PointLedger.OFFICE_RENT, "OFFICE", orderNo, null, null,
                plan.getName() + " · " + period.name());

        OfficeRentOrder order = new OfficeRentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPlanId(plan.getId());
        order.setUserOfficeId(office.getId());
        order.setOrderType(OfficeRentOrder.OrderType.RENT);
        order.setPeriod(period);
        order.setTotalPoints(total);
        order.setStatus(OfficeRentOrder.Status.PAID);
        orderRepository.save(order);

        Long officeId = office.getId();
        provisionAsync(officeId, plan.getId(), token, userId);

        return officeView(office);
    }

    @Async
    public void provisionAsync(Long officeId, Long planId, String token, Long userId) {
        try {
            OfficePlan plan = planRepository.findById(planId).orElseThrow();
            OfficeCvmClient.ProvisionResult r = cvmClient.runInstance(plan, token, userId);
            UserOffice office = officeRepository.findById(officeId).orElseThrow();
            office.setCvmInstanceId(r.instanceId());
            String ip = r.privateIp();
            if (ip == null) {
                ip = cvmClient.waitPrivateIp(r.instanceId(), 180);
            }
            if (ip != null) office.setPrivateIp(ip);
            if (ip != null || r.instanceId().startsWith("ins-mock-")) {
                office.setStatus(UserOffice.Status.ACTIVE);
                office.setLastHeartbeatAt(LocalDateTime.now());
                office.setProvisionError(null);
            } else {
                office.setStatus(UserOffice.Status.PROVISIONING);
                office.setProvisionError("等待内网 IP 中");
            }
            officeRepository.save(office);
            log.info("[Office] provisioned officeId={} instance={} ip={}", officeId, r.instanceId(), ip);
        } catch (Exception e) {
            log.error("[Office] provision failed officeId={}: {}", officeId, e.getMessage(), e);
            officeRepository.findById(officeId).ifPresent(o -> {
                o.setStatus(UserOffice.Status.FAILED);
                o.setProvisionError(e.getMessage() != null && e.getMessage().length() > 1000
                        ? e.getMessage().substring(0, 1000) : e.getMessage());
                officeRepository.save(o);
            });
        }
    }

    @Transactional
    public Map<String, Object> renew(Long userId, RentPeriod period) {
        UserOffice office = requireActiveOrProvisioning(userId);
        OfficePlan plan = planRepository.findById(office.getPlanId())
                .orElseThrow(() -> new IllegalArgumentException("规格不存在"));
        long total = price(plan, period);
        if (pointsService.balance(userId) < total) throw new IllegalStateException("积分不足");

        String orderNo = "OR" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6);
        pointsService.deduct(userId, total, PointLedger.OFFICE_RENT, "OFFICE", orderNo, null, null,
                "续费 " + plan.getName() + " · " + period.name());

        LocalDateTime base = office.getExpiresAt() != null && office.getExpiresAt().isAfter(LocalDateTime.now())
                ? office.getExpiresAt() : LocalDateTime.now();
        office.setExpiresAt(base.plus(periodDuration(period)));
        if (office.getStatus() == UserOffice.Status.EXPIRED || office.getStatus() == UserOffice.Status.STOPPED) {
            office.setStatus(UserOffice.Status.ACTIVE);
        }
        officeRepository.save(office);

        OfficeRentOrder order = new OfficeRentOrder();
        order.setOrderNo(orderNo);
        order.setUserId(userId);
        order.setPlanId(plan.getId());
        order.setUserOfficeId(office.getId());
        order.setOrderType(OfficeRentOrder.OrderType.RENEW);
        order.setPeriod(period);
        order.setTotalPoints(total);
        order.setStatus(OfficeRentOrder.Status.PAID);
        orderRepository.save(order);

        return officeView(office);
    }

    @Transactional
    public Map<String, Object> release(Long userId) {
        UserOffice office = officeRepository
                .findFirstByUserIdAndStatusInOrderByIdDesc(userId, List.of(
                        UserOffice.Status.PROVISIONING,
                        UserOffice.Status.ACTIVE,
                        UserOffice.Status.FAILED,
                        UserOffice.Status.STOPPED
                ))
                .orElseThrow(() -> new IllegalArgumentException("当前没有办公室"));
        office.setStatus(UserOffice.Status.STOPPING);
        officeRepository.save(office);
        try {
            cvmClient.terminate(office.getCvmInstanceId());
        } catch (Exception e) {
            log.warn("[Office] terminate warn: {}", e.getMessage());
        }
        office.setStatus(UserOffice.Status.STOPPED);
        office.setPrivateIp(null);
        officeRepository.save(office);
        return officeView(office);
    }

    @Transactional(readOnly = true)
    public boolean hasActiveOffice(Long userId) {
        if (userId == null) return false;
        refreshExpired(userId);
        return officeRepository
                .findFirstByUserIdAndStatusInOrderByIdDesc(userId, List.of(UserOffice.Status.ACTIVE))
                .filter(o -> o.getExpiresAt() == null || o.getExpiresAt().isAfter(LocalDateTime.now()))
                .isPresent();
    }

    @Transactional(readOnly = true)
    public UserOffice requireActiveOfficeEntity(Long userId) {
        refreshExpired(userId);
        return officeRepository
                .findFirstByUserIdAndStatusInOrderByIdDesc(userId, List.of(UserOffice.Status.ACTIVE))
                .filter(o -> o.getExpiresAt() == null || o.getExpiresAt().isAfter(LocalDateTime.now()))
                .orElseThrow(() -> new IllegalStateException("请先租用办公室后再使用宽边界工具"));
    }

    private UserOffice requireActiveOrProvisioning(Long userId) {
        return officeRepository
                .findFirstByUserIdAndStatusInOrderByIdDesc(userId, List.of(
                        UserOffice.Status.PROVISIONING, UserOffice.Status.ACTIVE
                ))
                .orElseThrow(() -> new IllegalArgumentException("当前没有可续费的办公室"));
    }

    private void refreshExpired(Long userId) {
        for (UserOffice o : officeRepository.findByUserIdOrderByIdDesc(userId)) {
            if (o.getExpiresAt() != null
                    && o.getExpiresAt().isBefore(LocalDateTime.now())
                    && (o.getStatus() == UserOffice.Status.ACTIVE || o.getStatus() == UserOffice.Status.PROVISIONING)) {
                o.setStatus(UserOffice.Status.EXPIRED);
                officeRepository.save(o);
                try {
                    cvmClient.terminate(o.getCvmInstanceId());
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long price(OfficePlan p, RentPeriod period) {
        return switch (period) {
            case DAY -> p.getPriceDayPoints();
            case MONTH -> p.getPriceMonthPoints();
            case YEAR -> p.getPriceYearPoints();
        };
    }

    private static java.time.Period periodDuration(RentPeriod period) {
        return switch (period) {
            case DAY -> java.time.Period.ofDays(1);
            case MONTH -> java.time.Period.ofMonths(1);
            case YEAR -> java.time.Period.ofYears(1);
        };
    }

    private Map<String, Object> planView(OfficePlan p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId());
        m.put("code", p.getCode());
        m.put("name", p.getName());
        m.put("areaSqm", p.getAreaSqm());
        m.put("instanceType", p.getInstanceType());
        m.put("cpuDesc", p.getCpuDesc());
        m.put("memDesc", p.getMemDesc());
        m.put("diskGb", p.getDiskGb());
        m.put("priceDayPoints", p.getPriceDayPoints());
        m.put("priceMonthPoints", p.getPriceMonthPoints());
        m.put("priceYearPoints", p.getPriceYearPoints());
        m.put("description", p.getDescription());
        m.put("enabled", p.isEnabled());
        return m;
    }

    private Map<String, Object> officeView(UserOffice o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("planId", o.getPlanId());
        planRepository.findById(o.getPlanId()).ifPresent(p -> {
            m.put("planName", p.getName());
            m.put("areaSqm", p.getAreaSqm());
        });
        m.put("status", o.getStatus().name());
        m.put("ready", o.getStatus() == UserOffice.Status.ACTIVE);
        m.put("expiresAt", o.getExpiresAt() != null ? o.getExpiresAt().toString() : null);
        m.put("provisionError", o.getProvisionError());
        m.put("lastHeartbeatAt", o.getLastHeartbeatAt() != null ? o.getLastHeartbeatAt().toString() : null);
        m.put("mock", props.isMockProvision() || !props.isConfigured());
        // 不对前端暴露 privateIp / token / instanceId
        return m;
    }
}
