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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.data.Offset.offset;

class DeterministicValidationEmbeddingClientTest {

    private final DeterministicValidationEmbeddingClient client = new DeterministicValidationEmbeddingClient();

    @Test
    void shouldProduceStableNormalizedVectorWithConfiguredDimension() {
        ModelTarget target = target(1536);

        List<Float> first = client.embed("文章版本 v8", target);
        List<Float> second = client.embed("文章版本 v8", target);
        List<Float> different = client.embed("文章版本 v9", target);

        assertThat(first).hasSize(1536).isEqualTo(second).isNotEqualTo(different);
        double norm = Math.sqrt(first.stream().mapToDouble(value -> value * value).sum());
        assertThat(norm).isCloseTo(1D, offset(0.0001D));
    }

    @Test
    void shouldPreserveBatchOrderAndRejectUnsafeDimension() {
        ModelTarget target = target(8);

        List<List<Float>> batch = client.embedBatch(List.of("first", "second"), target);

        assertThat(batch).containsExactly(client.embed("first", target), client.embed("second", target));
        assertThatThrownBy(() -> client.embed("invalid", target(0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dimension");
    }

    private ModelTarget target(int dimension) {
        AIModelProperties.ModelCandidate candidate = new AIModelProperties.ModelCandidate();
        candidate.setId("validation-embedding-1536");
        candidate.setProvider("noop");
        candidate.setModel("deterministic-validation-only");
        candidate.setDimension(dimension);
        return new ModelTarget(candidate.getId(), candidate, null);
    }
}
