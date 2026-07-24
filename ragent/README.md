# Ragent

## 环境

- JDK 17
- Maven 3.9+
- Node.js 20+
- PostgreSQL 16 与 pgvector
- Redis
- RocketMQ
- S3 兼容对象存储

仓库根目录的 `community/scripts` 和 `community/ops/ragent` 提供低资源依赖启动方式。

## 启动依赖

从仓库根目录执行：

```bash
cd community
bash scripts/start-ragent-low-resource-dependencies.sh
```

默认端口：

```text
PostgreSQL: 5432
Redis:      16379
RocketMQ:   9876
S3:         9000
```

## 构建

```bash
cd ragent
./mvnw clean package -DskipTests
```

也可以从 `community` 目录使用：

```bash
bash scripts/build-ragent-integration-artifact.sh
```

## 模型配置

按需设置环境变量：

```bash
export SILICONFLOW_API_KEY="your-key"
export OPENROUTER_API_KEY="your-key"
export BAILIAN_API_KEY="your-key"
```

没有配置真实模型时，可以启用项目提供的验证模型完成本地基础联调：

```bash
export RAGENT_VALIDATION_EMBEDDING_ENABLED=true
export RAGENT_EMBEDDING_MODEL=validation-embedding-1536
```

## 启动后端

推荐从 `community` 目录启动：

```bash
cd community
bash scripts/run-ragent-low-resource.sh
```

服务地址：

```text
http://127.0.0.1:9090/api/ragent
```

健康检查：

```bash
curl http://127.0.0.1:9090/api/ragent/actuator/health
```

## 启动前端

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
```

不要将 `.env` 或模型 API Key 提交到仓库。
