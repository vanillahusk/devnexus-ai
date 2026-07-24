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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class IndexGenerationService {
    private static final Pattern COLLECTION = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_-]{0,62}");
    private static final Pattern GENERATION_LABEL = Pattern.compile("[a-zA-Z0-9][a-zA-Z0-9_-]{0,39}");

    private final IndexGenerationRepository repository;
    private final IndexGenerationProperties properties;

    public String readCollection(String logicalCollection) {
        validateCollection(logicalCollection);
        if (!properties.isEnabled()) return logicalCollection;
        return repository.find(logicalCollection).map(IndexGenerationState::activeGeneration).orElse(logicalCollection);
    }

    public List<String> writeCollections(String logicalCollection) {
        validateCollection(logicalCollection);
        if (!properties.isEnabled()) return List.of(logicalCollection);
        Optional<IndexGenerationState> state = repository.find(logicalCollection);
        if (state.isEmpty()) return List.of(logicalCollection);
        LinkedHashSet<String> targets = new LinkedHashSet<>();
        targets.add(state.get().activeGeneration());
        if (state.get().rebuilding()) targets.add(state.get().buildingGeneration());
        return List.copyOf(targets);
    }

    public Optional<String> rebuildingCollection(String logicalCollection) {
        validateCollection(logicalCollection);
        if (!properties.isEnabled()) return Optional.empty();
        return repository.find(logicalCollection).filter(IndexGenerationState::rebuilding)
                .map(IndexGenerationState::buildingGeneration);
    }

    public Optional<IndexGenerationState> state(String logicalCollection) {
        validateCollection(logicalCollection);
        if (!properties.isEnabled()) return Optional.empty();
        return repository.find(logicalCollection);
    }

    public Map<Long, ArticleVersionSummary> articleVersions(String physicalCollection) {
        requireEnabled();
        validateCollection(physicalCollection);
        return repository.articleVersions(physicalCollection);
    }

    @Transactional
    public IndexGenerationState begin(String logicalCollection, String generationLabel, long startWatermark) {
        requireEnabled();
        validateCollection(logicalCollection);
        if (!GENERATION_LABEL.matcher(generationLabel).matches()) {
            throw new IllegalArgumentException("Generation标签格式不合法");
        }
        if (startWatermark < 0) throw new IllegalArgumentException("起始水位不能为负数");
        String generation = physicalName(logicalCollection, generationLabel);
        Instant now = Instant.now();
        repository.ensureInitial(logicalCollection, now);
        IndexGenerationState next = IndexGenerationPolicy.begin(locked(logicalCollection), generation,
                startWatermark, now);
        repository.save(next);
        return next;
    }

    @Transactional
    public IndexGenerationState recordProgress(String logicalCollection, String generationLabel,
                                               long appliedWatermark, long targetWatermark, boolean reconciled) {
        requireEnabled();
        validateCollection(logicalCollection);
        String generation = physicalName(logicalCollection, generationLabel);
        IndexGenerationState next = IndexGenerationPolicy.progress(locked(logicalCollection), generation,
                appliedWatermark, targetWatermark, reconciled, Instant.now());
        repository.save(next);
        return next;
    }

    @Transactional
    public IndexGenerationState activate(String logicalCollection, String generationLabel) {
        requireEnabled();
        validateCollection(logicalCollection);
        IndexGenerationState next = IndexGenerationPolicy.activate(locked(logicalCollection),
                physicalName(logicalCollection, generationLabel), Instant.now());
        repository.save(next);
        return next;
    }

    @Transactional
    public IndexGenerationState fail(String logicalCollection, String generationLabel) {
        requireEnabled();
        validateCollection(logicalCollection);
        IndexGenerationState next = IndexGenerationPolicy.fail(locked(logicalCollection),
                physicalName(logicalCollection, generationLabel), Instant.now());
        repository.save(next);
        return next;
    }

    @Transactional
    public IndexGenerationState rollback(String logicalCollection) {
        requireEnabled();
        validateCollection(logicalCollection);
        IndexGenerationState next = IndexGenerationPolicy.rollback(locked(logicalCollection),
                Duration.ofHours(properties.getRollbackRetentionHours()), Instant.now());
        repository.save(next);
        return next;
    }

    private IndexGenerationState locked(String logicalCollection) {
        return repository.findForUpdate(logicalCollection)
                .orElseThrow(() -> new IllegalStateException("索引Generation状态不存在"));
    }

    private String physicalName(String logicalCollection, String generationLabel) {
        if (!GENERATION_LABEL.matcher(generationLabel).matches()) {
            throw new IllegalArgumentException("Generation标签格式不合法");
        }
        String value = logicalCollection + "--" + generationLabel;
        if (value.length() > 63) throw new IllegalArgumentException("物理Generation名称过长");
        return value;
    }

    private void validateCollection(String collection) {
        if (collection == null || !COLLECTION.matcher(collection).matches()) {
            throw new IllegalArgumentException("Collection名称格式不合法");
        }
    }

    private void requireEnabled() {
        if (!properties.isEnabled()) {
            throw new IllegalStateException("索引Generation功能未启用，请先执行数据库升级脚本");
        }
    }

    public record ArticleVersionSummary(long minVersion, long maxVersion, long chunkCount) {
    }
}
