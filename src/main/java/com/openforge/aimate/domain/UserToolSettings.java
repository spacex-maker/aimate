package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * 用户级系统工具开关：长期记忆、联网搜索、AI 自主编写工具、用户系统脚本执行。
 * 每个用户一条记录，未设置时默认全部开启。
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "user_tool_settings",
    uniqueConstraints = @UniqueConstraint(name = "uq_user_tool_settings_user_id", columnNames = "user_id")
)
public class UserToolSettings extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    /** 长期记忆：recall_memory / store_memory */
    @Builder.Default
    @Column(name = "memory_enabled", nullable = false)
    private Boolean memoryEnabled = true;

    /** 联网搜索：tavily_search */
    @Builder.Default
    @Column(name = "web_search_enabled", nullable = false)
    private Boolean webSearchEnabled = true;

    /** AI 自主编写工具：create_tool */
    @Builder.Default
    @Column(name = "create_tool_enabled", nullable = false)
    private Boolean createToolEnabled = true;

    /** 用户系统脚本执行：install_container_package / run_container_cmd / write_container_file */
    @Builder.Default
    @Column(name = "script_exec_enabled", nullable = false)
    private Boolean scriptExecEnabled = true;
}
