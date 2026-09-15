# AIMate Desktop

Electron + React 桌面 Agent 客户端（OpenClaw / WorkBuddy 风格架构）。

## 开发

```bash
cd desktop
npm install
npm run dev
```

## 打包 Windows exe

```bash
npm run dist:win
```

产出位于 `desktop/release/`。

## 架构

- **Renderer**：React UI（登录 / 项目 / 会话 / 记忆 / Skills / 设置）
- **Main + Preload**：Host API（`hostapi:invoke` / `hostapi:event`）
- **AgentHost**：本地 RECALL→THINK→ACT 循环、JSONL 会话、OpenClaw 兼容 Skills、本地记忆
- **Cloud**：Spring `/api/auth`、`/api/proxy/chat/completions`、`/api/proxy/embeddings`
