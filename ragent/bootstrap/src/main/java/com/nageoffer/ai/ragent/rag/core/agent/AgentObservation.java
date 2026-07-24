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

package com.nageoffer.ai.ragent.rag.core.agent;

import com.nageoffer.ai.ragent.rag.core.retrieve.TrustedRetrievalResult;

import java.util.List;

/** Planner 可见的工具观察；正文明确标为不可信，普通日志只记录 summary。 */
public record AgentObservation(AgentToolName toolName, String summary, String untrustedContext,
                               List<TrustedRetrievalResult.Citation> citations) {
    public AgentObservation {
        summary = summary == null ? "" : summary;
        untrustedContext = untrustedContext == null ? "" : untrustedContext;
        citations = citations == null ? List.of() : List.copyOf(citations);
    }
}
