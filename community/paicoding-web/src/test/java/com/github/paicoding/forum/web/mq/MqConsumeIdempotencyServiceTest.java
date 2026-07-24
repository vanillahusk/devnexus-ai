package com.github.paicoding.forum.web.mq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MqConsumeIdempotencyServiceTest {
    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private MqConsumeIdempotencyService service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = mock(StringRedisTemplate.class);
        valueOperations = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        service = new MqConsumeIdempotencyService(redisTemplate);
    }

    @Test
    void shouldAcquireNewEventAndMarkItCompleted() {
        when(valueOperations.setIfAbsent(eq("mq:consume:notify:event-1"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);

        MqConsumeIdempotencyService.ConsumeToken token = service.tryAcquire("notify", "event-1");
        service.complete(token);

        assertEquals(MqConsumeIdempotencyService.ConsumeState.ACQUIRED, token.state());
        verify(valueOperations).set(eq("mq:consume:notify:event-1"), eq("COMPLETED"), any(Duration.class));
    }

    @Test
    void shouldSkipCompletedDuplicate() {
        when(valueOperations.setIfAbsent(eq("mq:consume:notify:event-1"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(false);
        when(valueOperations.get("mq:consume:notify:event-1")).thenReturn("COMPLETED");

        MqConsumeIdempotencyService.ConsumeToken token = service.tryAcquire("notify", "event-1");

        assertEquals(MqConsumeIdempotencyService.ConsumeState.COMPLETED, token.state());
    }

    @Test
    void shouldReleaseLeaseAfterBusinessFailure() {
        when(valueOperations.setIfAbsent(eq("mq:consume:comment-write:event-2"), eq("PROCESSING"), any(Duration.class)))
                .thenReturn(true);
        MqConsumeIdempotencyService.ConsumeToken token = service.tryAcquire("comment-write", "event-2");

        service.release(token);

        verify(redisTemplate).delete("mq:consume:comment-write:event-2");
    }
}
