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

/** Planner 只能输出工具调用或最终答案，不接收/返回内部思维过程。 */
public sealed interface AgentAction permits AgentAction.ToolCall, AgentAction.FinalAnswer {
    int estimatedTokens();

    record ToolCall(AgentToolName toolName, Object input, int estimatedTokens) implements AgentAction {}

    /** requiresEvidence=false 只允许没有调用过工具的问候等直接回答。 */
    record FinalAnswer(String answer, boolean requiresEvidence, int estimatedTokens) implements AgentAction {}
}
