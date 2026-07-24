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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContextBudgetPolicyTest {
    private final ContextBudgetPolicy policy = new ContextBudgetPolicy(text -> text == null ? 0 : text.length());

    @Test
    void shouldKeepWholeChunksWithinTokenBudgetAndSkipOversizedCandidate() {
        List<RetrievedChunk> chunks = List.of(
                chunk("too-large", "ignored", 80),
                chunk("first", "12345", 5),
                chunk("second", "1234", 4));

        ContextBudgetPolicy.Selection selection = policy.select(chunks, 3, 9);

        assertEquals(List.of("first", "second"), selection.chunks().stream().map(RetrievedChunk::getId).toList());
        assertEquals(9, selection.tokenCount());
    }

    private RetrievedChunk chunk(String id, String text, int tokenCount) {
        return RetrievedChunk.builder().id(id).text(text).score(1F)
                .metadata(Map.of("tokenCount", tokenCount)).build();
    }
}
