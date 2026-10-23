package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "office_plans")
public class OfficePlan extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    /** 面向用户的「面积」叙事（㎡） */
    @Column(name = "area_sqm", nullable = false)
    private int areaSqm;

    @Column(name = "instance_type", nullable = false, length = 64)
    private String instanceType;

    @Column(name = "cpu_desc", length = 64)
    private String cpuDesc;

    @Column(name = "mem_desc", length = 64)
    private String memDesc;

    @Column(name = "disk_gb", nullable = false)
    private int diskGb = 50;

    @Column(name = "price_day_points", nullable = false)
    private long priceDayPoints;

    @Column(name = "price_month_points", nullable = false)
    private long priceMonthPoints;

    @Column(name = "price_year_points", nullable = false)
    private long priceYearPoints;

    @Column(length = 512)
    private String description;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;
}
