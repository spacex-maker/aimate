package com.openforge.aimate.agent;

import com.openforge.aimate.agent.dto.BillingUsageDetail;
import com.openforge.aimate.agent.dto.BillingUsageResponse;
import com.openforge.aimate.agent.dto.BillingUsageSummary;
import com.openforge.aimate.billing.PointsService;
import com.openforge.aimate.domain.LlmCallLog;
import com.openforge.aimate.domain.PaymentOrder;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.PointPackage;
import com.openforge.aimate.domain.User;
import com.openforge.aimate.repository.LlmCallLogRepository;
import com.openforge.aimate.repository.PaymentOrderRepository;
import com.openforge.aimate.repository.PointLedgerRepository;
import com.openforge.aimate.repository.PointPackageRepository;
import com.openforge.aimate.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/billing")
@RequiredArgsConstructor
public class AdminBillingController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final LlmCallLogRepository callLogRepository;
    private final UserRepository userRepository;
    private final PointPackageRepository packageRepository;
    private final PaymentOrderRepository orderRepository;
    private final PointLedgerRepository ledgerRepository;
    private final PointsService pointsService;

    @GetMapping("/usage")
    public ResponseEntity<BillingUsageResponse> usage(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        List<Object[]> rows = callLogRepository.aggregateUsage(userId, from, to);
        Map<Long, User> users = userRepository.findAllById(
                rows.stream().map(r -> (Long) r[0]).toList()
        ).stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<BillingUsageSummary> summaries = new ArrayList<>();
        long grandTokens = 0;
        double grandCost = 0;
        for (Object[] r : rows) {
            Long uid = (Long) r[0];
            long prompt = ((Number) r[1]).longValue();
            long completion = ((Number) r[2]).longValue();
            long total = ((Number) r[3]).longValue();
            double cost = ((Number) r[4]).doubleValue();
            long calls = ((Number) r[5]).longValue();
            User u = users.get(uid);
            summaries.add(new BillingUsageSummary(
                    uid,
                    u != null ? u.getUsername() : String.valueOf(uid),
                    calls,
                    prompt,
                    completion,
                    total,
                    cost
            ));
            grandTokens += total;
            grandCost += cost;
        }
        return ResponseEntity.ok(new BillingUsageResponse(summaries, grandTokens, grandCost));
    }

    @GetMapping("/usage/{userId}")
    public ResponseEntity<List<BillingUsageDetail>> usageDetail(
            @PathVariable Long userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @RequestParam(defaultValue = "100") int limit
    ) {
        List<LlmCallLog> logs = callLogRepository.findDetails(
                userId, from, to, PageRequest.of(0, Math.min(Math.max(limit, 1), 500)));
        List<BillingUsageDetail> details = logs.stream()
                .map(l -> new BillingUsageDetail(
                        l.getId(),
                        l.getProvider(),
                        l.getModel(),
                        l.getCallType() != null ? l.getCallType().name() : null,
                        l.getSessionId(),
                        l.getPromptTokens(),
                        l.getCompletionTokens(),
                        l.getTotalTokens(),
                        l.getEstimatedCostCny() != null ? l.getEstimatedCostCny() : l.getEstimatedCostUsd(),
                        l.getPointsCharged(),
                        l.isSuccess(),
                        l.getCreateTime() != null ? l.getCreateTime().format(ISO) : null
                ))
                .toList();
        return ResponseEntity.ok(details);
    }

    @GetMapping("/packages")
    public List<Map<String, Object>> listPackages() {
        return packageRepository.findAllByOrderBySortOrderAsc().stream().map(this::packageView).toList();
    }

    @PostMapping("/packages")
    public Map<String, Object> createPackage(@RequestBody PackageBody body) {
        PointPackage pack = new PointPackage();
        applyPackage(pack, body);
        return packageView(packageRepository.save(pack));
    }

    @PutMapping("/packages/{id}")
    public Map<String, Object> updatePackage(@PathVariable Long id, @RequestBody PackageBody body) {
        PointPackage pack = packageRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "套餐不存在"));
        applyPackage(pack, body);
        return packageView(packageRepository.save(pack));
    }

    @DeleteMapping("/packages/{id}")
    public ResponseEntity<Void> deletePackage(@PathVariable Long id) {
        if (!packageRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "套餐不存在");
        }
        packageRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/orders")
    public Map<String, Object> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status
    ) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<PaymentOrder> result;
        if (status != null && !status.isBlank()) {
            result = orderRepository.findByStatusOrderByCreateTimeDesc(status.trim().toUpperCase(), PageRequest.of(p, s));
        } else {
            result = orderRepository.findAllByOrderByCreateTimeDesc(PageRequest.of(p, s));
        }
        Map<String, Object> m = new HashMap<>();
        m.put("items", result.getContent().stream().map(this::orderView).toList());
        m.put("page", p);
        m.put("size", s);
        m.put("total", result.getTotalElements());
        m.put("totalPages", result.getTotalPages());
        return m;
    }

    @GetMapping("/users/{userId}/ledger")
    public Map<String, Object> userLedger(
            @PathVariable Long userId,
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<PointLedger> result;
        if (reason != null && !reason.isBlank()) {
            result = ledgerRepository.findByUserIdAndReasonOrderByCreateTimeDesc(
                    userId, reason.trim().toUpperCase(), PageRequest.of(p, s));
        } else {
            result = ledgerRepository.findByUserIdOrderByCreateTimeDesc(userId, PageRequest.of(p, s));
        }
        Map<String, Object> m = new HashMap<>();
        m.put("items", result.getContent().stream().map(this::ledgerView).toList());
        m.put("page", p);
        m.put("size", s);
        m.put("total", result.getTotalElements());
        m.put("totalPages", result.getTotalPages());
        m.put("pointsBalance", pointsService.balance(userId));
        return m;
    }

    @PostMapping("/users/{userId}/adjust")
    public Map<String, Object> adjustPoints(@PathVariable Long userId, @RequestBody AdjustBody body) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "用户不存在");
        }
        long delta = body.delta();
        if (delta == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "delta 不能为 0");
        }
        String remark = body.remark() != null ? body.remark() : "管理员调整";
        long after;
        if (delta > 0) {
            after = pointsService.credit(userId, delta, PointLedger.ADMIN_ADJUST, "ADMIN", String.valueOf(userId), remark);
        } else {
            after = pointsService.deduct(userId, -delta, PointLedger.ADMIN_ADJUST, "ADMIN", String.valueOf(userId), null, null, remark);
        }
        return Map.of("pointsBalance", after);
    }

    private void applyPackage(PointPackage pack, PackageBody body) {
        if (body.name() == null || body.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "名称不能为空");
        }
        if (body.priceYuan() == null || body.priceYuan().compareTo(BigDecimal.ZERO) < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "价格无效");
        }
        if (body.points() == null || body.points() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "积分必须大于 0");
        }
        pack.setName(body.name().trim());
        pack.setPriceYuan(body.priceYuan());
        pack.setPoints(body.points());
        pack.setBonusPoints(body.bonusPoints() != null ? Math.max(0, body.bonusPoints()) : 0);
        if (body.enabled() != null) pack.setEnabled(body.enabled());
        if (body.sortOrder() != null) pack.setSortOrder(body.sortOrder());
        pack.setDescription(body.description());
    }

    private Map<String, Object> packageView(PointPackage pack) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", pack.getId());
        m.put("name", pack.getName());
        m.put("priceYuan", pack.getPriceYuan());
        m.put("points", pack.getPoints());
        m.put("bonusPoints", pack.getBonusPoints());
        m.put("totalPoints", pack.totalPoints());
        m.put("enabled", pack.isEnabled());
        m.put("sortOrder", pack.getSortOrder());
        m.put("description", pack.getDescription());
        return m;
    }

    private Map<String, Object> orderView(PaymentOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("userId", o.getUserId());
        m.put("productType", o.getProductType());
        m.put("productId", o.getProductId());
        m.put("amountYuan", o.getAmountYuan());
        m.put("paymentMethod", o.getPaymentMethod());
        m.put("status", o.getStatus());
        m.put("transactionId", o.getTransactionId());
        m.put("metaJson", o.getMetaJson());
        m.put("paidAt", o.getPaidAt() != null ? o.getPaidAt().format(ISO) : null);
        m.put("createTime", o.getCreateTime() != null ? o.getCreateTime().format(ISO) : null);
        return m;
    }

    private Map<String, Object> ledgerView(PointLedger l) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", l.getId());
        m.put("delta", l.getDelta());
        m.put("balanceAfter", l.getBalanceAfter());
        m.put("reason", l.getReason());
        m.put("remark", l.getRemark());
        m.put("sessionId", l.getSessionId());
        m.put("createTime", l.getCreateTime() != null ? l.getCreateTime().format(ISO) : null);
        return m;
    }

    public record PackageBody(
            String name,
            BigDecimal priceYuan,
            Long points,
            Long bonusPoints,
            Boolean enabled,
            Integer sortOrder,
            String description
    ) {}

    public record AdjustBody(long delta, String remark) {}
}
