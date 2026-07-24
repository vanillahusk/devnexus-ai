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
import com.nageoffer.ai.ragent.infra.token.TokenCounterService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/** 使用 Token 预算选择完整 Chunk，避免按固定字符截断代码块和表格。 */
@Component
@RequiredArgsConstructor
public class ContextBudgetPolicy {
    private final TokenCounterService tokenCounterService;

    public Selection select(List<RetrievedChunk> ranked, int maxChunks, int maxTokens) {
        int chunkLimit = Math.max(1, maxChunks);
        int tokenLimit = Math.max(1, maxTokens);
        int used = 0;
        List<RetrievedChunk> selected = new ArrayList<>();
        for (RetrievedChunk chunk : ranked) {
            if (selected.size() >= chunkLimit) break;
            int tokens = tokenCount(chunk);
            if (tokens <= 0 || used + tokens > tokenLimit) continue;
            selected.add(chunk);
            used += tokens;
        }
        return new Selection(List.copyOf(selected), used);
    }

    private int tokenCount(RetrievedChunk chunk) {
        Object metadataCount = chunk.getMetadata() == null ? null : chunk.getMetadata().get("tokenCount");
        if (metadataCount instanceof Number number && number.intValue() > 0) return number.intValue();
        if (metadataCount != null) {
            try {
                int parsed = Integer.parseInt(metadataCount.toString());
                if (parsed > 0) return parsed;
            } catch (NumberFormatException ignored) {
                // 元数据不可信时重新估算。
            }
        }
        Integer estimated = tokenCounterService.countTokens(chunk.getText());
        return estimated == null ? 0 : Math.max(0, estimated);
    }

    public record Selection(List<RetrievedChunk> chunks, int tokenCount) {
    }
}
