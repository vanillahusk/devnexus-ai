package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import com.github.paicoding.forum.web.QuickForumApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 使用真实 MySQL 验证 Outbox 多实例条件抢占和发送租约恢复。
 */
@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=false"
})
@EnabledIfSystemProperty(named = "outbox.concurrency.integration.enabled", matches = "true")
class MqOutboxConcurrencyIntegrationTest {
    private static final String CLAIM_EVENT_ID = "integration-outbox-concurrent-claim";
    private static final String STALE_EVENT_ID = "integration-outbox-stale-lease";
    private static final int WORKERS = 16;

    @Autowired
    private MqOutboxEventDao outboxEventDao;

    @MockBean
    private MessageQueueService messageQueueService;

    @Test
    void shouldAllowOnlyOneConcurrentClaim() throws Exception {
        deleteByEventId(CLAIM_EVENT_ID);
        MqOutboxEventDO event = saveEvent(CLAIM_EVENT_ID, MqOutboxStatusEnum.PENDING);
        ExecutorService executor = Executors.newFixedThreadPool(WORKERS);
        CountDownLatch ready = new CountDownLatch(WORKERS);
        CountDownLatch start = new CountDownLatch(1);

        try {
            List<Future<Boolean>> results = new ArrayList<>();
            Date staleBefore = new Date(System.currentTimeMillis() - 300_000L);
            for (int i = 0; i < WORKERS; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return outboxEventDao.claim(event, staleBefore);
                }));
            }
            ready.await();
            start.countDown();

            long claimed = 0;
            for (Future<Boolean> result : results) {
                if (result.get()) {
                    claimed++;
                }
            }
            assertEquals(1L, claimed, "同一Outbox事件只能被一个调度实例抢占");
            assertEquals(MqOutboxStatusEnum.SENDING.getCode(),
                    outboxEventDao.getById(event.getId()).getStatus());
        } finally {
            executor.shutdownNow();
            deleteByEventId(CLAIM_EVENT_ID);
        }
    }

    @Test
    void shouldRecoverOnlyExpiredSendingLease() {
        deleteByEventId(STALE_EVENT_ID);
        MqOutboxEventDO event = saveEvent(STALE_EVENT_ID, MqOutboxStatusEnum.SENDING);
        Date staleUpdateTime = new Date(System.currentTimeMillis() - 600_000L);
        outboxEventDao.lambdaUpdate().eq(MqOutboxEventDO::getId, event.getId())
                .set(MqOutboxEventDO::getUpdateTime, staleUpdateTime).update();

        try {
            Date staleBefore = new Date(System.currentTimeMillis() - 300_000L);
            List<MqOutboxEventDO> dispatchable = outboxEventDao.listDispatchable(100, staleBefore);
            MqOutboxEventDO expiredLease = dispatchable.stream()
                    .filter(item -> item.getId().equals(event.getId())).findFirst().orElseThrow();

            assertTrue(outboxEventDao.claim(expiredLease, staleBefore),
                    "超过租约时间的SENDING事件应允许重新抢占");
            assertTrue(outboxEventDao.getById(event.getId()).getUpdateTime().after(staleUpdateTime));

            Date recentLeaseBoundary = new Date(System.currentTimeMillis() - 300_000L);
            assertTrue(outboxEventDao.listDispatchable(100, recentLeaseBoundary).stream()
                    .noneMatch(item -> item.getId().equals(event.getId())),
                    "刚续租的SENDING事件不能被其他实例立即抢占");
        } finally {
            deleteByEventId(STALE_EVENT_ID);
        }
    }

    private MqOutboxEventDO saveEvent(String eventId, MqOutboxStatusEnum status) {
        MqOutboxEventDO event = new MqOutboxEventDO();
        event.setEventId(eventId);
        event.setTopic("paicoding-business-event");
        event.setTag("integration-test");
        event.setAggregateId("integration-test");
        event.setPayload("{}");
        event.setStatus(status.getCode());
        event.setRetryCount(0);
        event.setLastError("");
        outboxEventDao.save(event);
        return event;
    }

    private void deleteByEventId(String eventId) {
        outboxEventDao.lambdaUpdate().eq(MqOutboxEventDO::getEventId, eventId).remove();
    }
}
