package com.github.paicoding.forum.api.model.vo.ai;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 面向社区前端的低敏 Agent 响应，不包含 Prompt、工具正文或内部异常。
 */
@Data
public class AiAgentReplyDTO implements Serializable {
    private static final long serialVersionUID = 1L;

    private String traceId;

    private String mode;

    private String answer;

    private Boolean fallback;

    private String failureCode;

    private List<ToolCallSummary> toolCalls;

    private List<Citation> citations;

    private UsageSummary usage;

    @Data
    public static class ToolCallSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private String toolName;
        private String status;
        private Integer citationCount;
    }

    @Data
    public static class Citation implements Serializable {
        private static final long serialVersionUID = 1L;
        private String chunkId;
        private String articleId;
        private String articleVersion;
        private String title;
        private String headingPath;
        private String snippet;
        private Float retrievalScore;
        private Float rerankScore;
    }

    @Data
    public static class UsageSummary implements Serializable {
        private static final long serialVersionUID = 1L;
        private Integer steps;
        private Integer toolCalls;
        private Integer retrievalCalls;
        private Integer rerankCalls;
        private Integer modelCalls;
        private Integer estimatedTokens;
        private String modelName;
        private Long remainingMillis;
    }
}
