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

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class IndexGenerationRepository {
    private static final String COLUMNS = "logical_collection, active_generation, building_generation, "
            + "previous_generation, status, start_watermark, applied_watermark, target_watermark, reconciled, "
            + "rebuild_started_at, switched_at, updated_at";

    private final JdbcTemplate jdbcTemplate;

    void ensureInitial(String logicalCollection, Instant now) {
        jdbcTemplate.update("INSERT INTO t_index_generation (logical_collection, active_generation, status, updated_at) "
                        + "VALUES (?, ?, 'ACTIVE', ?) ON CONFLICT (logical_collection) DO NOTHING",
                logicalCollection, logicalCollection, Timestamp.from(now));
    }

    Optional<IndexGenerationState> find(String logicalCollection) {
        return query("SELECT " + COLUMNS + " FROM t_index_generation WHERE logical_collection = ?", logicalCollection);
    }

    Optional<IndexGenerationState> findForUpdate(String logicalCollection) {
        return query("SELECT " + COLUMNS + " FROM t_index_generation WHERE logical_collection = ? FOR UPDATE",
                logicalCollection);
    }

    void save(IndexGenerationState state) {
        int updated = jdbcTemplate.update("UPDATE t_index_generation SET active_generation = ?, building_generation = ?, "
                        + "previous_generation = ?, status = ?, start_watermark = ?, applied_watermark = ?, "
                        + "target_watermark = ?, reconciled = ?, rebuild_started_at = ?, switched_at = ?, updated_at = ? "
                        + "WHERE logical_collection = ?",
                state.activeGeneration(), state.buildingGeneration(), state.previousGeneration(), state.status().name(),
                state.startWatermark(), state.appliedWatermark(), state.targetWatermark(), state.reconciled(),
                timestamp(state.rebuildStartedAt()), timestamp(state.switchedAt()), timestamp(state.updatedAt()),
                state.logicalCollection());
        if (updated != 1) {
            throw new IllegalStateException("索引Generation状态更新失败");
        }
    }

    Map<Long, IndexGenerationService.ArticleVersionSummary> articleVersions(String physicalCollection) {
        Map<Long, IndexGenerationService.ArticleVersionSummary> result = new LinkedHashMap<>();
        jdbcTemplate.query("SELECT (metadata->>'articleId')::bigint AS article_id, "
                        + "MIN((metadata->>'articleVersion')::bigint) AS min_version, "
                        + "MAX((metadata->>'articleVersion')::bigint) AS max_version, COUNT(*) AS chunk_count "
                        + "FROM t_knowledge_vector WHERE collection_name = ? "
                        + "AND metadata->>'sourceType' = 'ARTICLE' AND metadata->>'status' = 'ONLINE' "
                        + "AND metadata->>'articleId' ~ '^[0-9]+$' "
                        + "AND metadata->>'articleVersion' ~ '^[0-9]+$' "
                        + "GROUP BY metadata->>'articleId' ORDER BY article_id",
                (RowCallbackHandler) rs -> result.put(rs.getLong("article_id"), new IndexGenerationService.ArticleVersionSummary(
                        rs.getLong("min_version"), rs.getLong("max_version"), rs.getLong("chunk_count"))),
                physicalCollection);
        return result;
    }

    private Optional<IndexGenerationState> query(String sql, String logicalCollection) {
        List<IndexGenerationState> rows = jdbcTemplate.query(sql, (rs, rowNum) -> new IndexGenerationState(
                rs.getString("logical_collection"), rs.getString("active_generation"),
                rs.getString("building_generation"), rs.getString("previous_generation"),
                IndexGenerationStatus.valueOf(rs.getString("status")),
                rs.getLong("start_watermark"), rs.getLong("applied_watermark"), rs.getLong("target_watermark"),
                rs.getBoolean("reconciled"), instant(rs.getTimestamp("rebuild_started_at")),
                instant(rs.getTimestamp("switched_at")), instant(rs.getTimestamp("updated_at"))), logicalCollection);
        return rows.stream().findFirst();
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }
}
