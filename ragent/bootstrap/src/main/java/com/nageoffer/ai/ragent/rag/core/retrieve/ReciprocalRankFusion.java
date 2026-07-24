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

package com.nageoffer.ai.ragent.rag.core.retrieve;

import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** RRF 只使用各通道排名，避免直接比较不可比的 BM25 与余弦分数。 */
public final class ReciprocalRankFusion {
    private static final int DEFAULT_K = 60;

    public List<RetrievedChunk> fuse(List<List<RetrievedChunk>> channels, int topK) {
        Map<String, Accumulator> merged = new LinkedHashMap<>();
        if (channels != null) {
            for (List<RetrievedChunk> channel : channels) {
                if (channel == null) continue;
                for (int rank = 0; rank < channel.size(); rank++) {
                    RetrievedChunk chunk = channel.get(rank);
                    if (chunk == null || chunk.getId() == null) continue;
                    Accumulator accumulator = merged.computeIfAbsent(chunk.getId(), ignored -> new Accumulator(chunk));
                    accumulator.score += 1D / (DEFAULT_K + rank + 1D);
                }
            }
        }
        return merged.values().stream().sorted(Comparator.comparingDouble(Accumulator::score).reversed())
                .limit(Math.max(1, topK)).map(Accumulator::result).toList();
    }

    private static final class Accumulator {
        private final RetrievedChunk source;
        private double score;

        private Accumulator(RetrievedChunk source) {
            this.source = source;
        }

        private double score() {
            return score;
        }

        private RetrievedChunk result() {
            return RetrievedChunk.builder().id(source.getId()).text(source.getText())
                    .metadata(source.getMetadata()).score((float) score).build();
        }
    }
}
