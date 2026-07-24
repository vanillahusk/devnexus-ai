package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.core.senstive.SensitiveProperty;
import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** AI 入口配额、Prompt 注入和敏感内容治理。 */
@Service
@RequiredArgsConstructor
public class AiRequestGovernanceService {
    private static final String QUOTA_KEY_PREFIX = "ai:quota:";
    private static final List<String> INJECTION_MARKERS = List.of(
            "ignore previous", "ignore all previous", "system prompt", "developer message",
            "reveal your prompt", "jailbreak", "do anything now",
            "忽略以上", "忽略之前", "无视以上", "系统提示词", "开发者消息", "越狱"
    );
    private static final String QUOTA_LUA = """
            local requests = tonumber(redis.call('HGET', KEYS[1], 'requests') or '0')
            local tokens = tonumber(redis.call('HGET', KEYS[1], 'tokens') or '0')
            local nextTokens = tokens + tonumber(ARGV[1])
            if requests + 1 > tonumber(ARGV[2]) then return -1 end
            if nextTokens > tonumber(ARGV[3]) then return -2 end
            redis.call('HINCRBY', KEYS[1], 'requests', 1)
            redis.call('HINCRBY', KEYS[1], 'tokens', ARGV[1])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return 1
            """;

    private final StringRedisTemplate stringRedisTemplate;
    private final SensitiveProperty sensitiveProperty;
    private final AiKnowledgeProperties properties;
    private final MeterRegistry meterRegistry;
    private final DefaultRedisScript<Long> quotaScript = new DefaultRedisScript<>(QUOTA_LUA, Long.class);

    public void check(Long userId, String question) {
        String normalized = StringUtils.trimToEmpty(question);
        if (normalized.length() > properties.getGovernance().getMaxQuestionChars()) {
            reject("question_too_long", "问题长度超过限制");
        }
        String lower = normalized.toLowerCase(Locale.ROOT);
        if (INJECTION_MARKERS.stream().anyMatch(lower::contains)) {
            reject("prompt_injection", "问题包含疑似 Prompt 注入指令");
        }
        List<String> sensitiveWords = sensitiveProperty.getDeny();
        boolean unsafe = sensitiveWords != null && sensitiveWords.stream()
                .filter(StringUtils::isNotBlank)
                .map(word -> word.toLowerCase(Locale.ROOT))
                .anyMatch(lower::contains);
        if (unsafe) {
            reject("sensitive_content", "问题包含不适合提交给 AI 的敏感内容");
        }

        long estimatedTokens = Math.max(1L, (normalized.length() + 1L) / 2L);
        Long result = stringRedisTemplate.execute(quotaScript,
                Collections.singletonList(quotaKey(userId)),
                String.valueOf(estimatedTokens),
                String.valueOf(properties.getGovernance().getDailyRequestLimit()),
                String.valueOf(properties.getGovernance().getDailyTokenLimit()),
                String.valueOf(secondsUntilTomorrow()));
        if (result == null) {
            reject("quota_unavailable", "AI 配额服务暂时不可用");
        }
        if (result == -1L) {
            reject("request_quota", "今日 AI 调用次数已用完");
        }
        if (result == -2L) {
            reject("token_quota", "今日 AI Token 配额已用完");
        }
        meterRegistry.counter("ai.requests", "outcome", "allowed").increment();
        meterRegistry.counter("ai.tokens.estimated", "type", "input").increment(estimatedTokens);
    }

    private void reject(String reason, String message) {
        meterRegistry.counter("ai.requests", "outcome", "rejected", "reason", reason).increment();
        throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, message);
    }

    private String quotaKey(Long userId) {
        return QUOTA_KEY_PREFIX + LocalDate.now() + ":" + userId;
    }

    private long secondsUntilTomorrow() {
        return Math.max(60L, Duration.between(LocalDateTime.now(),
                LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.MIDNIGHT)).getSeconds() + 60L);
    }
}
