package com.github.paicoding.forum.web.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MqConsumeIdempotencyService {
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofDays(7);
    private final StringRedisTemplate stringRedisTemplate;

    public ConsumeToken tryAcquire(String consumer, String eventId) {
        String key = key(consumer, eventId);
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", PROCESSING_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return new ConsumeToken(key, ConsumeState.ACQUIRED);
        }
        String state = stringRedisTemplate.opsForValue().get(key);
        return new ConsumeToken(key, "COMPLETED".equals(state) ? ConsumeState.COMPLETED : ConsumeState.PROCESSING);
    }

    public void complete(ConsumeToken token) {
        stringRedisTemplate.opsForValue().set(token.key(), "COMPLETED", COMPLETED_TTL);
    }

    public void release(ConsumeToken token) {
        stringRedisTemplate.delete(token.key());
    }

    private String key(String consumer, String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("mq eventId must not be blank");
        }
        return "mq:consume:" + consumer + ':' + eventId;
    }

    public enum ConsumeState { ACQUIRED, PROCESSING, COMPLETED }

    public record ConsumeToken(String key, ConsumeState state) {
    }
}
