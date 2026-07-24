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

import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.http.ModelClientErrorType;
import com.nageoffer.ai.ragent.infra.http.ModelClientException;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import com.nageoffer.ai.ragent.infra.model.ModelCallMetrics;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/** Embedding 本地并发/QPS/短重试边界；远端熔断仍由 ModelHealthStore 负责。 */
@Component
public class EmbeddingCallGovernor {
    private final AIModelProperties.EmbeddingGovernance properties;
    private final Semaphore concurrency;
    private final ModelCallMetrics metrics;
    private double availableTokens;
    private long lastRefillNanos;

    public EmbeddingCallGovernor(AIModelProperties modelProperties, ModelCallMetrics metrics) {
        this.properties = modelProperties.getEmbeddingGovernance();
        this.metrics = metrics;
        int maxConcurrent = Math.max(1, properties.getMaxConcurrent());
        this.concurrency = new Semaphore(maxConcurrent, true);
        this.availableTokens = Math.max(1, properties.getRequestsPerSecond());
        this.lastRefillNanos = System.nanoTime();
    }

    public <T> T execute(EmbeddingCall<T> call) {
        if (!tryConsumeRateToken()) {
            metrics.rejected(ModelCapability.EMBEDDING, "rate_limit");
            throw new ModelClientException("Embedding 本地速率限制", ModelClientErrorType.RATE_LIMITED, 429);
        }
        boolean acquired;
        try {
            acquired = concurrency.tryAcquire(Math.max(1L, properties.getAcquireTimeoutMs()), TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            metrics.rejected(ModelCapability.EMBEDDING, "interrupted");
            throw new ModelClientException("Embedding 并发等待被中断", ModelClientErrorType.NETWORK_ERROR, null,
                    interrupted);
        }
        if (!acquired) {
            metrics.rejected(ModelCapability.EMBEDDING, "concurrency");
            throw new ModelClientException("Embedding 本地并发已满", ModelClientErrorType.RATE_LIMITED, 429);
        }
        ModelCallMetrics.Call metricCall = metrics.start(ModelCapability.EMBEDDING);
        String status = "failure";
        try {
            int attempts = Math.max(1, properties.getMaxAttempts());
            RuntimeException last = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    T result = call.execute();
                    status = "success";
                    return result;
                } catch (RuntimeException failure) {
                    last = failure;
                    if (attempt >= attempts || !isRetryable(failure)) throw failure;
                    metrics.retried(ModelCapability.EMBEDDING);
                    backoff(attempt);
                }
            }
            throw last == null ? new IllegalStateException("Embedding call failed without cause") : last;
        } finally {
            metricCall.close(status);
            concurrency.release();
        }
    }

    private synchronized boolean tryConsumeRateToken() {
        int rate = Math.max(1, properties.getRequestsPerSecond());
        long now = System.nanoTime();
        double refill = (now - lastRefillNanos) / 1_000_000_000D * rate;
        availableTokens = Math.min(rate, availableTokens + refill);
        lastRefillNanos = now;
        if (availableTokens < 1D) return false;
        availableTokens -= 1D;
        return true;
    }

    private boolean isRetryable(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ModelClientException modelFailure) {
                return modelFailure.getErrorType() == ModelClientErrorType.NETWORK_ERROR
                        || modelFailure.getErrorType() == ModelClientErrorType.RATE_LIMITED
                        || modelFailure.getErrorType() == ModelClientErrorType.SERVER_ERROR;
            }
            if (current instanceof IOException) return true;
        }
        return false;
    }

    private void backoff(int attempt) {
        long base = Math.max(1L, properties.getRetryBackoffMs());
        long delay = Math.min(2_000L, base * (1L << Math.min(10, attempt - 1)));
        try {
            Thread.sleep(delay);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ModelClientException("Embedding 重试等待被中断", ModelClientErrorType.NETWORK_ERROR, null,
                    interrupted);
        }
    }

    @FunctionalInterface
    public interface EmbeddingCall<T> {
        T execute();
    }
}
