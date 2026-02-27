package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * System user — used for login, ownership and future RBAC.
 *
 * All audit fields (id, create_time, update_time, version) come from BaseEntity.
 */
@Getter
@Setter
@Entity
@Table(name = "users")
public class User extends BaseEntity {

    @Column(nullable = false, length = 64, unique = true)
    private String username;

    @Column(nullable = true, length = 128, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    @Column(name = "last_login_time")
    private LocalDateTime lastLoginTime;

    public enum Status {
        ACTIVE,
        DISABLED
    }
}

