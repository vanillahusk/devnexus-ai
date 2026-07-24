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

package com.nageoffer.ai.ragent.infra.embedding;

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 显式提供 SILICONFLOW_API_KEY 时才执行，避免普通单测消耗远端额度。
 */
@EnabledIfEnvironmentVariable(named = "SILICONFLOW_API_KEY", matches = ".+")
class SiliconFlowEmbeddingRealApiTest {

    @Test
    void shouldCallQwen3EmbeddingWithProjectDimensionAndDistinguishBasicSemantics() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(30))
                .callTimeout(Duration.ofSeconds(40))
                .build();
        SiliconFlowEmbeddingClient client = new SiliconFlowEmbeddingClient(httpClient);

        List<List<Float>> vectors = client.embedBatch(List.of(
                "Redis 可以作为高并发场景下的缓存和削峰组件",
                "Redis 缓存能够降低数据库读取压力",
                "春天适合去公园拍摄花朵"
        ), target());

        assertThat(vectors).hasSize(3).allSatisfy(vector -> assertThat(vector).hasSize(1536));
        double related = cosine(vectors.get(0), vectors.get(1));
        double unrelated = cosine(vectors.get(0), vectors.get(2));
        assertThat(related).isGreaterThan(unrelated + 0.10D);
    }

    private ModelTarget target() {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(System.getenv().getOrDefault("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn"));
        provider.setApiKey(System.getenv("SILICONFLOW_API_KEY"));
        provider.setEndpoints(new HashMap<>(Map.of("embedding", "/v1/embeddings")));

        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("qwen-emb-8b");
        candidate.setProvider("siliconflow");
        candidate.setModel(System.getenv().getOrDefault(
                "SILICONFLOW_EMBEDDING_MODEL", "Qwen/Qwen3-Embedding-8B"));
        candidate.setDimension(Integer.parseInt(
                System.getenv().getOrDefault("SILICONFLOW_EMBEDDING_DIMENSION", "1536")));
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private double cosine(List<Float> left, List<Float> right) {
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int i = 0; i < left.size(); i++) {
            double a = left.get(i);
            double b = right.get(i);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }
}
