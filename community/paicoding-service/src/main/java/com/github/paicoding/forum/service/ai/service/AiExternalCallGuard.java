package com.github.paicoding.forum.service.ai.service;

import com.github.paicoding.forum.service.ai.config.AiKnowledgeProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * 外部 AI 调用的轻量隔离与熔断器。每个 route 独立统计，避免 ragent 故障影响直连 API。
 */
@Component
public class AiExternalCallGuard {

    private final AiKnowledgeProperties properties;
    private final Semaphore concurrencyLimiter;
    private final Map<String, CircuitState> circuits = new ConcurrentHashMap<>();
    private final MeterRegistry meterRegistry;

    public AiExternalCallGuard(AiKnowledgeProperties properties, MeterRegistry meterRegistry) {
        this.properties = properties;
        this.meterRegistry = meterRegistry;
        this.concurrencyLimiter = new Semaphore(
                Math.max(1, properties.getGovernance().getMaxConcurrentCalls()), true);
    }

    public String execute(String route, Supplier<String> supplier) {
        CircuitState circuit = circuits.computeIfAbsent(route, this::newCircuit);
        long now = System.currentTimeMillis();
        boolean probe = acquireCircuitPermission(route, circuit, now);
        if (!concurrencyLimiter.tryAcquire()) {
            if (probe) {
                circuit.halfOpenProbe.set(false);
            }
            throw new ExternalCallRejectedException(route + " 并发已满，已快速降级");
        }

        long start = System.currentTimeMillis();
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome = "success";
        try {
            String result = supplier.get();
            long elapsed = System.currentTimeMillis() - start;
            if (StringUtils.isBlank(result)) {
                recordFailure(circuit, now);
                throw new ExternalCallRejectedException(route + " 返回为空，已降级");
            }
            if (elapsed >= properties.getGovernance().getSlowCallThresholdMs()) {
                recordFailure(circuit, System.currentTimeMillis());
            } else {
                closeCircuit(circuit);
            }
            return result;
        } catch (ExternalCallRejectedException e) {
            outcome = "rejected";
            throw e;
        } catch (RuntimeException e) {
            outcome = "failure";
            recordFailure(circuit, System.currentTimeMillis());
            throw new ExternalCallRejectedException(route + " 调用失败，已降级", e);
        } finally {
            sample.stop(Timer.builder("ai.external.call.duration")
                    .tag("route", route).tag("outcome", outcome)
                    .publishPercentileHistogram().register(meterRegistry));
            meterRegistry.counter("ai.external.calls", "route", route, "outcome", outcome).increment();
            concurrencyLimiter.release();
            if (probe) {
                circuit.halfOpenProbe.set(false);
            }
        }
    }

    public CircuitSnapshot snapshot(String route) {
        CircuitState state = circuits.computeIfAbsent(route, this::newCircuit);
        return new CircuitSnapshot(route, state.consecutiveFailures.get(), state.openUntil,
                state.openUntil > System.currentTimeMillis());
    }

    private boolean acquireCircuitPermission(String route, CircuitState circuit, long now) {
        if (circuit.openUntil <= now) {
            if (circuit.openUntil > 0L) {
                if (!circuit.halfOpenProbe.compareAndSet(false, true)) {
                    throw new ExternalCallRejectedException(route + " 熔断半开探测中，已快速降级");
                }
                return true;
            }
            return false;
        }
        throw new ExternalCallRejectedException(route + " 熔断器已打开，已快速降级");
    }

    private void recordFailure(CircuitState circuit, long now) {
        int failures = circuit.consecutiveFailures.incrementAndGet();
        if (failures >= Math.max(1, properties.getGovernance().getFailureThreshold())) {
            circuit.openUntil = now + Math.max(1L, properties.getGovernance().getOpenDurationMs());
        }
    }

    private void closeCircuit(CircuitState circuit) {
        circuit.consecutiveFailures.set(0);
        circuit.openUntil = 0L;
    }

    private CircuitState newCircuit(String route) {
        CircuitState state = new CircuitState();
        Gauge.builder("ai.external.circuit.open", state,
                        item -> item.openUntil > System.currentTimeMillis() ? 1D : 0D)
                .tag("route", route).register(meterRegistry);
        Gauge.builder("ai.external.circuit.failures", state,
                        item -> item.consecutiveFailures.get())
                .tag("route", route).register(meterRegistry);
        return state;
    }

    private static class CircuitState {
        private final AtomicInteger consecutiveFailures = new AtomicInteger();
        private final AtomicBoolean halfOpenProbe = new AtomicBoolean();
        private volatile long openUntil;
    }

    public record CircuitSnapshot(String route, int consecutiveFailures, long openUntil, boolean open) {
    }

    public static class ExternalCallRejectedException extends RuntimeException {
        public ExternalCallRejectedException(String message) {
            super(message);
        }

        public ExternalCallRejectedException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
