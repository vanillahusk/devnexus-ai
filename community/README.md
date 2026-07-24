# Community

## 环境

- JDK 17
- Maven 3.9+
- MySQL 8
- Redis 7
- RocketMQ 5
- 可选：Nacos、Prometheus、Grafana、SkyWalking

## 构建

```bash
./mvnw clean package -DskipTests
```

## 启动依赖

```bash
docker compose -f ops/docker-compose.yml --profile full up -d mysql redis nacos
docker compose -f ops/rocketmq/docker-compose.yml up -d
```

数据库配置位于：

```text
paicoding-web/src/main/resources-env/dev/application-dal.yml
```

首次运行时需要创建 `pai_coding` 数据库并完成 Liquibase 初始化。

## 启动后端

在独立终端中依次运行：

```bash
bash scripts/run-auth-service-dev.sh
bash scripts/run-aigc-service-dev.sh
bash scripts/run-message-service-dev.sh
bash scripts/run-web-with-remote-services-dev.sh
bash scripts/run-gateway-dev.sh
```

默认入口：

```text
Gateway:        http://127.0.0.1:10010
Community Web:  http://127.0.0.1:8081
Auth Service:   http://127.0.0.1:8093
AIGC Service:   http://127.0.0.1:8094
Message Service:http://127.0.0.1:8095
```

## 启动前端

```bash
cd pai-coding-front
npm ci
npm run dev
```

## 使用 Nacos

```bash
export NACOS_DISCOVERY_ENABLED=true
export NACOS_ADDR=127.0.0.1:8848
```

设置后重新启动各 Java 服务。

## 停止依赖

```bash
docker compose -f ops/rocketmq/docker-compose.yml down
docker compose -f ops/docker-compose.yml --profile full down
```

本地开发凭据仅用于开发环境，部署前必须通过环境变量或配置中心替换。
