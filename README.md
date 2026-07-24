# DevNexus AI

技术社区与 AI 知识平台。本仓库包含两个可以独立构建和运行的工程：

```text
community/   社区后端、微服务和 Vue 前端
ragent/      RAG 服务和 React 管理端
```

## 环境要求

- JDK 17
- Maven 3.9+
- Node.js 20+
- Docker 与 Docker Compose
- `curl`、`nc`

## 获取代码

```bash
git clone git@github.com:VanillaCreamyy/devnexus-ai.git
cd devnexus-ai
```

## 启动社区依赖

```bash
cd community

# MySQL、Redis、Nacos
docker compose -f ops/docker-compose.yml --profile full up -d mysql redis nacos

# RocketMQ
docker compose -f ops/rocketmq/docker-compose.yml up -d
```

默认数据库为 `pai_coding`。首次运行前，请确认
`paicoding-web/src/main/resources-env/dev/application-dal.yml` 中的数据库连接可用，并完成 Liquibase 初始化。

## 启动社区服务

分别打开终端并在 `community` 目录执行：

```bash
bash scripts/run-auth-service-dev.sh
bash scripts/run-aigc-service-dev.sh
bash scripts/run-message-service-dev.sh
bash scripts/run-web-with-remote-services-dev.sh
bash scripts/run-gateway-dev.sh
```

默认端口：

| 服务 | 端口 |
| --- | ---: |
| Gateway | 10010 |
| Community Web | 8081 |
| Auth Service | 8093 |
| AIGC Service | 8094 |
| Message Service | 8095 |
| Nacos | 8848 |
| RocketMQ Dashboard | 8082 |

启动社区 Vue 前端：

```bash
cd community/pai-coding-front
npm ci
npm run dev
```

## 启动 RAG 服务

先启动低资源依赖并构建 Ragent：

```bash
cd community
bash scripts/start-ragent-low-resource-dependencies.sh
bash scripts/build-ragent-integration-artifact.sh
```

如需调用真实模型，在启动前通过环境变量提供密钥：

```bash
export SILICONFLOW_API_KEY="your-key"
export OPENROUTER_API_KEY="your-key"
```

启动服务：

```bash
cd community
bash scripts/run-ragent-low-resource.sh
```

健康检查：

```bash
curl http://127.0.0.1:9090/api/ragent/actuator/health
```

启动 Ragent React 前端：

```bash
cd ragent/frontend
cp .env.example .env
npm ci
npm run dev
```

## 停止依赖

```bash
cd community
bash scripts/stop-ragent-low-resource-dependencies.sh

docker compose -f ops/rocketmq/docker-compose.yml down
docker compose -f ops/docker-compose.yml --profile full down
```

## 构建

```bash
cd community
./mvnw clean package -DskipTests

cd ../ragent
./mvnw clean package -DskipTests
```

## 配置安全

- 不要提交 `.env`、API Key、Token、日志或运行时数据。
- 模型密钥通过环境变量注入。
- 本地密码和内部服务 Token 在公开部署前必须替换。

## 许可

本仓库基于以下 Apache License 2.0 项目进行二次开发：

- [Paicoding](https://github.com/itwanger/paicoding)
- [Ragent](https://github.com/nageoffer/ragent)

许可证分别保留在 `community/License` 和 `ragent/LICENSE`。

