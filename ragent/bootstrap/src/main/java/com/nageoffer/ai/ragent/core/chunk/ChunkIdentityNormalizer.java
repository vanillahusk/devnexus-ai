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

package com.nageoffer.ai.ragent.core.chunk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 为同一文档的确定性分块生成稳定、全局安全的 Chunk 身份和基础元数据。 */
public final class ChunkIdentityNormalizer {
    private ChunkIdentityNormalizer() {
    }

    public static void normalize(String documentNamespace, List<VectorChunk> chunks) {
        if (documentNamespace == null || documentNamespace.isBlank()) {
            throw new IllegalArgumentException("documentNamespace must not be blank");
        }
        if (chunks == null) return;
        for (int i = 0; i < chunks.size(); i++) {
            VectorChunk chunk = chunks.get(i);
            if (chunk == null || chunk.getContent() == null || chunk.getContent().isBlank()) {
                throw new IllegalArgumentException("chunk content must not be blank, index=" + i);
            }
            int index = chunk.getIndex() == null ? i : chunk.getIndex();
            String contentHash = contentHash(chunk.getContent());
            String identitySeed = documentNamespace + '|' + index + '|' + contentHash;
            chunk.setIndex(index);
            // t_knowledge_chunk.id 的跨数据库契约为 VARCHAR(20)，使用 80-bit SHA-256 前缀。
            chunk.setChunkId(contentHash(identitySeed).substring(0, 20));
            Map<String, Object> metadata = new LinkedHashMap<>();
            if (chunk.getMetadata() != null) metadata.putAll(chunk.getMetadata());
            metadata.put("contentHash", contentHash);
            metadata.put("charCount", chunk.getContent().length());
            chunk.setMetadata(metadata);
        }
    }

    public static String contentHash(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format("%02x", value));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }
}
