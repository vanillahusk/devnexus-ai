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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrieveRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** 只缓存已经完成 ONLINE 过滤、上下文预算和引用构建的可信检索结果。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrustedRetrievalCache {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final RetrievalCacheProperties properties;
    private final RetrievalIndexVersionCoordinator versionCoordinator;

    public Lookup lookup(TrustedRetrieveRequest request) {
        if (!properties.isEnabled()) return Lookup.bypass();
        RetrievalIndexVersionCoordinator.State before = versionCoordinator.state();
        if (!before.safeToCache()) return Lookup.bypass();
        try {
            String key = key(request, before.generation());
            String json = redisTemplate.opsForValue().get(key);
            if (json == null) return new Lookup(key, before.generation(), true, Optional.empty());
            RetrievalIndexVersionCoordinator.State after = versionCoordinator.state();
            if (!after.safeToCache() || after.generation() != before.generation()) return Lookup.bypass();
            TrustedRetrievalResult cached = objectMapper.readValue(json, TrustedRetrievalResult.class);
            return new Lookup(key, before.generation(), true, Optional.of(cached.asCacheHit()));
        } catch (RuntimeException | JsonProcessingException failure) {
            log.warn("可信检索缓存读取失败，已降级为实时检索，cause={}", failure.getClass().getSimpleName());
            return Lookup.bypass();
        }
    }

    public void put(Lookup lookup, TrustedRetrievalResult result) {
        if (lookup == null || !lookup.cacheable() || result == null) return;
        RetrievalIndexVersionCoordinator.State before = versionCoordinator.state();
        if (!before.safeToCache() || before.generation() != lookup.generation()) return;
        try {
            redisTemplate.opsForValue().set(lookup.key(), objectMapper.writeValueAsString(result.asCacheMiss()),
                    Duration.ofSeconds(properties.getTtlSeconds()));
            RetrievalIndexVersionCoordinator.State after = versionCoordinator.state();
            if (!after.safeToCache() || after.generation() != lookup.generation()) {
                redisTemplate.delete(lookup.key());
            }
        } catch (RuntimeException | JsonProcessingException failure) {
            log.warn("可信检索缓存写入失败，实时结果仍正常返回，cause={}", failure.getClass().getSimpleName());
        }
    }

    String key(TrustedRetrieveRequest request, long generation) {
        String queryHash = digest(normalize(request.getQuery()));
        Map<String, Object> filters = request.getMetadataFilters() == null
                ? Map.of() : new TreeMap<>(request.getMetadataFilters());
        String filterIdentity;
        try {
            filterIdentity = objectMapper.writeValueAsString(Map.of(
                    "collection", value(request.getCollectionName()),
                    "filters", filters,
                    "candidateTopK", request.getCandidateTopK(),
                    "topK", request.getTopK(),
                    "maxContextTokens", request.getMaxContextTokens()));
        } catch (JsonProcessingException failure) {
            throw new IllegalArgumentException("检索缓存过滤条件不可序列化", failure);
        }
        return properties.getKeyPrefix() + ":g" + generation + ":q" + queryHash + ":f" + digest(filterIdentity)
                + ":e" + digest(properties.getEmbeddingModelVersion())
                + ":r" + digest(properties.getRerankerModelVersion());
    }

    private String normalize(String value) {
        return value(value).strip().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String digest(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash, 0, 16);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256不可用", impossible);
        }
    }

    public record Lookup(String key, long generation, boolean cacheable,
                         Optional<TrustedRetrievalResult> result) {
        static Lookup bypass() {
            return new Lookup("", 0, false, Optional.empty());
        }
    }
}
