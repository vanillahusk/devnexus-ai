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

package com.nageoffer.ai.ragent.infra.embedding;

import com.nageoffer.ai.ragent.infra.enums.ModelProvider;
import com.nageoffer.ai.ragent.infra.model.ModelTarget;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * 资源受限环境的确定性向量客户端。
 *
 * <p>该实现只用于验证文档上传、分块、向量入库、更新替换和删除等基础设施闭环。
 * 它不表达文本语义，不能用于检索质量评估或生产环境。默认不注册，必须显式开启。</p>
 */
@Service
@ConditionalOnProperty(name = "ai.validation.embedding.enabled", havingValue = "true")
public class DeterministicValidationEmbeddingClient implements EmbeddingClient {

    private static final int MAX_DIMENSION = 4096;

    @Override
    public String provider() {
        return ModelProvider.NOOP.getId();
    }

    @Override
    public List<Float> embed(String text, ModelTarget target) {
        int dimension = requireDimension(target);
        byte[] input = text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8);
        float[] values = new float[dimension];
        double normSquared = 0D;

        for (int offset = 0, block = 0; offset < dimension; block++) {
            byte[] digest = digest(input, block);
            for (int index = 0; index < digest.length && offset < dimension; index++, offset++) {
                float value = digest[index] / 128F;
                values[offset] = value;
                normSquared += value * value;
            }
        }

        double norm = Math.sqrt(normSquared);
        List<Float> vector = new ArrayList<>(dimension);
        for (float value : values) {
            vector.add((float) (value / norm));
        }
        return vector;
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, ModelTarget target) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        return texts.stream().map(text -> embed(text, target)).toList();
    }

    private int requireDimension(ModelTarget target) {
        Integer dimension = target == null || target.candidate() == null
                ? null
                : target.candidate().getDimension();
        if (dimension == null || dimension <= 0 || dimension > MAX_DIMENSION) {
            throw new IllegalArgumentException("Validation embedding dimension must be between 1 and " + MAX_DIMENSION);
        }
        return dimension;
    }

    private byte[] digest(byte[] input, int block) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(input);
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(block).array());
            return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }
}
