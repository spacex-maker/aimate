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
@Table(name = "kb_knowledge_bases")
public class KnowledgeBase extends BaseEntity {

    /** 对外稳定 ID，招聘/关联用 */
    @Column(name = "public_id", nullable = false, length = 40, unique = true)
    private String publicId;

    @Column(name = "category_id", nullable = false)
    private Long categoryId;

    @Column(length = 64, unique = true)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(length = 1024)
    private String summary;

    @Lob
    @Column(columnDefinition = "MEDIUMTEXT")
    private String content;

    @Column(length = 512)
    private String tags;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 100;

    @Column(nullable = false)
    private boolean enabled = true;
}
