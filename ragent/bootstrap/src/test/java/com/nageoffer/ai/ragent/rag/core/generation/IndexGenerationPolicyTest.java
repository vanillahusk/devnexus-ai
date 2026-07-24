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

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndexGenerationPolicyTest {
    private static final Instant T0 = Instant.parse("2026-07-16T00:00:00Z");

    @Test
    void shouldSwitchOnlyAfterWatermarkCatchUpAndReconciliation() {
        IndexGenerationState initial = IndexGenerationState.initial("articles", T0);
        IndexGenerationState building = IndexGenerationPolicy.begin(initial, "articles--g2", 100, T0.plusSeconds(1));

        IndexGenerationState caughtUpButNotReconciled = IndexGenerationPolicy.progress(
                building, "articles--g2", 120, 120, false, T0.plusSeconds(2));
        assertEquals(IndexGenerationStatus.BUILDING, caughtUpButNotReconciled.status());
        assertThrows(IllegalStateException.class, () -> IndexGenerationPolicy.activate(
                caughtUpButNotReconciled, "articles--g2", T0.plusSeconds(3)));

        IndexGenerationState ready = IndexGenerationPolicy.progress(
                caughtUpButNotReconciled, "articles--g2", 120, 120, true, T0.plusSeconds(3));
        assertEquals(IndexGenerationStatus.READY, ready.status());

        IndexGenerationState active = IndexGenerationPolicy.activate(ready, "articles--g2", T0.plusSeconds(4));
        assertEquals("articles--g2", active.activeGeneration());
        assertEquals("articles", active.previousGeneration());
        assertFalse(active.rebuilding());
    }

    @Test
    void shouldRejectWatermarkRegressionAndAppliedWatermarkAheadOfTarget() {
        IndexGenerationState building = IndexGenerationPolicy.begin(
                IndexGenerationState.initial("articles", T0), "articles--g2", 100, T0);
        IndexGenerationState progressed = IndexGenerationPolicy.progress(
                building, "articles--g2", 110, 120, false, T0.plusSeconds(1));

        assertThrows(IllegalArgumentException.class, () -> IndexGenerationPolicy.progress(
                progressed, "articles--g2", 109, 120, false, T0.plusSeconds(2)));
        assertThrows(IllegalArgumentException.class, () -> IndexGenerationPolicy.progress(
                progressed, "articles--g2", 121, 120, true, T0.plusSeconds(2)));
    }

    @Test
    void failedRebuildMustLeaveActiveGenerationUntouched() {
        IndexGenerationState building = IndexGenerationPolicy.begin(
                IndexGenerationState.initial("articles", T0), "articles--g2", 100, T0);

        IndexGenerationState failed = IndexGenerationPolicy.fail(building, "articles--g2", T0.plusSeconds(1));

        assertEquals("articles", failed.activeGeneration());
        assertEquals(IndexGenerationStatus.FAILED, failed.status());
        assertFalse(failed.rebuilding());
    }

    @Test
    void rollbackMustHonorRetentionWindow() {
        IndexGenerationState ready = IndexGenerationPolicy.progress(
                IndexGenerationPolicy.begin(IndexGenerationState.initial("articles", T0), "articles--g2", 100, T0),
                "articles--g2", 100, 100, true, T0.plusSeconds(1));
        IndexGenerationState active = IndexGenerationPolicy.activate(ready, "articles--g2", T0.plusSeconds(2));

        IndexGenerationState rolledBack = IndexGenerationPolicy.rollback(active, Duration.ofHours(24),
                T0.plusSeconds(3));
        assertEquals("articles", rolledBack.activeGeneration());
        assertTrue(rolledBack.reconciled());

        assertThrows(IllegalStateException.class, () -> IndexGenerationPolicy.rollback(
                active, Duration.ofHours(1), T0.plus(Duration.ofHours(2))));
    }
}
