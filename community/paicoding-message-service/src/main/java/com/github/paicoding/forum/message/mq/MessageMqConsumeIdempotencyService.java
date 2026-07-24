package com.github.paicoding.forum.message.mq;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class MessageMqConsumeIdempotencyService {

    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);
    private static final Duration COMPLETED_TTL = Duration.ofDays(7);
    private final StringRedisTemplate redisTemplate;

    public ConsumeToken tryAcquire(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("mq eventId must not be blank");
        }
        String key = "mq:consume:notify:" + eventId;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "PROCESSING", PROCESSING_TTL);
        if (Boolean.TRUE.equals(acquired)) {
            return new ConsumeToken(key, ConsumeState.ACQUIRED);
        }
        String state = redisTemplate.opsForValue().get(key);
        return new ConsumeToken(key, "COMPLETED".equals(state) ? ConsumeState.COMPLETED : ConsumeState.PROCESSING);
    }

    public void complete(ConsumeToken token) {
        redisTemplate.opsForValue().set(token.key(), "COMPLETED", COMPLETED_TTL);
    }

    public void release(ConsumeToken token) {
        redisTemplate.delete(token.key());
    }

    public enum ConsumeState { ACQUIRED, PROCESSING, COMPLETED }
    public record ConsumeToken(String key, ConsumeState state) { }
}
