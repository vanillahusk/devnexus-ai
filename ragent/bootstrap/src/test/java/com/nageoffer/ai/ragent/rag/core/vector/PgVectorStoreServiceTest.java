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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RAGENT_REAL_PG_VECTOR_TEST", matches = "true")
public class PgVectorStoreServiceTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void testChineseCharacterInsertion() {
        // 准备测试数据
        String chunkId = "test_chunk_001";
        String collectionName = "generation-test";
        String docId = "test_doc_001";
        String content = "这是一段中文测试内容，包含各种字符：你好世界！";

        // 创建一个简单的向量
        float[] embedding = new float[1536];
        for (int i = 0; i < 1536; i++) {
            embedding[i] = 0.1f;
        }

        // 构建向量字符串
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        String vectorLiteral = sb.append("]").toString();

        // 插入数据
        String sql = "INSERT INTO t_knowledge_vector (collection_name, id, content, metadata, embedding) "
                + "VALUES (?, ?, ?, ?::jsonb, ?::vector)";

        jdbcTemplate.update(sql, collectionName, chunkId, content,
                "{\"collection_name\":\"generation-test\",\"doc_id\":\"" + docId + "\"}", vectorLiteral);

        // 查询验证
        String querySql = "SELECT id, content FROM t_knowledge_vector WHERE collection_name = ? AND id = ?";
        List<Map<String, Object>> results = jdbcTemplate.queryForList(querySql, collectionName, chunkId);

        assertEquals(1, results.size());
        assertEquals(chunkId, results.get(0).get("id"));
        assertEquals(content, results.get(0).get("content"));

        // 清理测试数据
        // noinspection SqlDialectInspection,SqlNoDataSourceInspection
        jdbcTemplate.update("DELETE FROM t_knowledge_vector WHERE collection_name = ? AND id = ?",
                collectionName, chunkId);
    }
}
