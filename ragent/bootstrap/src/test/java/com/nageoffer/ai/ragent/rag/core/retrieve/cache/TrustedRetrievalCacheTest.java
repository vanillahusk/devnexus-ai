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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TrustedRetrievalCacheTest {
    @Test
    void shouldIncludeQueryFiltersGenerationAndModelVersionsInKey() {
        Fixture fixture = fixture();
        TrustedRetrieveRequest first = request("Redis Queue", Map.of("articleId", "1001"));

        String key = fixture.cache.key(first, 8);

        assertTrue(key.contains(":g8:q"));
        assertNotEquals(key, fixture.cache.key(request("RocketMQ", Map.of("articleId", "1001")), 8));
        assertNotEquals(key, fixture.cache.key(request("Redis Queue", Map.of("articleId", "1002")), 8));
        assertNotEquals(key, fixture.cache.key(first, 9));
        fixture.properties.setEmbeddingModelVersion("new-embedding");
        assertNotEquals(key, fixture.cache.key(first, 8));
    }

    @Test
    void shouldReturnCacheHitOnlyWhenGenerationRemainsStable() throws Exception {
        Fixture fixture = fixture();
        TrustedRetrievalResult stored = result();
        when(fixture.coordinator.state()).thenReturn(
                new RetrievalIndexVersionCoordinator.State(4, 0, true),
                new RetrievalIndexVersionCoordinator.State(4, 0, true));
        when(fixture.values.get(any())).thenReturn(fixture.mapper.writeValueAsString(stored));

        TrustedRetrievalCache.Lookup lookup = fixture.cache.lookup(request("Redis", Map.of()));

        assertTrue(lookup.result().orElseThrow().cacheHit());
    }

    @Test
    void shouldBypassDuringMutationAndDeleteWriteIfGenerationChanges() {
        Fixture fixture = fixture();
        when(fixture.coordinator.state()).thenReturn(
                new RetrievalIndexVersionCoordinator.State(4, 1, true));
        TrustedRetrievalCache.Lookup bypass = fixture.cache.lookup(request("Redis", Map.of()));
        assertFalse(bypass.cacheable());
        verify(fixture.values, never()).get(any());

        when(fixture.coordinator.state()).thenReturn(
                new RetrievalIndexVersionCoordinator.State(4, 0, true),
                new RetrievalIndexVersionCoordinator.State(5, 0, true));
        TrustedRetrievalCache.Lookup token = new TrustedRetrievalCache.Lookup("cache-key", 4, true,
                java.util.Optional.empty());
        fixture.cache.put(token, result());

        verify(fixture.values).set(eq("cache-key"), any(), eq(Duration.ofSeconds(60)));
        verify(fixture.redis).delete("cache-key");
    }

    @SuppressWarnings("unchecked")
    private Fixture fixture() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        RetrievalIndexVersionCoordinator coordinator = mock(RetrievalIndexVersionCoordinator.class);
        RetrievalCacheProperties properties = new RetrievalCacheProperties();
        ObjectMapper mapper = new ObjectMapper();
        return new Fixture(new TrustedRetrievalCache(redis, mapper, properties, coordinator),
                redis, values, coordinator, properties, mapper);
    }

    private TrustedRetrieveRequest request(String query, Map<String, Object> filters) {
        return TrustedRetrieveRequest.builder().query(query).candidateTopK(20).topK(6)
                .maxContextTokens(4000).collectionName("articles").metadataFilters(filters).build();
    }

    private TrustedRetrievalResult result() {
        var citation = new TrustedRetrievalResult.Citation("c1", "1001", "8", "Redis", "恢复",
                "processing", 0.1F, 0.8F);
        return new TrustedRetrievalResult(true, "OK", Map.of("signal", true), true,
                20, "<untrusted_documents>x</untrusted_documents>", List.of(citation));
    }

    private record Fixture(TrustedRetrievalCache cache, StringRedisTemplate redis,
                           ValueOperations<String, String> values,
                           RetrievalIndexVersionCoordinator coordinator,
                           RetrievalCacheProperties properties, ObjectMapper mapper) {}
}
