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

package com.nageoffer.ai.ragent.infra.model;

import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/** 模型调用低基数指标；不接受 modelId、用户、查询或正文作为标签。 */
@Component
public class ModelCallMetrics {

    private final MeterRegistry registry;
    private final Map<ModelCapability, AtomicInteger> inFlight = new EnumMap<>(ModelCapability.class);

    public ModelCallMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (ModelCapability capability : ModelCapability.values()) {
            AtomicInteger value = new AtomicInteger();
            inFlight.put(capability, value);
            Gauge.builder("rag.model.calls.inflight", value, AtomicInteger::get)
                    .tag("capability", label(capability))
                    .description("Current in-flight model calls")
                    .register(registry);
        }
    }

    public Call start(ModelCapability capability) {
        inFlight.get(capability).incrementAndGet();
        return new Call(capability, Timer.start(registry));
    }

    public void rejected(ModelCapability capability, String reason) {
        Counter.builder("rag.model.calls.rejected")
                .tag("capability", label(capability))
                .tag("reason", safeReason(reason))
                .register(registry)
                .increment();
    }

    public void retried(ModelCapability capability) {
        Counter.builder("rag.model.calls.retried")
                .tag("capability", label(capability))
                .register(registry)
                .increment();
    }

    private String label(ModelCapability capability) {
        return capability.name().toLowerCase(java.util.Locale.ROOT);
    }

    private String safeReason(String reason) {
        return switch (reason) {
            case "rate_limit", "concurrency", "interrupted" -> reason;
            default -> "other";
        };
    }

    public final class Call implements AutoCloseable {
        private final ModelCapability capability;
        private final Timer.Sample sample;
        private final AtomicBoolean closed = new AtomicBoolean();

        private Call(ModelCapability capability, Timer.Sample sample) {
            this.capability = capability;
            this.sample = sample;
        }

        public void close(String status) {
            if (!closed.compareAndSet(false, true)) return;
            inFlight.get(capability).decrementAndGet();
            sample.stop(Timer.builder("rag.model.calls.duration")
                    .tag("capability", label(capability))
                    .tag("status", safeStatus(status))
                    .description("Model call duration")
                    .publishPercentileHistogram()
                    .register(registry));
        }

        @Override
        public void close() {
            close("success");
        }

        private String safeStatus(String status) {
            return switch (status) {
                case "success", "failure", "cancelled" -> status;
                default -> "failure";
            };
        }
    }
}
