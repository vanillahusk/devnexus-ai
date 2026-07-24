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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;
import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrieveRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "RAGENT_REAL_REDIS_RETRIEVAL_CACHE", matches = "true")
class TrustedRetrievalCacheRealIntegrationTest {
    @Test
    void shouldHitThenInvalidateAcrossRealRedisMutationBarrier() {
        String host = System.getenv().getOrDefault("RAGENT_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("RAGENT_REDIS_PORT", "6379"));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(host, port);
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        StringRedisTemplate redis = new StringRedisTemplate(connectionFactory);
        redis.afterPropertiesSet();

        RetrievalCacheProperties properties = new RetrievalCacheProperties();
        properties.setKeyPrefix("rag:test:retrieval-cache:" + UUID.randomUUID().toString().replace("-", ""));
        properties.setTtlSeconds(30);
        properties.setMutationGuardTtlSeconds(60);
        RetrievalIndexVersionCoordinator coordinator = new RetrievalIndexVersionCoordinator(redis, properties);
        TrustedRetrievalCache cache = new TrustedRetrievalCache(redis, new ObjectMapper(), properties, coordinator);
        TrustedRetrieveRequest request = TrustedRetrieveRequest.builder().query("Redis processing 恢复")
                .candidateTopK(20).topK(6).maxContextTokens(4000)
                .metadataFilters(Map.of("articleId", "1001")).build();

        String oldCacheKey = null;
        boolean cleanupVerified = false;
        try {
            TrustedRetrievalCache.Lookup miss = cache.lookup(request);
            assertTrue(miss.cacheable());
            assertFalse(miss.result().isPresent());
            oldCacheKey = miss.key();
            cache.put(miss, result());
            assertTrue(Boolean.TRUE.equals(redis.hasKey(oldCacheKey)));
            assertTrue(redis.getExpire(oldCacheKey) > 0);
            assertFalse(oldCacheKey.contains("Redis processing"));

            TrustedRetrievalCache.Lookup hit = cache.lookup(request);
            assertTrue(hit.result().orElseThrow().cacheHit());

            RetrievalIndexVersionCoordinator.Mutation mutation = coordinator.beginMutation();
            assertFalse(cache.lookup(request).cacheable());
            coordinator.completeMutation(mutation);

            TrustedRetrievalCache.Lookup afterMutation = cache.lookup(request);
            assertTrue(afterMutation.cacheable());
            assertFalse(afterMutation.result().isPresent());
            assertFalse(afterMutation.key().equals(oldCacheKey));
            Long deleted = redis.delete(List.of(oldCacheKey, coordinator.versionKey(),
                    coordinator.activeMutationsKey()));
            assertEquals(2L, deleted);
            cleanupVerified = true;
        } finally {
            if (!cleanupVerified) {
                redis.delete(List.of(oldCacheKey == null ? properties.getKeyPrefix() + ":unused" : oldCacheKey,
                        coordinator.versionKey(), coordinator.activeMutationsKey()));
            }
            connectionFactory.destroy();
        }
    }

    private TrustedRetrievalResult result() {
        var citation = new TrustedRetrievalResult.Citation("c1", "1001", "8", "Redis", "恢复",
                "processing", 0.1F, 0.8F);
        return new TrustedRetrievalResult(true, "OK", Map.of(), true, 20,
                "<untrusted_documents>x</untrusted_documents>", List.of(citation));
    }
}
