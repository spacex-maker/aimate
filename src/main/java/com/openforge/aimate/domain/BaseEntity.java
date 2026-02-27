package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Canonical audit columns shared by every business table.
 *
 * - create_time  : set once on INSERT, never touched again
 * - update_time  : refreshed on every UPDATE  (乐观锁时间戳凭证)
 * - version      : JPA @Version — acts as the hard optimistic-lock counter
 *                  for blue-green hot-swap scenarios
 */
@Getter
@Setter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @CreatedDate
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @LastModifiedDate
    @Column(name = "update_time", nullable = false)
    private LocalDateTime updateTime;

    /**
     * Hard optimistic-lock counter.
     * JPA throws OptimisticLockException when two concurrent writers
     * try to flush the same row — critical during blue-green cut-over.
     */
    @Version
    @Column(nullable = false)
    private Integer version = 0;
}
