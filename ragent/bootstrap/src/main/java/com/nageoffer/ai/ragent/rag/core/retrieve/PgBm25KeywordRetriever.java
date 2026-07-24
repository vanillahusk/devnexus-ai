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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nageoffer.ai.ragent.framework.convention.RetrievedChunk;
import com.nageoffer.ai.ragent.rag.config.RAGDefaultProperties;
import com.nageoffer.ai.ragent.rag.core.generation.IndexGenerationService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgBm25KeywordRetriever implements KeywordRetriever {
    private static final int MAX_CANDIDATES = 5000;
    private final JdbcTemplate jdbcTemplate;
    private final RAGDefaultProperties ragDefaultProperties;
    private final ObjectMapper objectMapper;
    private final IndexGenerationService indexGenerationService;
    private final Bm25Scorer scorer = new Bm25Scorer();

    @Override
    public List<RetrievedChunk> retrieve(RetrieveRequest request) {
        String collectionName = request.getCollectionName() == null || request.getCollectionName().isBlank()
                ? ragDefaultProperties.getCollectionName() : request.getCollectionName();
        String activeGeneration = indexGenerationService.readCollection(collectionName);
        PgMetadataFilters.FilterClause filters = PgMetadataFilters.build(request.getMetadataFilters());
        String sql = "SELECT id, content, metadata::text AS metadata_json FROM t_knowledge_vector "
                + "WHERE collection_name = ?" + filters.sql() + " LIMIT ?";
        List<Object> arguments = new ArrayList<>();
        arguments.add(activeGeneration);
        arguments.addAll(filters.arguments());
        arguments.add(MAX_CANDIDATES);
        List<RetrievedChunk> candidates = jdbcTemplate.query(sql, (rs, rowNum) -> RetrievedChunk.builder()
                .id(rs.getString("id")).text(rs.getString("content"))
                .metadata(parseMetadata(rs.getString("metadata_json"))).score(0F).build(), arguments.toArray());
        return scorer.score(request.getQuery(), candidates, request.getTopK());
    }

    private Map<String, Object> parseMetadata(String json) {
        if (json == null || json.isBlank()) return new LinkedHashMap<>();
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new IllegalStateException("关键词索引元数据不是合法JSON", e);
        }
    }
}
