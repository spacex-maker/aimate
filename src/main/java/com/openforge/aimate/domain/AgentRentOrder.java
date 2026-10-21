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
@Table(name = "agent_rent_orders")
public class AgentRentOrder extends BaseEntity {

    public enum OrderType {
        HIRE,
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

    @Column(name = "catalog_id")
    private Long catalogId;

    @Column(name = "user_agent_id")
    private Long userAgentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private OrderType orderType = OrderType.HIRE;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RentPeriod period;

    @Column(name = "agent_points", nullable = false)
    private long agentPoints;

    @Column(name = "soul_points", nullable = false)
    private long soulPoints;

    @Column(name = "total_points", nullable = false)
    private long totalPoints;

    @Column(name = "soul_seat_id")
    private Long soulSeatId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.PAID;

    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;
}