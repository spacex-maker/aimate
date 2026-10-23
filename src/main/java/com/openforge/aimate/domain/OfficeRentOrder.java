package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "office_rent_orders")
public class OfficeRentOrder extends BaseEntity {

    public enum OrderType {
        RENT,
        RENEW
    }

    public enum Status {
        PAID,
        FAILED
    }

    @Column(name = "order_no", nullable = false, length = 64, unique = true)
    private String orderNo;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "plan_id", nullable = false)
    private Long planId;

    @Column(name = "user_office_id")
    private Long userOfficeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderType orderType = OrderType.RENT;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RentPeriod period;

    @Column(name = "total_points", nullable = false)
    private long totalPoints;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PAID;

    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;
}
