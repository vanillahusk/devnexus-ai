package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import com.github.paicoding.forum.service.ai.service.AiExternalCallGuard;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiExternalCallGuardTest {

    @Test
    void shouldOpenCircuitAfterConsecutiveFailuresAndFastReject() {
        AiKnowledgeProperties properties = properties(2, 1);
        AiExternalCallGuard guard = new AiExternalCallGuard(properties, new SimpleMeterRegistry());

        assertThrows(AiExternalCallGuard.ExternalCallRejectedException.class,
                () -> guard.execute("ragent", () -> null));
        assertThrows(AiExternalCallGuard.ExternalCallRejectedException.class,
                () -> guard.execute("ragent", () -> null));

        AtomicBoolean invoked = new AtomicBoolean(false);
        assertThrows(AiExternalCallGuard.ExternalCallRejectedException.class,
                () -> guard.execute("ragent", () -> {
                    invoked.set(true);
                    return "should-not-run";
                }));

        assertFalse(invoked.get());
        assertTrue(guard.snapshot("ragent").open());
        assertEquals(2, guard.snapshot("ragent").consecutiveFailures());
    }

    @Test
    void shouldCloseCircuitAfterHalfOpenProbeSucceeds() throws Exception {
        AiKnowledgeProperties properties = properties(1, 1);
        properties.getGovernance().setOpenDurationMs(5L);
        AiExternalCallGuard guard = new AiExternalCallGuard(properties, new SimpleMeterRegistry());
        assertThrows(AiExternalCallGuard.ExternalCallRejectedException.class,
                () -> guard.execute("api", () -> null));

        Thread.sleep(10L);
        assertEquals("ok", guard.execute("api", () -> "ok"));

        assertFalse(guard.snapshot("api").open());
        assertEquals(0, guard.snapshot("api").consecutiveFailures());
    }

    @Test
    void shouldRejectImmediatelyWhenConcurrencyBulkheadIsFull() throws Exception {
        AiKnowledgeProperties properties = properties(5, 1);
        AiExternalCallGuard guard = new AiExternalCallGuard(properties, new SimpleMeterRegistry());
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            executor.submit(() -> guard.execute("ragent", () -> {
                entered.countDown();
                try {
                    release.await(2, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "ok";
            }));
            assertTrue(entered.await(1, TimeUnit.SECONDS));

            AiExternalCallGuard.ExternalCallRejectedException exception = assertThrows(
                    AiExternalCallGuard.ExternalCallRejectedException.class,
                    () -> guard.execute("api", () -> "never"));
            assertTrue(exception.getMessage().contains("并发已满"));
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private AiKnowledgeProperties properties(int failureThreshold, int maxConcurrent) {
        AiKnowledgeProperties properties = new AiKnowledgeProperties();
        properties.getGovernance().setFailureThreshold(failureThreshold);
        properties.getGovernance().setMaxConcurrentCalls(maxConcurrent);
        properties.getGovernance().setOpenDurationMs(60000L);
        properties.getGovernance().setSlowCallThresholdMs(5000L);
        return properties;
    }
}
