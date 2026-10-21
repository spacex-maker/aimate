package com.openforge.aimate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "agent_prompt_versions", uniqueConstraints = {
        @UniqueConstraint(name = "uk_agent_prompt_ver", columnNames = {"user_agent_id", "version_no"})
})
public class AgentPromptVersion extends BaseEntity {

    @Column(name = "user_agent_id", nullable = false)
    private Long userAgentId;

    @Column(name = "version_no", nullable = false)
    private int versionNo;

    /** 合成后的完整 system prompt（人设段） */
    @Column(name = "prompt_text", nullable = false, columnDefinition = "LONGTEXT")
    private String promptText;

    @Column(name = "change_summary", length = 1024)
    private String changeSummary;

    /** 原子更新条目 JSON 数组：[{id,text,excluded}] */
    @Column(name = "atoms_json", columnDefinition = "LONGTEXT")
    private String atomsJson;

    @Column(name = "active", nullable = false)
    private boolean active = false;
}
