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
@Table(name = "chat_groups")
public class ChatGroup extends BaseEntity {

    public enum Status {
        ACTIVE,
        DISSOLVED
    }

    @Column(name = "public_id", nullable = false, length = 64, unique = true)
    private String publicId;

    @Column(nullable = false, length = 128)
    private String title;

    @Column(name = "avatar_url", length = 512)
    private String avatarUrl;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status = Status.ACTIVE;

    /** 群公告 */
    @Column(columnDefinition = "TEXT")
    private String announcement;

    /** 扫码入群邀请码 */
    @Column(name = "invite_code", length = 32, unique = true)
    private String inviteCode;

    /** 是否允许普通成员邀请用户/Agent；群主与管理员始终可邀请 */
    @Column(name = "members_invite_enabled", nullable = false)
    private boolean membersInviteEnabled = true;
}
