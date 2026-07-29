# DevNexus Console

DevNexus AI 的 RAG 聊天与管理端，基于 React 18、TypeScript、Vite、Zustand、Radix UI 和 Tailwind CSS。

## 页面职责

- RAG 流式问答、引用展示和生成取消；
- 知识库、文档、Chunk 与摄取任务管理；
- Trace、意图树、样例问题和系统配置管理；
- 管理入口与社区普通用户入口分离。

前端隐藏菜单不构成权限控制，知识库写操作和系统配置仍必须由后端强制鉴权。

## 本地运行

要求 Node.js 20+。Ragent 默认 API 前缀示例为 `http://localhost:9090/api/ragent`：

```bash
cp .env.example .env
npm ci
npm run dev
```

`.env` 示例：

```dotenv
VITE_API_BASE_URL=http://localhost:9090/api/ragent
VITE_APP_NAME=DevNexus Console
VITE_COMMUNITY_URL=http://localhost:5174
```

所有 `VITE_*` 变量都会公开到浏览器，禁止填写模型密钥、数据库密码或服务端 Token。

## 质量检查

```bash
npm run type-check
npm run lint
npm test
npm run build
```

当前测试覆盖 SSE 分片解析、重试和取消边界，以及文档启停、分块、重建和批量操作等管理 API 契约。

## 边界

- 页面采用路由级懒加载，Markdown 高亮只注册常用语言；
- Chat Store 负责会话/流状态，SSE 请求构造集中在 `chatService`；
- 客户端取消等待 3 秒仍未收到服务端确认时会主动中断连接并收敛 UI 状态；
- `.env`、日志、构建产物和真实凭据不得提交。
