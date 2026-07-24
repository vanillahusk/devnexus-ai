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
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ReciprocalRankFusionTest {
    @Test
    void shouldPromoteChunkFoundByBothChannelsWithoutComparingRawScores() {
        List<RetrievedChunk> result = new ReciprocalRankFusion().fuse(List.of(
                List.of(chunk("keyword-only", 100F), chunk("shared", 1F)),
                List.of(chunk("dense-only", 0.99F), chunk("shared", 0.10F))), 3);

        assertEquals("shared", result.get(0).getId());
        assertEquals(3, result.size());
    }

    private RetrievedChunk chunk(String id, float score) {
        return new RetrievedChunk(id, id + " text", score);
    }
}
