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

package com.nageoffer.ai.ragent.rag.core.vector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.rag.core.retrieve.cache.RetrievalIndexVersionCoordinator;
import com.nageoffer.ai.ragent.rag.core.generation.IndexGenerationService;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgVectorStoreCacheInvalidationTest {
    @Test
    void shouldWrapArticleDeletionWithMutationBarrier() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RetrievalIndexVersionCoordinator coordinator = mock(RetrievalIndexVersionCoordinator.class);
        IndexGenerationService generations = mock(IndexGenerationService.class);
        var token = new RetrievalIndexVersionCoordinator.Mutation(5, true);
        when(coordinator.beginMutation()).thenReturn(token);
        when(jdbc.update(anyString(), anyString(), anyString()))
                .thenReturn(1);
        when(generations.writeCollections("articles")).thenReturn(java.util.List.of("articles"));
        PgVectorStoreService service = new PgVectorStoreService(jdbc, new ObjectMapper(), coordinator, generations);

        service.deleteDocumentVectors("articles", "article-1001");

        InOrder order = inOrder(coordinator, jdbc);
        order.verify(coordinator).beginMutation();
        order.verify(jdbc).update(contains("DELETE FROM t_knowledge_vector"), eq("articles"), eq("article-1001"));
        order.verify(jdbc).update(contains("DELETE FROM t_vector_document_identity"),
                eq("articles"), eq("article-1001"));
        order.verify(coordinator).completeMutation(token);
    }

    @Test
    void shouldAbortBarrierWhenIndexDeletionFails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RetrievalIndexVersionCoordinator coordinator = mock(RetrievalIndexVersionCoordinator.class);
        IndexGenerationService generations = mock(IndexGenerationService.class);
        var token = new RetrievalIndexVersionCoordinator.Mutation(5, true);
        when(coordinator.beginMutation()).thenReturn(token);
        when(jdbc.update(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("pg down"));
        when(generations.writeCollections("articles")).thenReturn(java.util.List.of("articles"));
        PgVectorStoreService service = new PgVectorStoreService(jdbc, new ObjectMapper(), coordinator, generations);

        assertThrows(IllegalStateException.class,
                () -> service.deleteDocumentVectors("articles", "article-1001"));

        verify(coordinator).abortMutation(token);
        verify(coordinator, never()).completeMutation(token);
    }
}
