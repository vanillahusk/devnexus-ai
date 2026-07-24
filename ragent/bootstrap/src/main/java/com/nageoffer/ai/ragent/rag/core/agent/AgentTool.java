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

/** 强类型、只读、有界结果的 Agent 工具合同。 */
public interface AgentTool<I> {
    AgentToolName name();
    Class<I> inputType();
    default boolean readOnly() { return true; }
    String normalizedSignature(I input);
    AgentToolResult execute(I input);
    default AgentToolResult execute(I input, AgentToolContext context) { return execute(input); }
}
