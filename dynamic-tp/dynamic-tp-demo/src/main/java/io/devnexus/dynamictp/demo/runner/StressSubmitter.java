package io.devnexus.dynamictp.demo.runner;

import java.time.LocalDateTime;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class StressSubmitter {

    private static final Logger log = LoggerFactory.getLogger(StressSubmitter.class);

    private final ThreadPoolExecutor orderThreadPool;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong successCounter = new AtomicLong();
    private final AtomicLong rejectCounter = new AtomicLong();

    private volatile Thread producerThread;

    public StressSubmitter(@Qualifier("orderThreadPool") ThreadPoolExecutor orderThreadPool) {
        this.orderThreadPool = orderThreadPool;
    }

    public synchronized String start() {
        if (running.get()) {
            return "stress producer is already running";
        }
        running.set(true);
        producerThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (running.get()) {
                    try {
                        orderThreadPool.execute(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    Thread.sleep(1500L);
                                    successCounter.incrementAndGet();
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                        });
                    } catch (RejectedExecutionException exception) {
                        long rejected = rejectCounter.incrementAndGet();
                        if (rejected % 20 == 0) {
                            log.warn("rejected tasks={}, poolSize={}, active={}, queueSize={}", rejected,
                                    orderThreadPool.getPoolSize(), orderThreadPool.getActiveCount(),
                                    orderThreadPool.getQueue().size());
                        }
                    }

                    try {
                        Thread.sleep(50L);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            }
        }, "stress-producer");
        producerThread.setDaemon(true);
        producerThread.start();
        log.info("stress producer started at {}", LocalDateTime.now());
        return "stress producer started";
    }

    public synchronized String stop() {
        if (!running.get()) {
            return "stress producer is already stopped";
        }
        running.set(false);
        if (producerThread != null) {
            producerThread.interrupt();
        }
        log.info("stress producer stopped at {}", LocalDateTime.now());
        return "stress producer stopped";
    }

    public String stats() {
        return String.format("running=%s, success=%d, rejected=%d, poolSize=%d, active=%d, queueSize=%d",
                running.get(), successCounter.get(), rejectCounter.get(), orderThreadPool.getPoolSize(),
                orderThreadPool.getActiveCount(), orderThreadPool.getQueue().size());
    }

    @PreDestroy
    public void shutdown() {
        stop();
    }
}