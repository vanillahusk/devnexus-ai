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

import com.nageoffer.ai.ragent.framework.errorcode.BaseErrorCode;
import com.nageoffer.ai.ragent.framework.exception.RemoteException;
import com.nageoffer.ai.ragent.infra.config.AIModelProperties;
import com.nageoffer.ai.ragent.infra.enums.ModelCapability;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/** Chat/Rerank 进程级并发舱壁；Embedding 由专用 governor 管理。 */
@Component
public class ModelCallConcurrencyGovernor {

    private final Map<ModelCapability, Semaphore> permits = new EnumMap<>(ModelCapability.class);
    private final long acquireTimeoutMs;
    private final ModelCallMetrics metrics;

    public ModelCallConcurrencyGovernor(AIModelProperties properties, ModelCallMetrics metrics) {
        this.metrics = metrics;
        AIModelProperties.ModelCallGovernance config = properties.getModelCallGovernance();
        permits.put(ModelCapability.CHAT, new Semaphore(Math.max(1, config.getChatMaxConcurrent()), true));
        permits.put(ModelCapability.RERANK, new Semaphore(Math.max(1, config.getRerankMaxConcurrent()), true));
        acquireTimeoutMs = Math.max(1L, config.getAcquireTimeoutMs());
    }

    public <T> T execute(ModelCapability capability, GuardedCall<T> call) {
        Lease lease = acquire(capability);
        try {
            T result = call.execute();
            lease.close("success");
            return result;
        } catch (RuntimeException failure) {
            lease.close("failure");
            throw failure;
        }
    }

    public Lease acquire(ModelCapability capability) {
        Semaphore semaphore = permits.get(capability);
        if (semaphore == null) {
            throw new IllegalArgumentException("Unsupported governed model capability: " + capability);
        }
        try {
            if (!semaphore.tryAcquire(acquireTimeoutMs, TimeUnit.MILLISECONDS)) {
                metrics.rejected(capability, "concurrency");
                throw rejected(capability);
            }
            return new Lease(semaphore, metrics.start(capability));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            metrics.rejected(capability, "interrupted");
            throw new RemoteException(capability.getDisplayName() + " 并发等待被中断", interrupted,
                    BaseErrorCode.REMOTE_ERROR);
        }
    }

    private RemoteException rejected(ModelCapability capability) {
        return new RemoteException(capability.getDisplayName() + " 本地并发已满，请稍后重试",
                BaseErrorCode.REMOTE_ERROR);
    }

    @FunctionalInterface
    public interface GuardedCall<T> {
        T execute();
    }

    public static final class Lease implements AutoCloseable {
        private final Semaphore semaphore;
        private final ModelCallMetrics.Call metricCall;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(Semaphore semaphore, ModelCallMetrics.Call metricCall) {
            this.semaphore = semaphore;
            this.metricCall = metricCall;
        }

        public void close(String status) {
            if (released.compareAndSet(false, true)) {
                metricCall.close(status);
                semaphore.release();
            }
        }

        @Override
        public void close() {
            close("success");
        }
    }
}
