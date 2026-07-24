package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.api.model.exception.ExceptionUtil;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

/**
 * 按文章和评论树隔离的固定窗口限流器。Redis 异常时降级放行，避免限流组件拖垮评论主链路。
 */
@Slf4j
@Service
public class CommentRateLimitService {
    private static final String KEY_PREFIX = "comment:write:limiter:";
    private static final String RATE_LIMIT_LUA =
            "local c = redis.call('INCR', KEYS[1]);" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end;" +
                    "return c;";

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int limit;
    private final long windowSeconds;
    private final long keyExpireSeconds;
    private final DefaultRedisScript<Long> script = new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
    private final Counter allowed;
    private final Counter blocked;
    private final Counter degraded;

    public CommentRateLimitService(StringRedisTemplate redisTemplate,
                                   MeterRegistry meterRegistry,
                                   @Value("${paicoding.comment.rate-limit.enabled:true}") boolean enabled,
                                   @Value("${paicoding.comment.rate-limit.limit:500}") int limit,
                                   @Value("${paicoding.comment.rate-limit.window-seconds:1}") long windowSeconds,
                                   @Value("${paicoding.comment.rate-limit.key-expire-seconds:2}") long keyExpireSeconds) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.limit = Math.max(1, limit);
        this.windowSeconds = Math.max(1L, windowSeconds);
        this.keyExpireSeconds = Math.max(this.windowSeconds + 1L, keyExpireSeconds);
        this.allowed = counter(meterRegistry, "allowed");
        this.blocked = counter(meterRegistry, "blocked");
        this.degraded = counter(meterRegistry, "degraded");
    }

    public void check(Long articleId, Long topCommentId) {
        if (!enabled) {
            return;
        }
        long treeId = topCommentId == null || topCommentId <= 0 ? 0L : topCommentId;
        long window = System.currentTimeMillis() / (windowSeconds * 1000L);
        String key = KEY_PREFIX + articleId + ":" + treeId + ":" + window;
        Long count;
        try {
            count = redisTemplate.execute(script, Collections.singletonList(key), String.valueOf(keyExpireSeconds));
        } catch (RuntimeException e) {
            degraded.increment();
            log.warn("comment rate limiter unavailable, allow request, articleId={}, treeId={}, error={}",
                    articleId, treeId, e.getClass().getSimpleName());
            return;
        }
        if (count == null) {
            degraded.increment();
            log.warn("comment rate limiter returned null, allow request, articleId={}, treeId={}", articleId, treeId);
            return;
        }
        if (count > limit) {
            blocked.increment();
            throw ExceptionUtil.of(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "评论过于频繁, 请稍后重试");
        }
        allowed.increment();
    }

    private Counter counter(MeterRegistry registry, String result) {
        return Counter.builder("comment.write.rate.limit")
                .description("Comment write rate-limit decisions")
                .tag("result", result)
                .register(registry);
    }
}
