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

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IndexGenerationServiceTest {
    @Test
    void shouldKeepReadingActiveAndDualWriteDuringRebuild() {
        IndexGenerationRepository repository = mock(IndexGenerationRepository.class);
        IndexGenerationProperties properties = new IndexGenerationProperties();
        properties.setEnabled(true);
        IndexGenerationService service = new IndexGenerationService(repository, properties);
        IndexGenerationState state = new IndexGenerationState("articles", "articles", "articles--g2", null,
                IndexGenerationStatus.BUILDING, 100, 110, 120, false,
                Instant.now(), null, Instant.now());
        when(repository.find("articles")).thenReturn(java.util.Optional.of(state));

        assertEquals("articles", service.readCollection("articles"));
        assertEquals(List.of("articles", "articles--g2"), service.writeCollections("articles"));
        assertEquals("articles--g2", service.rebuildingCollection("articles").orElseThrow());
    }

    @Test
    void disabledModeMustPreserveLegacySingleGenerationBehavior() {
        IndexGenerationRepository repository = mock(IndexGenerationRepository.class);
        IndexGenerationProperties properties = new IndexGenerationProperties();
        IndexGenerationService service = new IndexGenerationService(repository, properties);

        assertEquals("articles", service.readCollection("articles"));
        assertEquals(List.of("articles"), service.writeCollections("articles"));
        assertThrows(IllegalStateException.class, () -> service.begin("articles", "g2", 10));
    }

    @Test
    void physicalGenerationMustRemainCompatibleWithCollectionLengthBoundary() {
        IndexGenerationRepository repository = mock(IndexGenerationRepository.class);
        IndexGenerationProperties properties = new IndexGenerationProperties();
        properties.setEnabled(true);
        IndexGenerationService service = new IndexGenerationService(repository, properties);

        assertThrows(IllegalArgumentException.class,
                () -> service.begin("a".repeat(60), "generation-too-long", 10));
    }
}
