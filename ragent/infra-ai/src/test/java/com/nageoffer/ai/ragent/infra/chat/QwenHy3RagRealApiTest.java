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

package com.nageoffer.ai.ragent.infra.chat;

import com.nageoffer.ai.ragent.framework.convention.ChatMessage;
import com.nageoffer.ai.ragent.framework.convention.ChatRequest;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.embedding.SiliconFlowEmbeddingClient;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariables;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Qwen3 Embedding 检索 + HY3 带引用生成的最小真实组合验证。
 */
@EnabledIfEnvironmentVariables({
        @EnabledIfEnvironmentVariable(named = "SILICONFLOW_API_KEY", matches = ".+"),
        @EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
})
class QwenHy3RagRealApiTest {

    @Test
    void shouldRetrieveWithQwen3AndGenerateGroundedAnswerWithHy3() {
        OkHttpClient httpClient = new OkHttpClient.Builder()
                .connectTimeout(Duration.ofSeconds(5))
                .readTimeout(Duration.ofSeconds(45))
                .callTimeout(Duration.ofSeconds(55))
                .build();
        SiliconFlowEmbeddingClient embeddingClient = new SiliconFlowEmbeddingClient(httpClient);
        OpenRouterChatClient chatClient = new OpenRouterChatClient(httpClient, Runnable::run);

        String query = "为什么使用 Outbox 和 RocketMQ 后，消费者仍然需要幂等？";
        List<Evidence> evidence = List.of(
                new Evidence("c1", "Outbox 与业务数据在同一数据库事务提交，后台任务至少一次投递到 RocketMQ；发送确认丢失时可能重复发送，所以消费者必须按事件或业务版本幂等。"),
                new Evidence("c2", "Redis 常用于热点缓存，并通过过期时间控制缓存生命周期。"),
                new Evidence("c3", "SkyWalking 可以展示跨服务调用拓扑和慢接口。")
        );

        List<String> texts = new ArrayList<>();
        texts.add(query);
        evidence.forEach(item -> texts.add(item.content()));
        List<List<Float>> vectors = embeddingClient.embedBatch(texts, embeddingTarget());
        List<Float> queryVector = vectors.get(0);
        List<ScoredEvidence> ranked = new ArrayList<>();
        for (int index = 0; index < evidence.size(); index++) {
            ranked.add(new ScoredEvidence(evidence.get(index), cosine(queryVector, vectors.get(index + 1))));
        }
        ranked.sort(Comparator.comparingDouble(ScoredEvidence::score).reversed());

        assertThat(ranked.get(0).evidence().id()).isEqualTo("c1");
        String context = ranked.stream().limit(2)
                .map(item -> "[ref:" + item.evidence().id() + "] " + item.evidence().content())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow();
        ChatRequest request = ChatRequest.builder()
                .messages(List.of(
                        ChatMessage.system("只能依据给定资料回答；每个事实必须引用 [ref:编号]；证据不足就明确拒答。"),
                        ChatMessage.user("问题：" + query + "\n<untrusted_documents>\n" + context
                                + "\n</untrusted_documents>")
                ))
                .temperature(0.1D)
                .maxTokens(300)
                .thinking(false)
                .build();

        String answer = chatClient.chat(request, chatTarget());

        assertThat(answer).containsIgnoringCase("Outbox").containsIgnoringCase("RocketMQ").contains("[ref:c1]");
    }

    private ModelTarget embeddingTarget() {
        AIModelProperties.ProviderConfig provider = provider(
                System.getenv().getOrDefault("SILICONFLOW_BASE_URL", "https://api.siliconflow.cn"),
                System.getenv("SILICONFLOW_API_KEY"), "embedding", "/v1/embeddings");
        AIModelProperties.ModelCandidate candidate = candidate(
                "qwen-emb-8b", "siliconflow", "Qwen/Qwen3-Embedding-8B");
        candidate.setDimension(1536);
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private ModelTarget chatTarget() {
        AIModelProperties.ProviderConfig provider = provider(
                System.getenv().getOrDefault("OPENROUTER_BASE_URL", "https://openrouter.ai"),
                System.getenv("OPENROUTER_API_KEY"), "chat", "/api/v1/chat/completions");
        AIModelProperties.ModelCandidate candidate = candidate("hy3-free", "openrouter", "tencent/hy3:free");
        return new ModelTarget(candidate.getId(), candidate, provider);
    }

    private AIModelProperties.ProviderConfig provider(
            String url, String apiKey, String capability, String endpoint) {
        AIModelProperties.ProviderConfig provider = new AIModelProperties.ProviderConfig();
        provider.setUrl(url);
        provider.setApiKey(apiKey);
        provider.setEndpoints(new HashMap<>(Map.of(capability, endpoint)));
        return provider;
    }

    private AIModelProperties.ModelCandidate candidate(String id, String provider, String model) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId(id);
        candidate.setProvider(provider);
        candidate.setModel(model);
        return candidate;
    }

    private double cosine(List<Float> left, List<Float> right) {
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < left.size(); index++) {
            double a = left.get(index);
            double b = right.get(index);
            dot += a * b;
            leftNorm += a * a;
            rightNorm += b * b;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private record Evidence(String id, String content) {
    }

    private record ScoredEvidence(Evidence evidence, double score) {
    }
}
