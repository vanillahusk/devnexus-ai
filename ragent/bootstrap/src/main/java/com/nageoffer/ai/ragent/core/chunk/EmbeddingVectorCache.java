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

package com.nageoffer.ai.ragent.core.chunk;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/** JVM 内有界 Embedding 缓存；Key 必须同时包含模型版本与内容哈希。 */
@Component
public class EmbeddingVectorCache {
    private static final int DEFAULT_MAX_ENTRIES = 10_000;
    private final Map<String, float[]> values;

    public EmbeddingVectorCache() {
        this(DEFAULT_MAX_ENTRIES);
    }

    EmbeddingVectorCache(int maxEntries) {
        if (maxEntries <= 0) throw new IllegalArgumentException("maxEntries must be positive");
        values = new LinkedHashMap<>(128, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, float[]> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized float[] get(String key) {
        float[] value = values.get(key);
        return value == null ? null : value.clone();
    }

    public synchronized void put(String key, float[] vector) {
        if (key == null || key.isBlank() || vector == null || vector.length == 0) return;
        values.put(key, vector.clone());
    }
}
