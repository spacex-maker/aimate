package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "user_profiles")
public class UserProfile extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(length = 64)
    private String occupation;

    @Column
    private Integer age;

    /** MALE / FEMALE / OTHER / UNSPECIFIED */
    @Column(length = 32)
    private String gender;

    @Column(length = 512)
    private String bio;

    @Column(name = "extra_json", columnDefinition = "TEXT")
    private String extraJson;

    /** 画像是否足够生成秘书 */
    @Column(name = "completed", nullable = false)
    private boolean completed = false;
}
