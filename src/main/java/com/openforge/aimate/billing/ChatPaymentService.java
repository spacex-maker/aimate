package com.openforge.aimate.billing;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openforge.aimate.config.SystemConfigService;
import com.openforge.aimate.domain.PaymentOrder;
import com.openforge.aimate.domain.PointLedger;
import com.openforge.aimate.domain.PointPackage;
import com.openforge.aimate.repository.PaymentOrderRepository;
import com.openforge.aimate.repository.PointPackageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class ChatPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ChatPaymentService.class);
    private static final String STATUS_PAID = "OD";

    private final PaymentOrderRepository orderRepository;
    private final PointPackageRepository packageRepository;
    private final PointsService pointsService;
    private final SystemConfigService systemConfigService;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate = new RestTemplate();

    public ChatPaymentService(
            PaymentOrderRepository orderRepository,
            PointPackageRepository packageRepository,
            PointsService pointsService,
            SystemConfigService systemConfigService,
            ObjectMapper objectMapper
    ) {
        this.orderRepository = orderRepository;
        this.packageRepository = packageRepository;
        this.pointsService = pointsService;
        this.systemConfigService = systemConfigService;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentOrder createPointPackOrder(Long userId, long packageId) {
        PointPackage pack = packageRepository.findById(packageId)
                .orElseThrow(() -> new IllegalArgumentException("套餐不存在"));
        if (!pack.isEnabled()) {
            throw new IllegalStateException("该套餐已下架");
        }
        PaymentOrder order = new PaymentOrder();
        order.setOrderNo("CHAT" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        order.setUserId(userId);
        order.setProductType(PaymentOrder.PRODUCT_POINT_PACK);
        order.setProductId(pack.getId());
        order.setAmountYuan(pack.getPriceYuan());
        order.setPaymentMethod(PaymentOrder.METHOD_XUNHU);
        order.setStatus(PaymentOrder.STATUS_PENDING);
        order.setMetaJson("{\"packageName\":\"" + pack.getName().replace("\"", "") + "\",\"points\":"
                + pack.getPoints() + ",\"bonus\":" + pack.getBonusPoints() + "}");
        return orderRepository.save(order);
    }

    @Transactional(readOnly = true)
    public WechatCheckoutView checkout(Long userId, String orderNo) {
        PaymentOrder order = orderRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("订单不存在"));
        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("订单不存在");
        }
        if (!PaymentOrder.STATUS_PENDING.equals(order.getStatus())) {
            throw new IllegalStateException("订单状态不正确");
        }

        String appId = cfg("XUNHU_APPID");
        String secret = cfg("XUNHU_SECRET");
        if (!StringUtils.hasText(secret)) secret = cfg("XUNHU_APPSECRET");
        String gatewayUrl = cfg("XUNHU_GATEWAY_URL");
        String notifyUrl = cfg("XUNHU_NOTIFY_URL");
        String returnUrl = cfg("XUNHU_RETURN_URL");
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(secret) || !StringUtils.hasText(gatewayUrl)) {
            throw new IllegalStateException("微信支付未配置：请在系统配置填写 XUNHU_APPID / XUNHU_SECRET / XUNHU_GATEWAY_URL");
        }
        if (!StringUtils.hasText(notifyUrl)) {
            throw new IllegalStateException("请配置 XUNHU_NOTIFY_URL");
        }

        String nonceStr = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long time = System.currentTimeMillis() / 1000;
        String totalFee = order.getAmountYuan().stripTrailingZeros().toPlainString();

        Map<String, String> params = new TreeMap<>();
        params.put("version", "1.1");
        params.put("appid", appId);
        params.put("trade_order_id", order.getOrderNo());
        params.put("total_fee", totalFee);
        params.put("title", "AIMATEX-Chat积分充值");
        params.put("time", String.valueOf(time));
        params.put("notify_url", notifyUrl);
        if (StringUtils.hasText(returnUrl)) {
            params.put("return_url", returnUrl);
        }
        params.put("nonce_str", nonceStr);
        params.put("hash", generateXunhuHash(params, secret));

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach(form::add);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    gatewayUrl, new HttpEntity<>(form, headers), String.class);
            String body = response.getBody();
            if (body == null) throw new IllegalStateException("微信支付接口无响应");
            JsonNode node = objectMapper.readTree(body);
            int errcode = node.has("errcode") ? node.get("errcode").asInt() : -1;
            if (errcode != 0) {
                String errmsg = node.has("errmsg") ? node.get("errmsg").asText() : "请求失败";
                throw new IllegalStateException("微信支付发起失败: " + errmsg);
            }
            return new WechatCheckoutView(
                    node.has("url") ? node.get("url").asText() : null,
                    node.has("url_qrcode") ? node.get("url_qrcode").asText() : null
            );
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            log.error("微信支付发起异常", e);
            throw new IllegalStateException("微信支付发起失败: " + e.getMessage());
        }
    }

    @Transactional
    public void handleWechatNotify(Map<String, String> params) {
        String orderNo = params.get("trade_order_id");
        String status = params.get("status");
        String receivedHash = params.get("hash");
        if (!StringUtils.hasText(orderNo)) return;

        String secret = cfg("XUNHU_SECRET");
        if (!StringUtils.hasText(secret)) secret = cfg("XUNHU_APPSECRET");
        if (StringUtils.hasText(secret) && StringUtils.hasText(receivedHash)) {
            String computed = generateXunhuHash(new TreeMap<>(params), secret);
            if (!computed.equalsIgnoreCase(receivedHash)) {
                log.warn("虎皮椒回调验签失败 orderNo={}", orderNo);
                return;
            }
        }
        if (!STATUS_PAID.equals(status)) return;

        PaymentOrder order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null) return;
        if (PaymentOrder.STATUS_SUCCESS.equals(order.getStatus())) return;

        order.setStatus(PaymentOrder.STATUS_SUCCESS);
        order.setTransactionId(params.getOrDefault("transaction_id", params.get("open_order_id")));
        order.setPaidAt(LocalDateTime.now());
        orderRepository.save(order);
        fulfill(order);
    }

    private void fulfill(PaymentOrder order) {
        if (!PaymentOrder.PRODUCT_POINT_PACK.equals(order.getProductType())) return;
        PointPackage pack = packageRepository.findById(order.getProductId()).orElse(null);
        long pts = pack != null ? pack.totalPoints() : 0L;
        if (pts <= 0) return;
        pointsService.credit(
                order.getUserId(),
                pts,
                PointLedger.RECHARGE,
                "ORDER",
                order.getOrderNo(),
                "充值套餐入账"
        );
    }

    private String cfg(String key) {
        return systemConfigService.get(key).orElse("");
    }

    public static String generateXunhuHash(Map<String, String> params, String secret) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : params.entrySet()) {
            if ("hash".equals(e.getKey()) || e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        sb.append(secret);
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception ex) {
            throw new RuntimeException("MD5 failed", ex);
        }
    }

    public record WechatCheckoutView(String url, String urlQrcode) {}
}
