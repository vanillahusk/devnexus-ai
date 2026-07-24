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

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.AssertTrue;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "rag.retrieval.cache")
public class RetrievalCacheProperties {
    private boolean enabled = true;

    @NotBlank
    @Pattern(regexp = "[a-zA-Z0-9:_-]{1,96}")
    private String keyPrefix = "rag:retrieval:cache:v1";

    @Min(10)
    @Max(600)
    private long ttlSeconds = 60;

    @Min(30)
    @Max(1_800)
    private long mutationGuardTtlSeconds = 120;

    @NotBlank
    private String embeddingModelVersion = "qwen-emb-8b";

    @NotBlank
    private String rerankerModelVersion = "qwen3-rerank";

    @AssertTrue(message = "mutationGuardTtlSeconds必须不小于ttlSeconds")
    public boolean isMutationGuardLongEnough() {
        return mutationGuardTtlSeconds >= ttlSeconds;
    }
}
