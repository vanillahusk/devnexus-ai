# Dynamic Thread Pool

可复用的 Spring Boot 动态线程池 Starter 与独立演示服务。

## 模块

| 模块 | 用途 |
| --- | --- |
| `dynamic-tp-spring-boot-starter` | 自动接管线程池、动态刷新配置、指标与健康检查 |
| `dynamic-tp-demo` | 独立运行和验证 Starter |
| `ops` | Redis、Prometheus 与 Grafana 本地运行配置 |

## 环境要求

- JDK 17
- Maven 3.9+
- 可选：Docker 与 Docker Compose

## 构建并安装

在当前目录执行：

```bash
mvn clean install
```

社区工程依赖该 Starter，首次构建 DevNexus AI 时需要先执行上述命令。

## 在其他项目中使用

添加依赖：

```xml
<dependency>
    <groupId>io.devnexus</groupId>
    <artifactId>dynamic-tp-spring-boot-starter</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

基础配置示例：

```yaml
dynamic-tp:
  enabled: true
  redis-channel: dynamic-tp:refresh
  monitor-interval-ms: 5000
  alert-threshold: 0.8
```

应用启用 Redis 配置后，可以通过 `dynamic-tp:refresh` Channel 发布线程池刷新命令。运行状态通过以下 Actuator 入口查看：

```text
/actuator/dynamicThreadPools
/actuator/health
/actuator/prometheus
```

## 运行 Demo

先确保 Redis 可用，然后执行：

```bash
mvn -pl dynamic-tp-demo spring-boot:run
```

默认地址为 `http://127.0.0.1:8080`。

启动测试任务：

```bash
curl -X POST http://127.0.0.1:8080/demo/stress/start
```

查看线程池状态：

```bash
curl http://127.0.0.1:8080/actuator/dynamicThreadPools
```

## 启动完整本地环境

```bash
cd ops
docker compose up -d --build
```

默认入口：

| 服务 | 地址 |
| --- | --- |
| Demo | `http://127.0.0.1:8080` |
| Prometheus | `http://127.0.0.1:9090` |
| Grafana | `http://127.0.0.1:3000` |
| Redis | `127.0.0.1:6379` |

停止环境：

```bash
docker compose down
```
