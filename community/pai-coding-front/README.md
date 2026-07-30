# DevNexus AI Community

DevNexus AI 的社区主站与作品集入口，基于 Vue 3、TypeScript、Vite、Pinia 和 Element Plus。

## 页面职责

- 社区首页、文章、评论、通知和用户页面；
- 项目能力、验证指标与运行状态展示；
- 面向登录用户的受控 Agent 问答和文章引用；
- 只调用社区 Gateway，不在浏览器中保存 RAG 服务凭据。

社区 Agent 门面目前默认使用同步 HTTP。前端已经实现 POST SSE 解析、增量文本和协作取消
客户端，但只有后端真实流端点通过验证后才能启用：

```dotenv
VITE_AGENT_STREAM_ENABLED=false
```

关闭该开关时，“停止等待”只取消当前浏览器请求，不代表服务端任务已撤销。项目不会把
同步回答在浏览器端拆字后称为真实模型流式。

## 本地运行

要求 Node.js 20+。先复制公开配置：

```bash
cp .env.example .env
npm ci
npm run dev
```

开发服务器固定使用 `http://localhost:5174`，可与 `5173` 端口的 React 管理台同时运行。

服务化模式下，`VITE_API_BASE_URL` 应指向 Gateway，默认开发地址为：

```dotenv
VITE_API_BASE_URL=http://localhost:10010
VITE_WS_BASE_URL=ws://localhost:10010
```

如需直接连接社区单体，可将地址改为 `http://localhost:8081`。所有 `VITE_*` 变量都会进入浏览器产物，禁止填写 API Key、数据库密码或内部服务 Token。

## 质量检查

```bash
npm run type-check
npm run lint
npm test
npm run test:e2e
npm run build
```

Vitest 覆盖请求拦截器、登录守卫、Agent 成功/失败/取消、SSE 分帧和引用渲染。
Playwright 使用确定性 API Fixture 验证公开主流程和桌面、平板、手机布局；它不替代
真实 RAG/模型端到端证据。

## 目录

```text
src/
├── config/       运行时公开配置
├── features/     Agent 等业务特性
├── services/     类型化 HTTP 服务
├── shared/       通用展示组件
├── stores/       Pinia 全局状态
├── styles/       Design Tokens 与全局样式
└── views/        路由页面
```
