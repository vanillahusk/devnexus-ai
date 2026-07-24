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
import com.nageoffer.ai.ragent.core.chunk.VectorChunk;
import com.nageoffer.ai.ragent.rag.core.generation.IndexGenerationService;
import com.nageoffer.ai.ragent.rag.core.retrieve.cache.RetrievalIndexVersionCoordinator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "rag.vector.type", havingValue = "pg")
public class PgVectorStoreService implements VectorStoreService {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final RetrievalIndexVersionCoordinator cacheVersionCoordinator;
    private final IndexGenerationService indexGenerationService;

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        mutateIndex(() -> {
            rememberDocumentIdentity(collectionName, docId, chunks.get(0));
            // noinspection SqlDialectInspection,SqlNoDataSourceInspection
            for (String generation : indexGenerationService.writeCollections(collectionName)) {
                jdbcTemplate.batchUpdate(
                        "INSERT INTO t_knowledge_vector (collection_name, id, content, metadata, embedding) "
                                + "VALUES (?, ?, ?, ?::jsonb, ?::vector) "
                                + "ON CONFLICT (collection_name, id) DO UPDATE SET content = EXCLUDED.content, "
                                + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                        chunks, chunks.size(), (ps, chunk) -> {
                            ps.setString(1, generation);
                            ps.setString(2, chunk.getChunkId());
                            ps.setString(3, chunk.getContent());
                            ps.setString(4, buildMetadataJson(collectionName, generation, docId, chunk));
                            ps.setString(5, toVectorLiteral(chunk.getEmbedding()));
                        });
            }
        });

        log.info("批量写入向量到 PostgreSQL，collectionName={}, docId={}, count={}", collectionName, docId, chunks.size());
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        Optional<DocumentIdentity> identity = findDocumentIdentity(collectionName, docId);
        final int[] deleted = new int[1];
        mutateIndex(() -> {
            // noinspection SqlDialectInspection,SqlNoDataSourceInspection
            for (String generation : indexGenerationService.writeCollections(collectionName)) {
                if (identity.filter(DocumentIdentity::article).isPresent()) {
                    DocumentIdentity article = identity.orElseThrow();
                    deleted[0] += jdbcTemplate.update(
                            "DELETE FROM t_knowledge_vector WHERE collection_name = ? "
                                    + "AND metadata->>'sourceType' = 'ARTICLE' "
                                    + "AND metadata->>'articleId' = ? "
                                    + "AND COALESCE(NULLIF(metadata->>'articleVersion', ''), '0')::bigint <= ?",
                            generation, article.businessId(), article.businessVersion());
                } else {
                    deleted[0] += jdbcTemplate.update(
                            "DELETE FROM t_knowledge_vector WHERE collection_name = ? AND metadata->>'doc_id' = ?",
                            generation, docId);
                }
            }
            jdbcTemplate.update("DELETE FROM t_vector_document_identity WHERE logical_collection = ? AND doc_id = ?",
                    collectionName, docId);
        });
        log.info("删除文档向量，collectionName={}, docId={}, deleted={}", collectionName, docId, deleted[0]);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        mutateIndex(() -> {
            // noinspection SqlDialectInspection,SqlNoDataSourceInspection
            for (String generation : indexGenerationService.writeCollections(collectionName)) {
                jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE collection_name = ? AND id = ?",
                        generation, chunkId);
            }
        });
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
        mutateIndex(() -> {
            rememberDocumentIdentity(collectionName, docId, chunk);
            // noinspection SqlDialectInspection,SqlNoDataSourceInspection
            for (String generation : indexGenerationService.writeCollections(collectionName)) {
                jdbcTemplate.update(
                        "INSERT INTO t_knowledge_vector (collection_name, id, content, metadata, embedding) "
                                + "VALUES (?, ?, ?, ?::jsonb, ?::vector) "
                                + "ON CONFLICT (collection_name, id) DO UPDATE SET content = EXCLUDED.content, "
                                + "metadata = EXCLUDED.metadata, embedding = EXCLUDED.embedding",
                        generation, chunk.getChunkId(), chunk.getContent(),
                        buildMetadataJson(collectionName, generation, docId, chunk),
                        toVectorLiteral(chunk.getEmbedding()));
            }
        });
    }

    private void mutateIndex(Runnable mutation) {
        RetrievalIndexVersionCoordinator.Mutation token = cacheVersionCoordinator.beginMutation();
        try {
            mutation.run();
        } catch (RuntimeException failure) {
            cacheVersionCoordinator.abortMutation(token);
            throw failure;
        }
        cacheVersionCoordinator.completeMutation(token);
    }

    private String buildMetadataJson(String collectionName, String generation, String docId, VectorChunk chunk) {
        Map<String, Object> meta = new LinkedHashMap<>();
        if (chunk.getMetadata() != null) {
            meta.putAll(chunk.getMetadata());
        }

        meta.put("collection_name", collectionName);
        meta.put("index_generation", generation);
        meta.put("doc_id", docId);
        meta.put("chunk_index", chunk.getIndex());
        try {
            return objectMapper.writeValueAsString(meta);
        } catch (Exception e) {
            throw new RuntimeException("元数据序列化失败", e);
        }
    }

    private void rememberDocumentIdentity(String collectionName, String docId, VectorChunk chunk) {
        Map<String, Object> metadata = chunk.getMetadata();
        String sourceType = text(metadata, "sourceType");
        String businessId = "ARTICLE".equals(sourceType) ? text(metadata, "articleId") : null;
        Long businessVersion = "ARTICLE".equals(sourceType) ? number(metadata, "articleVersion") : null;
        jdbcTemplate.update(
                "INSERT INTO t_vector_document_identity "
                        + "(logical_collection, doc_id, source_type, business_id, business_version, updated_at) "
                        + "VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP) "
                        + "ON CONFLICT (logical_collection, doc_id) DO UPDATE SET source_type = EXCLUDED.source_type, "
                        + "business_id = EXCLUDED.business_id, business_version = EXCLUDED.business_version, "
                        + "updated_at = CURRENT_TIMESTAMP",
                collectionName, docId, sourceType, businessId, businessVersion);
    }

    private Optional<DocumentIdentity> findDocumentIdentity(String collectionName, String docId) {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT source_type, business_id, business_version FROM t_vector_document_identity "
                        + "WHERE logical_collection = ? AND doc_id = ?",
                collectionName, docId);
        if (rows.isEmpty()) return Optional.empty();
        Map<String, Object> row = rows.get(0);
        return Optional.of(new DocumentIdentity(
                row.get("source_type") == null ? null : row.get("source_type").toString(),
                row.get("business_id") == null ? null : row.get("business_id").toString(),
                row.get("business_version") instanceof Number value ? value.longValue() : null));
    }

    private String text(Map<String, Object> metadata, String key) {
        if (metadata == null || metadata.get(key) == null) return null;
        String value = metadata.get(key).toString();
        return value.isBlank() ? null : value;
    }

    private Long number(Map<String, Object> metadata, String key) {
        if (metadata == null) return null;
        Object value = metadata.get(key);
        if (value instanceof Number number) return number.longValue();
        try {
            return value == null ? null : Long.valueOf(value.toString());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private record DocumentIdentity(String sourceType, String businessId, Long businessVersion) {
        boolean article() {
            return "ARTICLE".equals(sourceType) && businessId != null && businessVersion != null;
        }
    }

    private String toVectorLiteral(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        return sb.append("]").toString();
    }
}
