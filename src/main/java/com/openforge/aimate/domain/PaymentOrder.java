package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "payment_orders")
public class PaymentOrder extends BaseEntity {

    public static final String PRODUCT_POINT_PACK = "POINT_PACK";
    /** 管理员系统赠送（自定义积分数，不关联套餐） */
    public static final String PRODUCT_SYSTEM_GIFT = "SYSTEM_GIFT";
    public static final String METHOD_XUNHU = "wechat_pay_xunhu";
    /** 系统赠送入账，无需真实支付 */
    public static final String METHOD_SYSTEM_GIFT = "system_gift";
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_CLOSED = "CLOSED";

    @Column(name = "order_no", nullable = false, unique = true, length = 64)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "product_type", nullable = false, length = 32)
    private String productType = PRODUCT_POINT_PACK;

    @Column(name = "product_id", nullable = false)
    private long productId;

    @Column(name = "amount_yuan", nullable = false, precision = 12, scale = 2)
    private BigDecimal amountYuan;

    @Column(name = "payment_method", nullable = false, length = 64)
    private String paymentMethod = METHOD_XUNHU;

    @Column(nullable = false, length = 32)
    private String status = STATUS_PENDING;

    @Column(name = "transaction_id", length = 128)
    private String transactionId;

    @Column(name = "meta_json", columnDefinition = "LONGTEXT")
    private String metaJson;

    @Column(name = "paid_at")
    private LocalDateTime paidAt;
}
