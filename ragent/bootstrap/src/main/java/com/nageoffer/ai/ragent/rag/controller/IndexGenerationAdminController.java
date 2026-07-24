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

package com.nageoffer.ai.ragent.rag.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.nageoffer.ai.ragent.framework.convention.Result;
import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.framework.web.Results;
import com.nageoffer.ai.ragent.rag.core.generation.IndexGenerationService;
import com.nageoffer.ai.ragent.rag.core.generation.IndexGenerationState;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Administrative control plane for watermarked, two-Generation index rebuilds. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/rag/index-generations")
@ConditionalOnProperty(name = "rag.index-generation.enabled", havingValue = "true")
public class IndexGenerationAdminController {
    private final IndexGenerationService generationService;

    @GetMapping
    public Result<IndexGenerationState> state(@RequestParam String collectionName) {
        checkAdmin();
        return Results.success(generationService.state(collectionName)
                .orElseThrow(() -> new ClientException("Collection尚未建立Generation状态")));
    }

    @GetMapping("/article-versions")
    public Result<Map<Long, IndexGenerationService.ArticleVersionSummary>> articleVersions(
            @RequestParam String collectionName) {
        checkAdmin();
        return Results.success(generationService.articleVersions(collectionName));
    }

    @PostMapping("/begin")
    public Result<IndexGenerationState> begin(@RequestBody BeginRequest request) {
        checkAdmin();
        require(request != null && request.collectionName() != null && request.generationLabel() != null,
                "Collection和Generation不能为空");
        return Results.success(generationService.begin(request.collectionName(), request.generationLabel(),
                request.startWatermark()));
    }

    @PostMapping("/progress")
    public Result<IndexGenerationState> progress(@RequestBody ProgressRequest request) {
        checkAdmin();
        require(request != null && request.collectionName() != null && request.generationLabel() != null,
                "Collection和Generation不能为空");
        return Results.success(generationService.recordProgress(request.collectionName(), request.generationLabel(),
                request.appliedWatermark(), request.targetWatermark(), request.reconciled()));
    }

    @PostMapping("/activate")
    public Result<IndexGenerationState> activate(@RequestBody GenerationRequest request) {
        checkAdmin();
        requireGeneration(request);
        return Results.success(generationService.activate(request.collectionName(), request.generationLabel()));
    }

    @PostMapping("/fail")
    public Result<IndexGenerationState> fail(@RequestBody GenerationRequest request) {
        checkAdmin();
        requireGeneration(request);
        return Results.success(generationService.fail(request.collectionName(), request.generationLabel()));
    }

    @PostMapping("/rollback")
    public Result<IndexGenerationState> rollback(@RequestBody CollectionRequest request) {
        checkAdmin();
        require(request != null && request.collectionName() != null, "Collection不能为空");
        return Results.success(generationService.rollback(request.collectionName()));
    }

    private void checkAdmin() {
        StpUtil.checkRole("admin");
    }

    private void requireGeneration(GenerationRequest request) {
        require(request != null && request.collectionName() != null && request.generationLabel() != null,
                "Collection和Generation不能为空");
    }

    private void require(boolean valid, String message) {
        if (!valid) throw new ClientException(message);
    }

    public record BeginRequest(String collectionName, String generationLabel, long startWatermark) {
    }

    public record ProgressRequest(String collectionName, String generationLabel, long appliedWatermark,
                                  long targetWatermark, boolean reconciled) {
    }

    public record GenerationRequest(String collectionName, String generationLabel) {
    }

    public record CollectionRequest(String collectionName) {
    }
}
