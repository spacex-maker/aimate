# OpenForgeX AIMate：自主 AI Agent 平台，实时看它「怎么想」

## 一、写在前面

你是否想过：让 AI 不只给你一段答案，而是**像人一样先回忆、再思考、再决定要不要动手**？  
**[OpenForgeX AIMate](https://agent.aimatex.com)** 就是这样一个**自主 AI Agent 平台**：后端驱动完整的「回忆 → 思考 → 决策 → 执行」循环，前端用 WebSocket **实时展示** Agent 的推理过程——你不仅能拿到最终结果，还能看到它调用了哪些记忆、想了什么、用了什么工具。

**公网体验地址：<https://agent.aimatex.com>**

---

## 二、项目是什么？

**AIMate**（OpenForgeX 下的子项目）定位很清晰：

- **后端**：驱动 Agent 的「思考循环」，对接大模型、长期记忆、工具执行。
- **前端**：会话式界面 + **实时思考流**，把 RECALL / THINK / TOOL_CALL / 最终答案 都推到你眼前。

一句话：**自主 Agent + 可观测**——既能把复杂任务拆成多步执行，又能看清楚每一步在干什么。

---

## 三、核心能力一览

### 1. Agent 思考循环（RECALL → THINK → DECIDE → ACT）

每次迭代大致是：

1. **RECALL**：从向量库（Milvus）召回与当前问题相关的**长期记忆**，注入上下文。
2. **THINK**：流式调用 LLM，生成回复或 **tool_call**；前端通过 WebSocket 实时收到 token 与事件。
3. **DECIDE**：若有工具调用则进入 ACT，否则视为完成（DONE）。
4. **ACT**：执行工具（搜索、记忆读写、用户容器内命令等），把结果追加回上下文。
5. **PERSIST**：把当前上下文窗口落库；超过最大迭代次数则终止。

这样 Agent 可以**主动用记忆、主动用工具**，而不是单轮问答。

### 2. 长期记忆（向量存储）

- 记忆写入 **Milvus**，按用户 + 集合隔离。
- 支持**用户自定义 Embedding 模型**（如 BGE、OpenAI 等），维度可配置。
- 会话中通过工具 **store_memory / recall_memory** 写入与召回，实现「跨会话记住事情」。

### 3. 实时思考流（WebSocket）

- 前端订阅 `/topic/agent/{sessionId}`，收到事件类型包括：
  - `ITERATION_START` / `THINKING`（流式 token）/ `TOOL_CALL` / `TOOL_RESULT` / `FINAL_ANSWER` / `STATUS_CHANGE` / `ERROR`。
- 你可以在会话页**边看边等**：当前在回忆、在推理，还是在跑工具，一目了然。

### 4. 用户自带 LLM / Embedding

- **API 密钥**：支持用户配置自己的 LLM API Key（OpenAI、DeepSeek、通义、Kimi 等），优先于系统 key。
- **Embedding 模型**：可配自己的向量模型与维度，记忆检索按用户配置走。

方便在**同一套系统里**用不同厂商、不同模型，而不改部署。

### 5. 每用户一个 Linux 容器（脚本/工具执行）

- 用户脚本类工具（Python、Node、Shell 等）在**独立 Docker 容器**中执行，每用户一个，互不干扰。
- 支持在容器内 **install 包**（如 `python3`、`nodejs`）、**执行命令**、**写文件**，适合「先装环境再跑脚本」的 Agent 工作流。
- 容器启用 **no-new-privileges + cap-drop=ALL** 等安全选项，兼顾可用性与隔离。

### 6. 管理后台（管理员）

- 容器监控、宿主机资源、系统模型/配置、用户与权限等，方便运维与排障。
- 启动摘要中的**组件连接状态**（MySQL、Milvus、LLM、Embedding、Docker）在管理端可查看，便于确认环境是否就绪。

---

## 四、技术栈简表

| 层级     | 技术 |
|----------|------|
| 后端     | Spring Boot 3.5、Java 25（Virtual Threads、ScopedValue）、MySQL、Milvus |
| 安全     | Spring Security + JWT（Bearer） |
| 弹性     | Resilience4j（熔断、重试、限流） |
| 实时     | Spring WebSocket + STOMP |
| 前端     | React 18、TypeScript、Vite、TailwindCSS、TanStack Query v5、React Router v6 |

前后端 JSON 统一 **camelCase**，接口风格一致，便于扩展和对接。

---

## 五、适合谁用？

- **想体验「自主 Agent + 可观测」**：看 AI 如何用记忆、如何调工具、如何一步步给出答案。
- **需要长期记忆与多轮规划**：跨会话记忆、任务拆解与工具链。
- **希望自带 key、自选模型**：在同一平台上用不同 LLM/Embedding，少改代码。
- **有脚本/代码执行需求**：在隔离容器里装环境、跑脚本，并做基本安全加固。

---

## 六、如何体验？

- **公网访问（推荐）**：浏览器打开 **[https://agent.aimatex.com](https://agent.aimatex.com)**，注册/登录后即可创建会话，与 Agent 对话并观察实时思考流与工具调用。
- 若项目开源，可在 GitHub 搜索 **OpenForgeX / AIMate** 获取源码与本地部署说明。

---

## 七、小结

**OpenForgeX AIMate** 把「自主推理 + 长期记忆 + 工具执行 + 实时可观测」做进一套系统里，并用 **Spring Boot 3 / Java 25** 与 **React 18 / TypeScript** 实现前后端分离与实时推送。  
无论你是想**直接体验**多步推理与记忆，还是想**学习/二次开发** Agent 架构，都可以从 **[https://agent.aimatex.com](https://agent.aimatex.com)** 开始。

---

*本文基于当前系统能力整理，用于项目介绍与推广。公网域名：<https://agent.aimatex.com>。*
