package com.github.paicoding.forum.web.config;

import com.github.paicoding.forum.service.user.service.favor.RejectAwareRunnable;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
public class FavorRejectAwareHandler implements RejectedExecutionHandler {

    private final String poolName;
    private final Counter rejectCounter;

    public FavorRejectAwareHandler(String poolName, MeterRegistry meterRegistry) {
        this.poolName = poolName;
        this.rejectCounter = Counter.builder("favor.executor.reject.total")
                .tag("pool", poolName)
                .register(meterRegistry);
    }

    @Override
    public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
        rejectCounter.increment();
        log.warn("线程池任务被拒绝, pool={}, active={}, queueSize={}",
                poolName,
                executor.getActiveCount(),
                executor.getQueue().size());
        if (r instanceof RejectAwareRunnable rejectAwareRunnable) {
            rejectAwareRunnable.onRejected(poolName);
            return;
        }
        if (!executor.isShutdown()) {
            r.run();
        }
    }
}
