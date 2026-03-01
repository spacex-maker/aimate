-- 将内置工具写入 agent_tools，使该表成为工具唯一来源；执行逻辑仍在 Java 中（executeTool 分支）。
-- 首次部署或空表时执行本脚本。未执行时 Agent 会退回到代码中的内置三件套，但工具索引与统一管理以表为准。

INSERT INTO agent_tools (tool_name, tool_description, input_schema, tool_type, script_content, entry_point, is_active, is_system, version, create_time, update_time)
VALUES
('recall_memory',
 'Search long-term memory by natural language query. Returns relevant past information (e.g. user profile, name, preferences). Use when you need to look up something that may have been stored before.',
 '{"type":"object","properties":{"query":{"type":"string","description":"Natural language query for what to recall (e.g. 用户的名字, user name, 用户偏好)."},"top_k":{"type":"integer","minimum":1,"maximum":20,"description":"Max number of memories to return (default 10)."}},"required":["query"]}',
 'JAVA_NATIVE', NULL, 'recall_memory', 1, 1, 0, NOW(), NOW()),
('store_memory',
 'Store an IMPORTANT, long-term piece of information into memory for future sessions. Use sparingly. Only call this for facts that will matter across many tasks.',
 '{"type":"object","properties":{"content":{"type":"string","description":"A stable, long-term fact (third-person sentence)."},"memory_type":{"type":"string","enum":["EPISODIC","SEMANTIC","PROCEDURAL"]},"importance":{"type":"number","minimum":0,"maximum":1}},"required":["content"]}',
 'JAVA_NATIVE', NULL, 'store_memory', 1, 1, 0, NOW(), NOW()),
('tavily_search',
 'Search the web for real-time or factual information via Tavily. Use when the user asks about current events, recent news, or facts you are unsure about. Prefer recall_memory for the user''s own stored information.',
 '{"type":"object","properties":{"query":{"type":"string","description":"Search query in natural language."},"max_results":{"type":"integer","minimum":1,"maximum":20},"search_depth":{"type":"string","enum":["basic","advanced","fast","ultra-fast"]},"topic":{"type":"string","enum":["general","news","finance"]}},"required":["query"]}',
 'JAVA_NATIVE', NULL, 'tavily_search', 1, 1, 0, NOW(), NOW()),
('install_container_package',
 'Install system packages (e.g. python3, nodejs) in the user''s isolated Linux container. Call before running script tools that need an interpreter; the container is minimal and does not include python3/node by default.',
 '{"type":"object","properties":{"packages":{"type":"string","description":"Space- or comma-separated apt package names, e.g. python3 or python3 nodejs."}},"required":["packages"]}',
 'JAVA_NATIVE', NULL, 'install_container_package', 1, 1, 0, NOW(), NOW()),
('run_container_cmd',
 'Run an arbitrary shell command in the user''s isolated Linux container. Use for setup that install_container_package cannot do: install JDK, add apt repos, run scripts. Pass command (e.g. apt-get update && apt-get install -y openjdk-17-jdk). Timeout configurable via run-container-cmd-timeout-seconds (default 300s).',
 '{"type":"object","properties":{"command":{"type":"string"},"commands":{"type":"array","items":{"type":"string"}}}',
 'JAVA_NATIVE', NULL, 'run_container_cmd', 1, 1, 0, NOW(), NOW()),
('write_container_file',
 'Write content to a file in the user''s container via stdin; no shell escaping. Use for scripts with quotes, $, etc. Pass path and content; optional append for chunked writes.',
 '{"type":"object","properties":{"path":{"type":"string"},"content":{"type":"string"},"append":{"type":"boolean"}},"required":["path","content"]}',
 'JAVA_NATIVE', NULL, 'write_container_file', 1, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  tool_description = VALUES(tool_description),
  input_schema = VALUES(input_schema),
  is_system = VALUES(is_system),
  update_time = NOW();
