package com.github.paicoding.forum.service.comment.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentRateLimitServiceTest {
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void shouldAllowRequestWithinLimit() {
        when(redisTemplate.execute(any(), any(), any())).thenReturn(2L);
        CommentRateLimitService service = service(true, 2);

        assertDoesNotThrow(() -> service.check(1L, 0L));
        assertEquals(1D, counter("allowed"));
    }

    @Test
    void shouldBlockRequestAbovePerTreeLimit() {
        when(redisTemplate.execute(any(), any(), any())).thenReturn(3L);
        CommentRateLimitService service = service(true, 2);

        assertThrows(RuntimeException.class, () -> service.check(1L, 99L));
        assertEquals(1D, counter("blocked"));
    }

    @Test
    void shouldFailOpenAndExposeMetricWhenRedisIsUnavailable() {
        when(redisTemplate.execute(any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("redis unavailable"));
        CommentRateLimitService service = service(true, 2);

        assertDoesNotThrow(() -> service.check(1L, 0L));
        assertEquals(1D, counter("degraded"));
    }

    @Test
    void shouldBypassRedisWhenDisabled() {
        CommentRateLimitService service = service(false, 2);

        assertDoesNotThrow(() -> service.check(1L, 0L));
        assertEquals(0D, counter("allowed"));
    }

    private CommentRateLimitService service(boolean enabled, int limit) {
        return new CommentRateLimitService(redisTemplate, registry, enabled, limit, 1L, 2L);
    }

    private double counter(String result) {
        return registry.get("comment.write.rate.limit").tag("result", result).counter().count();
    }
}
