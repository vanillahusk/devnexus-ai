/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.rag.core.agent.quota;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.rag.core.agent.AgentExecutionBudget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/**
 * Redis Lua 原子配额：先预占最坏用量，完成后按实际用量退款；reservationKey 保证结算幂等。
 * 所有 Key 使用同一个 Cluster hash tag，确保多 Key Lua 在 Redis Cluster 中位于同一 slot。
 */
@Service
@ConditionalOnProperty(name = "rag.agent.quota.enabled", havingValue = "true", matchIfMissing = true)
public class RedisAgentQuotaService implements AgentQuotaService {
    static final String HASH_TAG = "{agent-quota}";
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[5]) == 1 then return -5 end
            local us = tonumber(redis.call('GET', KEYS[1]) or '0')
            local ut = tonumber(redis.call('GET', KEYS[2]) or '0')
            local ss = tonumber(redis.call('GET', KEYS[3]) or '0')
            local st = tonumber(redis.call('GET', KEYS[4]) or '0')
            local rs = tonumber(ARGV[1])
            local rt = tonumber(ARGV[2])
            if us + rs > tonumber(ARGV[3]) then return -1 end
            if ut + rt > tonumber(ARGV[4]) then return -2 end
            if ss + rs > tonumber(ARGV[5]) then return -3 end
            if st + rt > tonumber(ARGV[6]) then return -4 end
            for i = 1, 4 do
              local amount = (i == 1 or i == 3) and rs or rt
              redis.call('INCRBY', KEYS[i], amount)
              if redis.call('TTL', KEYS[i]) < 0 then redis.call('EXPIRE', KEYS[i], ARGV[7]) end
            end
            redis.call('SET', KEYS[5], 'ACTIVE', 'EX', ARGV[7])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> SETTLE_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[5]) ~= 'ACTIVE' then return 0 end
            local refundSteps = math.max(0, tonumber(ARGV[1]) - tonumber(ARGV[3]))
            local refundTokens = math.max(0, tonumber(ARGV[2]) - tonumber(ARGV[4]))
            local refunds = {refundSteps, refundTokens, refundSteps, refundTokens}
            for i = 1, 4 do
              if refunds[i] > 0 then
                local quotaTtl = redis.call('TTL', KEYS[i])
                local value = redis.call('DECRBY', KEYS[i], refunds[i])
                if value < 0 then
                  redis.call('SET', KEYS[i], '0')
                  if quotaTtl > 0 then redis.call('EXPIRE', KEYS[i], quotaTtl) end
                end
              end
            end
            local ttl = redis.call('TTL', KEYS[5])
            redis.call('SET', KEYS[5], 'SETTLED')
            if ttl > 0 then redis.call('EXPIRE', KEYS[5], ttl) end
            return 1
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final AgentQuotaProperties properties;
    private final Clock clock;

    public RedisAgentQuotaService(StringRedisTemplate redisTemplate, AgentQuotaProperties properties) {
        this(redisTemplate, properties, Clock.systemUTC());
    }

    RedisAgentQuotaService(StringRedisTemplate redisTemplate, AgentQuotaProperties properties, Clock clock) {
        this.redisTemplate = redisTemplate;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public Reservation reserve(String userId, String sessionId, int steps, int tokens) {
        validateIdentity(userId, "userId");
        validateIdentity(sessionId, "sessionId");
        if (steps < 1 || steps > AgentExecutionBudget.MAX_STEPS
                || tokens < 1 || tokens > AgentExecutionBudget.MAX_TOKEN_BUDGET) {
            throw new IllegalArgumentException("Agent配额预占参数越界");
        }
        QuotaKeys keys = keys(userId, sessionId);
        String reservationId = UUID.randomUUID().toString();
        String reservationKey = keys.prefix() + ":reservation:" + reservationId;
        long ttl = ttlSeconds();
        Long code;
        try {
            code = redisTemplate.execute(RESERVE_SCRIPT,
                    List.of(keys.userSteps(), keys.userTokens(), keys.sessionSteps(), keys.sessionTokens(), reservationKey),
                    Integer.toString(steps), Integer.toString(tokens),
                    Integer.toString(properties.getUserDailySteps()),
                    Integer.toString(properties.getUserDailyTokens()),
                    Integer.toString(properties.getSessionDailySteps()),
                    Integer.toString(properties.getSessionDailyTokens()), Long.toString(ttl));
        } catch (RuntimeException failure) {
            throw new IllegalStateException("Agent配额服务不可用，已拒绝本次请求", failure);
        }
        if (code == null) throw new IllegalStateException("Agent配额服务返回为空，已拒绝本次请求");
        if (code != 1L) throw quotaExceeded(code);
        return new Reservation(reservationId, true, steps, tokens, keys.userSteps(), keys.userTokens(),
                keys.sessionSteps(), keys.sessionTokens(), reservationKey, ttl);
    }

    @Override
    public void settle(Reservation reservation, AgentExecutionBudget.Usage actualUsage) {
        if (reservation == null || !reservation.enforced()) return;
        int actualSteps = actualUsage == null ? 0 : Math.min(reservation.reservedSteps(), Math.max(0, actualUsage.steps()));
        int actualTokens = actualUsage == null ? 0 : Math.min(reservation.reservedTokens(), Math.max(0, actualUsage.tokens()));
        try {
            redisTemplate.execute(SETTLE_SCRIPT, List.of(reservation.userStepKey(), reservation.userTokenKey(),
                            reservation.sessionStepKey(), reservation.sessionTokenKey(), reservation.reservationKey()),
                    Integer.toString(reservation.reservedSteps()), Integer.toString(reservation.reservedTokens()),
                    Integer.toString(actualSteps), Integer.toString(actualTokens));
        } catch (RuntimeException failure) {
            // 保留最坏用量预占比错误退款更安全；当天 TTL 到期后会自动释放。
            throw new IllegalStateException("Agent配额结算失败，预占额度将保留至过期", failure);
        }
    }

    private ClientException quotaExceeded(long code) {
        return switch ((int) code) {
            case -1 -> new ClientException("今日用户 Agent 步数额度已用尽");
            case -2 -> new ClientException("今日用户 Agent Token 额度已用尽");
            case -3 -> new ClientException("今日会话 Agent 步数额度已用尽");
            case -4 -> new ClientException("今日会话 Agent Token 额度已用尽");
            case -5 -> new ClientException("Agent配额预占请求重复");
            default -> new ClientException("Agent配额校验失败");
        };
    }

    private QuotaKeys keys(String userId, String sessionId) {
        ZoneId zone = ZoneId.of(properties.getZoneId());
        String day = ZonedDateTime.now(clock).withZoneSameInstant(zone).format(DateTimeFormatter.BASIC_ISO_DATE);
        String prefix = "rag:agent:quota:" + HASH_TAG + ":" + day;
        String user = digest(userId);
        String session = digest(userId + ":" + sessionId);
        return new QuotaKeys(prefix, prefix + ":user:" + user + ":steps", prefix + ":user:" + user + ":tokens",
                prefix + ":session:" + session + ":steps", prefix + ":session:" + session + ":tokens");
    }

    private long ttlSeconds() {
        ZoneId zone = ZoneId.of(properties.getZoneId());
        ZonedDateTime now = ZonedDateTime.now(clock).withZoneSameInstant(zone);
        long seconds = Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(zone)).getSeconds()
                + properties.getExpiryGraceSeconds();
        return Math.max(60L, Math.min(172_800L, seconds));
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256不可用", impossible);
        }
    }

    private void validateIdentity(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException("Agent配额" + field + "非法");
        }
    }

    private record QuotaKeys(String prefix, String userSteps, String userTokens,
                             String sessionSteps, String sessionTokens) {}
}
