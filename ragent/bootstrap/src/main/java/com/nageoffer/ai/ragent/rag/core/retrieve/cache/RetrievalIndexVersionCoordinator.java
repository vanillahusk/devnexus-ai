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

package com.nageoffer.ai.ragent.rag.core.retrieve.cache;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 索引变更屏障。开始和结束各递增一次 Generation；变更期间 active counter 大于零，查询缓存必须绕过。
 * 若进程在变更中崩溃，guard TTL 长于缓存 TTL，旧缓存会先自然过期。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RetrievalIndexVersionCoordinator {
    private static final DefaultRedisScript<Long> BEGIN_SCRIPT = new DefaultRedisScript<>("""
            local generation = redis.call('INCR', KEYS[1])
            redis.call('INCR', KEYS[2])
            redis.call('EXPIRE', KEYS[2], ARGV[1])
            return generation
            """, Long.class);
    private static final DefaultRedisScript<Long> FINISH_SCRIPT = new DefaultRedisScript<>("""
            local generation = redis.call('INCR', KEYS[1])
            local active = tonumber(redis.call('GET', KEYS[2]) or '0')
            if active <= 1 then
              redis.call('DEL', KEYS[2])
            else
              redis.call('DECR', KEYS[2])
              redis.call('EXPIRE', KEYS[2], ARGV[1])
            end
            return generation
            """, Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RetrievalCacheProperties properties;

    public Mutation beginMutation() {
        if (!properties.isEnabled()) return Mutation.disabled();
        try {
            Long generation = redisTemplate.execute(BEGIN_SCRIPT,
                    List.of(versionKey(), activeMutationsKey()),
                    Long.toString(properties.getMutationGuardTtlSeconds()));
            if (generation == null) throw new IllegalStateException("empty generation");
            return new Mutation(generation, true);
        } catch (RuntimeException failure) {
            throw new IllegalStateException("检索缓存失效屏障不可用，已拒绝本次索引变更", failure);
        }
    }

    public void completeMutation(Mutation mutation) {
        finish(mutation, true);
    }

    public void abortMutation(Mutation mutation) {
        finish(mutation, false);
    }

    private void finish(Mutation mutation, boolean strict) {
        if (mutation == null || !mutation.enforced()) return;
        try {
            Long generation = redisTemplate.execute(FINISH_SCRIPT,
                    List.of(versionKey(), activeMutationsKey()),
                    Long.toString(properties.getMutationGuardTtlSeconds()));
            if (generation == null) throw new IllegalStateException("empty generation");
        } catch (RuntimeException failure) {
            if (strict) {
                throw new IllegalStateException("检索缓存失效屏障未能完成，索引操作应由上游重试", failure);
            }
            log.error("索引变更失败后的缓存屏障清理失败，保护 Key 将按 TTL 自动释放", failure);
        }
    }

    public State state() {
        if (!properties.isEnabled()) return new State(0, 0, false);
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(List.of(versionKey(), activeMutationsKey()));
            if (values == null || values.size() != 2) return new State(0, 0, false);
            return new State(number(values.get(0)), number(values.get(1)), true);
        } catch (RuntimeException failure) {
            return new State(0, 0, false);
        }
    }

    private long number(String value) {
        if (value == null || value.isBlank()) return 0;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }

    String versionKey() {
        return properties.getKeyPrefix() + ":index-generation";
    }

    String activeMutationsKey() {
        return properties.getKeyPrefix() + ":active-mutations";
    }

    public record Mutation(long generation, boolean enforced) {
        static Mutation disabled() {
            return new Mutation(0, false);
        }
    }

    public record State(long generation, long activeMutations, boolean available) {
        public boolean safeToCache() {
            return available && activeMutations == 0;
        }
    }
}
