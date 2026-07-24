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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/** 面向可信回答的检索请求；服务端会强制叠加文章可见性过滤。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustedRetrieveRequest {
    private String query;
    @Builder.Default
    private int candidateTopK = 20;
    @Builder.Default
    private int topK = 6;
    @Builder.Default
    private int maxContextTokens = 4000;
    private String collectionName;
    private Map<String, Object> metadataFilters;
}
