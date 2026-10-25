-- 官方工具库写入 agent_tools（user_id NULL）。执行逻辑仍在 Java（executeTool 分支）。
-- 需先执行 migration_dedupe_official_agent_tools.sql，使 (tool_name, user_scope_key) 唯一后本 seed 的 ON DUPLICATE 才可靠。
-- 办公室工具不入本表，由 AgentLoop 按开通状态注入。

INSERT INTO agent_tools (tool_name, tool_description, input_schema, tool_type, script_content, entry_point, is_active, is_system, version, create_time, update_time)
VALUES
('recall_memory',
 '按自然语言检索长期记忆（用户画像、偏好、过往事实等）。需要回忆此前存过的信息时调用。',
 '{"type":"object","properties":{"query":{"type":"string","description":"Natural language query for what to recall (e.g. 用户的名字, user name, 用户偏好)."},"top_k":{"type":"integer","minimum":1,"maximum":20,"description":"Max number of memories to return (default 10)."}},"required":["query"]}',
 'JAVA_NATIVE', NULL, 'recall_memory', 1, 1, 0, NOW(), NOW()),
('store_memory',
 '把重要、跨会话仍有用的事实写入长期记忆。仅对真正长期有用的信息调用，勿滥用。',
 '{"type":"object","properties":{"content":{"type":"string","description":"A stable, long-term fact (third-person sentence)."},"memory_type":{"type":"string","enum":["EPISODIC","SEMANTIC","PROCEDURAL"]},"importance":{"type":"number","minimum":0,"maximum":1}},"required":["content"]}',
 'JAVA_NATIVE', NULL, 'store_memory', 1, 1, 0, NOW(), NOW()),
('find_tools',
 '按自然语言检索官方工具库与 Agent 工具箱（词法精确 + 向量语义，带相关度分数）。工具很多或不确定该调哪个时先调用；只使用返回列表中的工具名。',
 '{"type":"object","properties":{"query":{"type":"string","description":"需要的能力描述，如 查天气、HTTP 请求、安装 JDK"},"top_k":{"type":"integer","minimum":1,"maximum":20}},"required":["query"]}',
 'JAVA_NATIVE', NULL, 'find_tools', 1, 1, 0, NOW(), NOW()),
('tavily_search',
 '通过 Tavily 检索实时/事实信息（新闻、时事等）。用户自身已存信息请优先用 recall_memory。',
 '{"type":"object","properties":{"query":{"type":"string","description":"Search query in natural language."},"max_results":{"type":"integer","minimum":1,"maximum":20},"search_depth":{"type":"string","enum":["basic","advanced","fast","ultra-fast"]},"topic":{"type":"string","enum":["general","news","finance"]}},"required":["query"]}',
 'JAVA_NATIVE', NULL, 'tavily_search', 1, 1, 0, NOW(), NOW()),
('http_request',
 '由服务端发起 HTTP 请求（无需办公室）。适合 REST/健康检查/Soul 同步；禁止访问内网与 localhost。',
 '{"type":"object","properties":{"method":{"type":"string","enum":["GET","POST","PUT","PATCH","DELETE","HEAD"]},"url":{"type":"string"},"headers":{"type":"object"},"body":{"type":"string"},"timeout_seconds":{"type":"integer","minimum":5,"maximum":60}},"required":["url"]}',
 'JAVA_NATIVE', NULL, 'http_request', 1, 1, 0, NOW(), NOW()),
('create_tool',
 '官方元工具：将简单脚本沉淀到 Agent 工具箱（如查天气 API），由平台保存并执行，无需办公室。装 JDK 等重活请用办公室工具。',
 '{"type":"object","properties":{"tool_name":{"type":"string"},"tool_description":{"type":"string"},"input_schema":{"type":"string"},"tool_type":{"type":"string","enum":["PYTHON_SCRIPT","NODE_SCRIPT","SHELL_CMD"]},"script_content":{"type":"string"},"entry_point":{"type":"string"}},"required":["tool_name","tool_description","input_schema","tool_type"]}',
 'JAVA_NATIVE', NULL, 'create_tool', 1, 1, 0, NOW(), NOW())
ON DUPLICATE KEY UPDATE
  tool_description = VALUES(tool_description),
  input_schema = VALUES(input_schema),
  is_system = VALUES(is_system),
  is_active = 1,
  update_time = NOW();
