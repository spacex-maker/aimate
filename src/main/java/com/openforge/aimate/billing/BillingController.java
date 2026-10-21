package com.openforge.aimate.billing;

import com.openforge.aimate.domain.PaymentOrder;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.PointPackage;
import com.openforge.aimate.repository.PaymentOrderRepository;
import com.openforge.aimate.repository.PointLedgerRepository;
import com.openforge.aimate.repository.PointPackageRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class BillingController {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final PointsService pointsService;
    private final ChatPaymentService paymentService;
    private final PointPackageRepository packageRepository;
    private final PaymentOrderRepository orderRepository;
    private final PointLedgerRepository ledgerRepository;

    public BillingController(
            PointsService pointsService,
            ChatPaymentService paymentService,
            PointPackageRepository packageRepository,
            PaymentOrderRepository orderRepository,
            PointLedgerRepository ledgerRepository
    ) {
        this.pointsService = pointsService;
        this.paymentService = paymentService;
        this.packageRepository = packageRepository;
        this.orderRepository = orderRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @GetMapping("/billing/me")
    public Map<String, Object> me() {
        Long userId = requireUserId();
        return Map.of("pointsBalance", pointsService.balance(userId));
    }

    @GetMapping("/billing/packages")
    public List<Map<String, Object>> packages() {
        requireUserId();
        return packageRepository.findByEnabledTrueOrderBySortOrderAsc().stream()
                .map(this::packageView)
                .toList();
    }

    @PostMapping("/billing/orders/point-pack")
    public Map<String, Object> createPointPackOrder(@RequestBody IdBody body) {
        Long userId = requireUserId();
        PaymentOrder order = paymentService.createPointPackOrder(userId, body.id());
        return orderView(order);
    }

    @PostMapping("/billing/wechat/checkout")
    public ChatPaymentService.WechatCheckoutView checkout(@RequestBody CheckoutBody body) {
        Long userId = requireUserId();
        return paymentService.checkout(userId, body.orderNo());
    }

    @GetMapping("/billing/orders")
    public Map<String, Object> myOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = requireUserId();
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<PaymentOrder> result = orderRepository.findByUserIdOrderByCreateTimeDesc(userId, PageRequest.of(p, s));
        return pageOf(result.map(this::orderView), p, s);
    }

    @GetMapping("/billing/ledger")
    public Map<String, Object> myLedger(
            @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Long userId = requireUserId();
        int p = Math.max(0, page);
        int s = Math.min(100, Math.max(1, size));
        Page<PointLedger> result;
        if (reason != null && !reason.isBlank()) {
            result = ledgerRepository.findByUserIdAndReasonOrderByCreateTimeDesc(
                    userId, reason.trim().toUpperCase(), PageRequest.of(p, s));
        } else {
            result = ledgerRepository.findByUserIdOrderByCreateTimeDesc(userId, PageRequest.of(p, s));
        }
        return pageOf(result.map(this::ledgerView), p, s);
    }

    /** 虎皮椒异步回调（公开） */
    @PostMapping(value = "/pay/wechat/notify", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ResponseEntity<String> wechatNotify(@RequestParam Map<String, String> params) {
        try {
            paymentService.handleWechatNotify(params);
        } catch (Exception ignored) {
        }
        return ResponseEntity.ok("success");
    }

    private Map<String, Object> packageView(PointPackage pack) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", pack.getId());
        m.put("name", pack.getName());
        m.put("priceYuan", pack.getPriceYuan());
        m.put("points", pack.getPoints());
        m.put("bonusPoints", pack.getBonusPoints());
        m.put("totalPoints", pack.totalPoints());
        m.put("description", pack.getDescription());
        m.put("sortOrder", pack.getSortOrder());
        return m;
    }

    private Map<String, Object> orderView(PaymentOrder o) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
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

    private static Map<String, Object> pageOf(Page<?> page, int p, int s) {
        Map<String, Object> m = new HashMap<>();
        m.put("items", page.getContent());
        m.put("page", p);
        m.put("size", s);
        m.put("total", page.getTotalElements());
        m.put("totalPages", page.getTotalPages());
        return m;
    }

    private Long requireUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Long id) {
            return id;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "请先登录");
    }

    public record IdBody(long id) {}
    public record CheckoutBody(String orderNo) {}
}
