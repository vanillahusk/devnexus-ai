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

import com.nageoffer.ai.ragent.framework.exception.ClientException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** PostgreSQL JSONB 等值过滤器，只接受知识索引公开的低基数字段。 */
final class PgMetadataFilters {
    private static final Set<String> ALLOWED_KEYS = Set.of(
            "sourceType", "status", "articleId", "articleVersion", "categoryId", "tagIds", "doc_id");

    private PgMetadataFilters() {
    }

    static FilterClause build(Map<String, Object> filters) {
        StringBuilder sql = new StringBuilder();
        List<Object> arguments = new ArrayList<>();
        if (filters == null || filters.isEmpty()) return new FilterClause("", arguments);
        filters.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!ALLOWED_KEYS.contains(entry.getKey())) {
                throw new ClientException("不允许的检索元数据字段: " + entry.getKey());
            }
            if (entry.getValue() == null) {
                throw new ClientException("检索元数据值不能为空: " + entry.getKey());
            }
            sql.append(" AND jsonb_extract_path_text(metadata, ?) = ?");
            arguments.add(entry.getKey());
            arguments.add(String.valueOf(entry.getValue()));
        });
        return new FilterClause(sql.toString(), arguments);
    }

    record FilterClause(String sql, List<Object> arguments) {
    }
}
