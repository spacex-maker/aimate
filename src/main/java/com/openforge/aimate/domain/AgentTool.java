package com.openforge.aimate.domain;

import jakarta.persistence.*;
import lombok.*;

/**
 * Represents one tool that the Agent can invoke.
 *
 * There are two "flavours":
 *
 *  JAVA_NATIVE   — implemented in Java; scriptContent is null;
 *                  entryPoint holds the fully-qualified bean name.
 *
 *  PYTHON_SCRIPT / NODE_SCRIPT / SHELL_CMD
 *                — "right-brain" generated tools; scriptContent holds
 *                  the raw source; entryPoint is the temp filename
 *                  used at execution time.
 *
 * The inputSchema column stores a JSON Schema object that is injected
 * verbatim into the LLM's "tools" array, so the model knows exactly
 * what parameters to pass.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
    name = "agent_tools",
    uniqueConstraints = @UniqueConstraint(name = "uq_tool_name", columnNames = "tool_name")
)
public class AgentTool extends BaseEntity {

    public enum ToolType {
        JAVA_NATIVE,
        PYTHON_SCRIPT,
        NODE_SCRIPT,
        SHELL_CMD
    }

    /** Unique machine-readable identifier, used as the function name in LLM calls. */
    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    /** Natural-language description sent to the LLM so it knows when to call this tool. */
    @Column(name = "tool_description", nullable = false, columnDefinition = "TEXT")
    private String toolDescription;

    /**
     * JSON Schema object describing the tool's parameters.
     * Example:
     * {
     *   "type": "object",
     *   "properties": { "query": { "type": "string" } },
     *   "required": ["query"]
     * }
     */
    @Column(name = "input_schema", nullable = false, columnDefinition = "TEXT")
    private String inputSchema;

    @Enumerated(EnumType.STRING)
    @Column(name = "tool_type", nullable = false, length = 32)
    private ToolType toolType;

    /**
     * Raw script source (Python / Node.js / Shell).
     * Null for JAVA_NATIVE tools.
     */
    @Column(name = "script_content", columnDefinition = "LONGTEXT")
    private String scriptContent;

    /**
     * For JAVA_NATIVE  : Spring bean name, e.g. "webSearchTool"
     * For script types : suggested filename, e.g. "search_web.py"
     */
    @Column(name = "entry_point", length = 256)
    private String entryPoint;

    @Builder.Default
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
}
