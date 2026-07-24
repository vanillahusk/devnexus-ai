package io.devnexus.dynamictp.demo;

import io.devnexus.dynamictp.starter.core.DynamicThreadPoolCommandPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(
        classes = DynamicTpDemoApplication.class,
        properties = {
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisRepositoriesAutoConfiguration",
                "management.health.redis.enabled=false",
                "dynamic.tp.monitor-log-enabled=false"
        })
@AutoConfigureMockMvc
class DynamicTpActuatorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    @Qualifier("orderThreadPool")
    private ThreadPoolExecutor orderThreadPool;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockBean
    private DynamicThreadPoolCommandPublisher dynamicThreadPoolCommandPublisher;

    private CountDownLatch latch;

    @AfterEach
    void tearDown() {
        if (latch != null) {
            latch.countDown();
        }
        orderThreadPool.getQueue().clear();
    }

    @Test
    void shouldExposeDynamicThreadPoolEndpointAndMetrics() throws Exception {
        mockMvc.perform(get("/actuator/dynamicThreadPools"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderThreadPool").exists())
                .andExpect(jsonPath("$.orderThreadPool.currentConfig.coreSize").value(2))
                .andExpect(jsonPath("$.orderThreadPool.snapshot.queueResizable").value(true));

        Assertions.assertNotNull(meterRegistry.find("dynamic.tp.queue.usage")
            .tag("pool", "orderThreadPool")
            .gauge());
    }

    @Test
    void shouldTurnHealthDownWhenQueueIsCritical() throws Exception {
        latch = new CountDownLatch(1);
        for (int index = 0; index < 12; index++) {
            orderThreadPool.execute(new Runnable() {
                @Override
                public void run() {
                    try {
                        latch.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                    }
                }
            });
        }

        Assertions.assertEquals(10, orderThreadPool.getQueue().size());

        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.components.dynamicThreadPool.status").value("DOWN"));
    }
}