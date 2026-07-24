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
import com.nageoffer.ai.ragent.rag.core.generation.IndexGenerationService;
import com.nageoffer.ai.ragent.rag.core.retrieve.cache.RetrievalIndexVersionCoordinator;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PgIndexGenerationRoutingTest {
    @Test
    void incrementalDeleteMustReachActiveAndBuildingGenerations() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RetrievalIndexVersionCoordinator cacheBarrier = mock(RetrievalIndexVersionCoordinator.class);
        IndexGenerationService generations = mock(IndexGenerationService.class);
        var mutation = new RetrievalIndexVersionCoordinator.Mutation(7, true);
        when(cacheBarrier.beginMutation()).thenReturn(mutation);
        when(generations.writeCollections("articles")).thenReturn(List.of("articles", "articles--g2"));
        PgVectorStoreService service = new PgVectorStoreService(
                jdbc, new ObjectMapper(), cacheBarrier, generations);

        service.deleteDocumentVectors("articles", "article-1001");

        verify(jdbc).update(contains("collection_name = ?"), eq("articles"), eq("article-1001"));
        verify(jdbc).update(contains("collection_name = ?"), eq("articles--g2"), eq("article-1001"));
        verify(cacheBarrier).completeMutation(mutation);
    }

    @Test
    void articleDeleteMustRemoveSameOrOlderBusinessVersionAcrossDifferentRemoteDocIds() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        RetrievalIndexVersionCoordinator cacheBarrier = mock(RetrievalIndexVersionCoordinator.class);
        IndexGenerationService generations = mock(IndexGenerationService.class);
        var mutation = new RetrievalIndexVersionCoordinator.Mutation(8, true);
        when(cacheBarrier.beginMutation()).thenReturn(mutation);
        when(generations.writeCollections("articles")).thenReturn(List.of("articles", "articles--g2"));
        when(jdbc.queryForList(contains("t_vector_document_identity"), eq("articles"), eq("remote-doc-v7")))
                .thenReturn(List.of(Map.of(
                        "source_type", "ARTICLE",
                        "business_id", "1001",
                        "business_version", 7L)));
        PgVectorStoreService service = new PgVectorStoreService(
                jdbc, new ObjectMapper(), cacheBarrier, generations);

        service.deleteDocumentVectors("articles", "remote-doc-v7");

        verify(jdbc).update(contains("articleVersion"), eq("articles"), eq("1001"), eq(7L));
        verify(jdbc).update(contains("articleVersion"), eq("articles--g2"), eq("1001"), eq(7L));
        verify(cacheBarrier).completeMutation(mutation);
    }
}
