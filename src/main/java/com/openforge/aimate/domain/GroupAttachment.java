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
@Table(name = "group_attachments")
public class GroupAttachment extends BaseEntity {

    public enum Kind {
        IMAGE,
        VIDEO,
        FILE,
        LINK,
        AUDIO
    }

    @Column(name = "message_id", nullable = false)
    private Long messageId;

    @Column(name = "group_id", nullable = false)
    private Long groupId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Kind kind;

    @Column(nullable = false, length = 1024)
    private String url;

    @Column(length = 128)
    private String mime;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(length = 256)
    private String title;

    @Column(name = "meta_json", columnDefinition = "TEXT")
    private String metaJson;
}
