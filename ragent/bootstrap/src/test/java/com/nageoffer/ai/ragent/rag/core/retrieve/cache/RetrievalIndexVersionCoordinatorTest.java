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

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RetrievalIndexVersionCoordinatorTest {
    @Test
    @SuppressWarnings("unchecked")
    void shouldOpenAndCloseMutationBarrierAtomically() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(7L, 8L);
        RetrievalIndexVersionCoordinator coordinator = coordinator(redis);

        var mutation = coordinator.beginMutation();
        coordinator.completeMutation(mutation);

        assertTrue(mutation.enforced());
        assertEquals(7, mutation.generation());
        verify(redis, org.mockito.Mockito.times(2))
                .execute(any(RedisScript.class), anyList(), any(Object[].class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldRejectIndexMutationWhenBarrierCannotBeCreated() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new IllegalStateException("redis down"));

        assertThrows(IllegalStateException.class, () -> coordinator(redis).beginMutation());
    }

    private RetrievalIndexVersionCoordinator coordinator(StringRedisTemplate redis) {
        RetrievalCacheProperties properties = new RetrievalCacheProperties();
        return new RetrievalIndexVersionCoordinator(redis, properties);
    }
}
