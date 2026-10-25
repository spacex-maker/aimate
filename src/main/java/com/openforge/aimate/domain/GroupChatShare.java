package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "group_chat_shares")
public class GroupChatShare extends BaseEntity {

    /** 公开链接后缀：随机字母 */
    @Column(name = "share_code", nullable = false, length = 32, unique = true)
    private String shareCode;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Column(name = "group_public_id", nullable = false, length = 64)
    private String groupPublicId;

    @Column(name = "group_title", length = 256)
    private String groupTitle;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "message_count", nullable = false)
    private Integer messageCount = 0;

    @Lob
    @Column(name = "payload_json", nullable = false, columnDefinition = "LONGTEXT")
    private String payloadJson;
}
