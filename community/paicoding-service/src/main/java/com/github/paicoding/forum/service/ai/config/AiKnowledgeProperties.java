package com.github.paicoding.forum.service.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * AI 知识助手配置
 *
 * @author Codex
 * @date 2026-04-01
 */
@Data
@Component
@ConfigurationProperties(prefix = "ai.knowledge")
public class AiKnowledgeProperties {
    /**
     * 开启后文章详情页会显示知识助手入口
     */
    private boolean enabled = true;

    /**
     * Redis 中单个会话保留的轮数
     */
    private int memoryLimit = 6;

    /** Redis 会话上下文保留时长。 */
    private int memoryTtlHours = 24;

    /**
     * 本地召回的文章数
     */
    private int recallArticleLimit = 3;

    /**
     * 本地召回的评论数
     */
    private int recallCommentLimit = 3;

    /**
     * 规则/FAQ 文档召回数
     */
    private int recallConfigLimit = 20;

    /**
     * 使用 global_conf 作为知识文档时的 key 前缀
     */
    private String configPrefix = "ai.knowledge.";

    private Route route = new Route();

    private Service service = new Service();

    private Ragent ragent = new Ragent();

    private Api api = new Api();

    private Governance governance = new Governance();

    private GenerationRebuild generationRebuild = new GenerationRebuild();

    @Data
    public static class Route {
        /**
         * 主路由：ragent / api / local
         */
        private String primary = "ragent";

        /**
         * 降级路由：当前实现默认 local
         */
        private String fallback = "local";
    }

    @Data
    public static class Service {
        /**
         * AI 服务调用模式：local / remote
         */
        private String mode = "local";

        /**
         * 远端 AIGC 服务根地址
         */
        private String baseUrl = "http://localhost:8080";

        /**
         * 远端 AIGC 服务 serviceId
         */
        private String serviceId = "aigc-service";

        /**
         * 内部 AI 助手接口前缀
         */
        private String assistantInternalPath = "/internal/aigc/assistant";

        /**
         * 内部 AI 知识后台接口前缀
         */
        private String adminInternalPath = "/internal/aigc/admin/knowledge";

        /**
         * 服务间调用鉴权头
         */
        private String tokenHeader = "X-AIGC-INTERNAL-TOKEN";

        /**
         * 透传用户id的头
         */
        private String userIdHeader = "X-AIGC-USER-ID";

        /**
         * 服务间调用 token
         */
        private String token;
    }

    @Data
    public static class Ragent {
        /**
         * 是否启用 ragent 代理
         */
        private boolean enabled = false;

        /**
         * 是否在知识文档变更后自动同步到 ragent
         */
        private boolean autoSync = false;

        /**
         * ragent 服务根地址
         */
        private String baseUrl = "http://localhost:9090/api/ragent";

        /**
         * 对话路径
         */
        private String chatPath = "/rag/v3/chat";

        /**
         * 可直接复用的鉴权 token
         */
        private String token;

        /**
         * 未直接配置 token 时，用账号密码自动登录
         */
        private String username;

        private String password;

        /**
         * 自动建库时使用的知识库名称
         */
        private String kbName = "paicoding-community-kb";

        /**
         * 默认嵌入模型
         */
        private String embeddingModel = "qwen-emb-8b";

        /**
         * 默认 collection 名称
         */
        private String collectionName = "paicodingcommunitykb";

        private int connectTimeoutMs = 500;

        private int readTimeoutMs = 2500;

        /** 文档异步分块、Embedding 和向量写入的最大确认时间。 */
        private int chunkWaitTimeoutMs = 60000;

        /** 文档状态轮询间隔，避免高频请求 Ragent。 */
        private int chunkPollIntervalMs = 500;
    }

    @Data
    public static class GenerationRebuild {
        /** 默认关闭；必须先执行 Ragent PostgreSQL Generation 升级脚本。 */
        private boolean enabled = false;
        private int batchSize = 100;
        private int maxCatchupRounds = 10;
        private int maxArticles = 100000;
    }

    @Data
    public static class Api {
        /**
         * 是否启用直连模型 API
         */
        private boolean enabled = false;

        /**
         * OpenAI 兼容服务根地址，例如 https://api.openai.com
         */
        private String baseUrl;

        /**
         * Chat Completions 路径
         */
        private String chatPath = "/v1/chat/completions";

        /**
         * 模型调用 API Key
         */
        private String apiKey;

        /**
         * 调用模型名
         */
        private String model = "gpt-4o-mini";

        /**
         * 是否通过项目代理访问
         */
        private boolean useProxy = true;

        /**
         * 生成温度
         */
        private Double temperature = 0.2D;

        /**
         * 最大输出 token
         */
        private Integer maxTokens = 1500;

        /**
         * 可选系统提示词
         */
        private String systemPrompt = "你是社区 AI 知识助手，请优先根据给定的社区资料回答，如果资料不足请明确说明不确定，不要编造。";

        private int connectTimeoutMs = 500;

        private int readTimeoutMs = 3000;
    }

    @Data
    public static class Governance {
        /** 单实例允许同时访问外部 AI 的请求数。 */
        private int maxConcurrentCalls = 16;

        /** 连续失败或慢调用达到该次数后打开熔断器。 */
        private int failureThreshold = 5;

        /** 熔断保持时间，之后允许一个探测请求。 */
        private long openDurationMs = 30000L;

        /** 超过该耗时即记为慢调用。 */
        private long slowCallThresholdMs = 2500L;

        /** 单用户自然日调用次数上限。 */
        private int dailyRequestLimit = 50;

        /** 单用户自然日估算输入 Token 上限。 */
        private int dailyTokenLimit = 20000;

        /** 单次问题最大字符数。 */
        private int maxQuestionChars = 2000;
    }
}
