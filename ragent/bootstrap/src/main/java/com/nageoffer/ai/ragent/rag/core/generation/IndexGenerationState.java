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

package com.nageoffer.ai.ragent.rag.core.generation;

import java.time.Instant;

public record IndexGenerationState(
        String logicalCollection,
        String activeGeneration,
        String buildingGeneration,
        String previousGeneration,
        IndexGenerationStatus status,
        long startWatermark,
        long appliedWatermark,
        long targetWatermark,
        boolean reconciled,
        Instant rebuildStartedAt,
        Instant switchedAt,
        Instant updatedAt) {

    public static IndexGenerationState initial(String logicalCollection, Instant now) {
        return new IndexGenerationState(logicalCollection, logicalCollection, null, null,
                IndexGenerationStatus.ACTIVE, 0, 0, 0, false, null, null, now);
    }

    public boolean rebuilding() {
        return status == IndexGenerationStatus.BUILDING || status == IndexGenerationStatus.READY;
    }
}
