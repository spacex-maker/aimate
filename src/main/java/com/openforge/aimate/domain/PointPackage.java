package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Entity
@Table(name = "point_packages")
public class PointPackage extends BaseEntity {

    @Column(nullable = false, length = 128, unique = true)
    private String name;

    @Column(name = "price_yuan", nullable = false, precision = 12, scale = 2)
    private BigDecimal priceYuan;

    @Column(nullable = false)
    private long points;

    @Column(name = "bonus_points", nullable = false)
    private long bonusPoints = 0L;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(length = 256)
    private String description;

    public long totalPoints() {
        return points + bonusPoints;
    }
}
