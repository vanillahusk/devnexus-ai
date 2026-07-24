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

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 仅注册 AgentToolName 枚举内的只读实现，拒绝重复或写工具。 */
@Component
public class AgentToolRegistry {
    private final Map<AgentToolName, AgentTool<?>> tools = new EnumMap<>(AgentToolName.class);

    public AgentToolRegistry(List<AgentTool<?>> registeredTools) {
        for (AgentTool<?> tool : registeredTools) {
            if (!tool.readOnly()) throw new IllegalStateException("Agent第一版禁止写工具: " + tool.name().value());
            if (tools.putIfAbsent(tool.name(), tool) != null) {
                throw new IllegalStateException("Agent工具重复注册: " + tool.name().value());
            }
        }
    }

    public Invocation prepare(AgentToolName name, Object input) {
        AgentTool<?> tool = tools.get(name);
        if (tool == null) throw new IllegalArgumentException("Agent工具尚未启用: " + name.value());
        if (input == null || !tool.inputType().isInstance(input)) {
            throw new IllegalArgumentException("Agent工具参数类型错误: " + name.value());
        }
        return prepareTyped(tool, input);
    }

    private <I> Invocation prepareTyped(AgentTool<I> raw, Object input) {
        I typed = raw.inputType().cast(input);
        return new Invocation(raw.name(), raw.normalizedSignature(typed), context -> raw.execute(typed, context));
    }

    public record Invocation(AgentToolName name, String signature, ToolCall call) {}

    @FunctionalInterface
    public interface ToolCall { AgentToolResult execute(AgentToolContext context); }
}
