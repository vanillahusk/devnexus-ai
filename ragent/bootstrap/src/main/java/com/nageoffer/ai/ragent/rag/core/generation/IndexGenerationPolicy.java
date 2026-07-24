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

import java.time.Duration;
import java.time.Instant;

final class IndexGenerationPolicy {
    private IndexGenerationPolicy() {
    }

    static IndexGenerationState begin(IndexGenerationState current, String generation,
                                      long startWatermark, Instant now) {
        if (current.rebuilding()) {
            throw new IllegalStateException("已有索引Generation正在重建");
        }
        if (generation.equals(current.activeGeneration())) {
            throw new IllegalArgumentException("新Generation不能与当前活动Generation相同");
        }
        return new IndexGenerationState(current.logicalCollection(), current.activeGeneration(), generation,
                current.previousGeneration(), IndexGenerationStatus.BUILDING,
                startWatermark, startWatermark, startWatermark, false,
                now, current.switchedAt(), now);
    }

    static IndexGenerationState progress(IndexGenerationState current, String generation,
                                         long appliedWatermark, long targetWatermark,
                                         boolean reconciled, Instant now) {
        requireBuilding(current, generation);
        if (appliedWatermark < current.appliedWatermark()) {
            throw new IllegalArgumentException("增量应用水位不能回退");
        }
        if (targetWatermark < current.targetWatermark()) {
            throw new IllegalArgumentException("目标水位不能回退");
        }
        if (appliedWatermark > targetWatermark) {
            throw new IllegalArgumentException("增量应用水位不能超过目标水位");
        }
        boolean caughtUp = appliedWatermark >= targetWatermark;
        boolean verified = caughtUp && reconciled;
        return new IndexGenerationState(current.logicalCollection(), current.activeGeneration(), generation,
                current.previousGeneration(), verified ? IndexGenerationStatus.READY : IndexGenerationStatus.BUILDING,
                current.startWatermark(), appliedWatermark, targetWatermark, verified,
                current.rebuildStartedAt(), current.switchedAt(), now);
    }

    static IndexGenerationState activate(IndexGenerationState current, String generation, Instant now) {
        requireBuilding(current, generation);
        if (current.status() != IndexGenerationStatus.READY
                || !current.reconciled()
                || current.appliedWatermark() < current.targetWatermark()) {
            throw new IllegalStateException("新Generation尚未追平水位并完成对账，禁止切换");
        }
        return new IndexGenerationState(current.logicalCollection(), generation, null,
                current.activeGeneration(), IndexGenerationStatus.ACTIVE,
                current.startWatermark(), current.appliedWatermark(), current.targetWatermark(), true,
                current.rebuildStartedAt(), now, now);
    }

    static IndexGenerationState fail(IndexGenerationState current, String generation, Instant now) {
        requireBuilding(current, generation);
        return new IndexGenerationState(current.logicalCollection(), current.activeGeneration(), null,
                current.previousGeneration(), IndexGenerationStatus.FAILED,
                current.startWatermark(), current.appliedWatermark(), current.targetWatermark(), false,
                current.rebuildStartedAt(), current.switchedAt(), now);
    }

    static IndexGenerationState rollback(IndexGenerationState current, Duration retention, Instant now) {
        if (current.rebuilding()) {
            throw new IllegalStateException("重建进行中不能回滚活动Generation");
        }
        if (current.previousGeneration() == null || current.switchedAt() == null) {
            throw new IllegalStateException("不存在可回滚的旧Generation");
        }
        if (current.switchedAt().plus(retention).isBefore(now)) {
            throw new IllegalStateException("旧Generation已超过回滚保留期");
        }
        return new IndexGenerationState(current.logicalCollection(), current.previousGeneration(), null,
                current.activeGeneration(), IndexGenerationStatus.ACTIVE,
                current.startWatermark(), current.appliedWatermark(), current.targetWatermark(), true,
                current.rebuildStartedAt(), now, now);
    }

    private static void requireBuilding(IndexGenerationState current, String generation) {
        if (!current.rebuilding() || !generation.equals(current.buildingGeneration())) {
            throw new IllegalStateException("目标Generation不是当前重建Generation");
        }
    }
}
