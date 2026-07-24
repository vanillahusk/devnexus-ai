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

package com.nageoffer.ai.ragent.rag.core.agent.quota;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.validation.annotation.Validated;

/** 受控 Agent 的用户/会话日配额。 */
@Data
@Validated
@Configuration
@ConfigurationProperties(prefix = "rag.agent.quota")
public class AgentQuotaProperties {
    private boolean enabled = true;

    @Min(3)
    @Max(100_000)
    private int userDailySteps = 60;

    @Min(8_000)
    @Max(100_000_000)
    private int userDailyTokens = 160_000;

    @Min(3)
    @Max(10_000)
    private int sessionDailySteps = 18;

    @Min(8_000)
    @Max(10_000_000)
    private int sessionDailyTokens = 48_000;

    /** 日配额归属时区。 */
    private String zoneId = "Asia/Shanghai";

    /** 跨过自然日后额外保留的审计/结算窗口。 */
    @Min(60)
    @Max(21_600)
    private int expiryGraceSeconds = 3_600;
}
