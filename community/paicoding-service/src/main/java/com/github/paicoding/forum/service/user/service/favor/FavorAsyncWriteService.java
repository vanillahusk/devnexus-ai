package com.github.paicoding.forum.service.user.service.favor;

import com.github.paicoding.forum.api.model.enums.DocumentTypeEnum;
import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.enums.OperateTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.core.cache.RedisClient;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.core.util.SpringUtil;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.statistics.constants.CountConstants;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.service.UserFootService;
import com.google.common.util.concurrent.Striped;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Slf4j
@Service
public class FavorAsyncWriteService {

    private static final String RATE_LIMIT_KEY = "favor:rate:%d:%d";
    private static final String FAVOR_EVENT_QUEUE_KEY = "favor:event:queue";
    private static final String FAVOR_EVENT_PROCESSING_QUEUE_KEY = "favor:event:processing:queue";
    private static final String FAVOR_PERSIST_RETRY_QUEUE_KEY = "favor:persist:retry:queue";
    private static final String FAVOR_PERSIST_RETRY_PROCESSING_QUEUE_KEY = "favor:persist:retry:processing:queue";
    private static final String FAVOR_NOTIFY_RETRY_QUEUE_KEY = "favor:notify:retry:queue";
    private static final String FAVOR_NOTIFY_RETRY_PROCESSING_QUEUE_KEY = "favor:notify:retry:processing:queue";
    private static final String FAVOR_PERSIST_DEAD_QUEUE_KEY = "favor:persist:dead:queue";
    private static final String FAVOR_NOTIFY_DEAD_QUEUE_KEY = "favor:notify:dead:queue";

    private static final long DEFAULT_RATE_LIMIT_WINDOW_SECONDS = 60L;
    private static final long DEFAULT_RATE_LIMIT_MAX_REQUESTS = 5L;
    private static final int FLUSH_BATCH_SIZE = 200;
    private static final int RETRY_BATCH_SIZE = 100;
    private static final int DEFAULT_LIKE_USER_SHARD_COUNT = 16;
    private static final int MAX_PERSIST_RETRY_TIMES = 5;
    private static final int MAX_NOTIFY_RETRY_TIMES = 5;
    private static final int SHUTDOWN_DRAIN_MAX_ROUNDS = 10000;
    private static final String ARTICLE_LIKE_USER_SHARD_KEY = "favor:liked:article:%d:%d";
    private static final String FAVOR_COMPLETED_EVENT_KEY = "favor:event:completed:%s";
    private static final String FAVOR_OPERATION_VERSION_KEY = "favor:version:%d:%d";
    private static final Duration COMPLETED_EVENT_TTL = Duration.ofDays(7);

    private static final Striped<Lock> FOOT_ROW_LOCKS = Striped.lock(2048);

    private static final String RATE_LIMIT_LUA =
            "local c = redis.call('INCR', KEYS[1]);" +
                    "if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end;" +
                    "if c > tonumber(ARGV[2]) then return 0 else return 1 end;";

    private final DefaultRedisScript<Long> rateLimitScript = new DefaultRedisScript<>(RATE_LIMIT_LUA, Long.class);
    private static final String OPERATION_VERSION_LUA =
            "local current = tonumber(redis.call('GET', KEYS[1]) or '0');" +
                    "local now = tonumber(ARGV[1]);" +
                    "local next = now; if current >= now then next = current + 1; end;" +
                    "redis.call('SET', KEYS[1], next, 'EX', ARGV[2]); return next;";
    private final DefaultRedisScript<Long> operationVersionScript =
            new DefaultRedisScript<>(OPERATION_VERSION_LUA, Long.class);

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private UserFootService userFootService;

    @Autowired
    private MessageQueueService messageQueueService;

    @Autowired
    @Qualifier("favorPersistExecutor")
    private ExecutorService favorPersistExecutor;

    @Autowired
    @Qualifier("favorNotifyExecutor")
    private ExecutorService favorNotifyExecutor;

    /**
     * 生产默认值保持原来的“同一用户、同一文章 60 秒最多 5 次”。
     * 压测环境可以显式调高阈值，以便测量真实业务链路，而不是限流器拒绝能力。
     */
    @Value("${favor.rate-limit.enabled:true}")
    private boolean rateLimitEnabled = true;

    @Value("${favor.rate-limit.window-seconds:60}")
    private long rateLimitWindowSeconds = DEFAULT_RATE_LIMIT_WINDOW_SECONDS;

    @Value("${favor.rate-limit.max-requests:5}")
    private long rateLimitMaxRequests = DEFAULT_RATE_LIMIT_MAX_REQUESTS;

    private final Map<Long, LongAdder> articlePraiseDelta = new ConcurrentHashMap<>();
    private final Map<Long, LongAdder> authorPraiseDelta = new ConcurrentHashMap<>();
    private final AtomicBoolean persistFlushRunning = new AtomicBoolean(false);

    private volatile boolean shuttingDown = false;

    public boolean allowFavorRequest(Long articleId, Long userId) {
        if (!rateLimitEnabled) {
            return true;
        }
        String key = String.format(RATE_LIMIT_KEY, articleId, userId);
        Long allowed = stringRedisTemplate.execute(rateLimitScript,
                Collections.singletonList(key),
                String.valueOf(Math.max(1L, rateLimitWindowSeconds)),
                String.valueOf(Math.max(1L, rateLimitMaxRequests)));
        return Long.valueOf(1L).equals(allowed);
    }

    public void enqueue(FavorEvent event) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.randomUUID().toString());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(System.currentTimeMillis());
        }
        if (event.getOperationVersion() == null) {
            event.setOperationVersion(nextOperationVersion(event));
        }
        stringRedisTemplate.opsForList().rightPush(FAVOR_EVENT_QUEUE_KEY, JsonUtil.toStr(event));
    }

    @Scheduled(fixedDelayString = "${favor.flush-fixed-delay-ms:3000}")
    public void flushFavorEvents() {
        if (shuttingDown) {
            return;
        }
        if (!persistFlushRunning.compareAndSet(false, true)) {
            return;
        }
        try {
            favorPersistExecutor.execute(wrapContextTask(() -> {
                try {
                    List<String> raws = loadOrMoveProcessingBatch(
                            FAVOR_EVENT_QUEUE_KEY, FAVOR_EVENT_PROCESSING_QUEUE_KEY, FLUSH_BATCH_SIZE);
                    for (String raw : raws) {
                        processFavorEvent(raw, 0, FAVOR_EVENT_PROCESSING_QUEUE_KEY, raw);
                    }
                } finally {
                    persistFlushRunning.set(false);
                }
            }));
        } catch (RuntimeException e) {
            persistFlushRunning.set(false);
            throw e;
        }
    }

    @Scheduled(fixedDelayString = "${favor.persist-retry-fixed-delay-ms:5000}")
    public void flushPersistRetryEvents() {
        if (shuttingDown) {
            return;
        }
        List<String> raws = loadOrMoveProcessingBatch(FAVOR_PERSIST_RETRY_QUEUE_KEY,
                FAVOR_PERSIST_RETRY_PROCESSING_QUEUE_KEY, RETRY_BATCH_SIZE);
        for (String raw : raws) {
            PersistRetryPayload payload = JsonUtil.toObj(raw, PersistRetryPayload.class);
            processFavorEvent(payload.getRaw(), safeRetryCount(payload.getRetryCount()),
                    FAVOR_PERSIST_RETRY_PROCESSING_QUEUE_KEY, raw);
        }
    }

    @Scheduled(fixedDelayString = "${favor.notify-retry-fixed-delay-ms:5000}")
    public void flushNotifyRetryEvents() {
        if (shuttingDown) {
            return;
        }
        List<String> raws = loadOrMoveProcessingBatch(FAVOR_NOTIFY_RETRY_QUEUE_KEY,
                FAVOR_NOTIFY_RETRY_PROCESSING_QUEUE_KEY, RETRY_BATCH_SIZE);
        for (String raw : raws) {
            NotifyRetryPayload payload = JsonUtil.toObj(raw, NotifyRetryPayload.class);
            submitNotifyTask(payload, FAVOR_NOTIFY_RETRY_PROCESSING_QUEUE_KEY, raw);
        }
    }

    @Scheduled(fixedDelayString = "${favor.counter-flush-fixed-delay-ms:1000}")
    public void flushPraiseCounters() {
        flushDeltaByPipeline(articlePraiseDelta,
                articleId -> CountConstants.ARTICLE_STATISTIC_INFO + articleId,
                CountConstants.PRAISE_COUNT);
        flushDeltaByPipeline(authorPraiseDelta,
                userId -> CountConstants.USER_STATISTIC_INFO + userId,
                CountConstants.PRAISE_COUNT);
    }

    private void processFavorEvent(String raw, int retryCount, String processingQueueKey, String storedRaw) {
        try {
            FavorEvent event = JsonUtil.toObj(raw, FavorEvent.class);
            normalizeEvent(event, raw);
            if (isCompleted(event.getEventId())) {
                acknowledge(processingQueueKey, storedRaw);
                return;
            }
            OperateTypeEnum operateType = OperateTypeEnum.fromCode(event.getOperateType());
            if (operateType == OperateTypeEnum.EMPTY) {
                acknowledge(processingQueueKey, storedRaw);
                return;
            }

            UserFootDO foot;
            boolean stateChanged;
            Lock rowLock = FOOT_ROW_LOCKS.get(event.getArticleId() + ":" + event.getUserId());
            rowLock.lock();
            try {
                UserFootService.UserFootUpdateResult updateResult = userFootService.saveOrUpdateUserFootWithOutbox(
                        DocumentTypeEnum.ARTICLE,
                        event.getArticleId(),
                        event.getAuthorId(),
                        event.getUserId(),
                        operateType,
                        event.getEventId(),
                        event.getOperationVersion());
                foot = updateResult.foot();
                stateChanged = updateResult.changed();
            } finally {
                rowLock.unlock();
            }

            if (!stateChanged) {
                markCompleted(event.getEventId());
                acknowledge(processingQueueKey, storedRaw);
                return;
            }

            aggregatePraiseCounter(foot, operateType);

            NotifyTypeEnum notifyType = OperateTypeEnum.getNotifyType(operateType);
            if (notifyType == null) {
                markCompleted(event.getEventId());
                acknowledge(processingQueueKey, storedRaw);
                return;
            }

            if (notifyType == NotifyTypeEnum.PRAISE || notifyType == NotifyTypeEnum.CANCEL_PRAISE) {
                // 点赞状态与 Outbox 已在同一数据库事务内提交，发送由 Outbox 调度器负责。
                markCompleted(event.getEventId());
                acknowledge(processingQueueKey, storedRaw);
                return;
            }

            SpringUtil.publishEvent(new NotifyMsgEvent<>(this, notifyType, foot));
            markCompleted(event.getEventId());
            acknowledge(processingQueueKey, storedRaw);
        } catch (Exception e) {
            enqueuePersistRetry(raw, retryCount + 1, e);
            acknowledge(processingQueueKey, storedRaw);
        }
    }

    private void enqueueNotify(NotifyRetryPayload payload) {
        stringRedisTemplate.opsForList().rightPush(FAVOR_NOTIFY_RETRY_QUEUE_KEY, JsonUtil.toStr(payload));
    }

    private void submitNotifyTask(NotifyRetryPayload payload, String processingQueueKey, String storedRaw) {
        favorNotifyExecutor.execute(wrapContextTask(new NotifyDispatchTask(payload, processingQueueKey, storedRaw)));
    }

    private void dispatchNotify(NotifyRetryPayload payload) {
        NotifyTypeEnum notifyType = NotifyTypeEnum.typeOf(payload.getNotifyType());
        if (notifyType == null) {
            return;
        }
        if ((notifyType == NotifyTypeEnum.PRAISE || notifyType == NotifyTypeEnum.CANCEL_PRAISE) && messageQueueService.enabled()) {
            messageQueueService.publish(
                    new MessageQueueEvent<>(notifyType, payload.getFoot()), CommonConstants.ROCKETMQ_TAG_NOTIFY);
            return;
        }
        SpringUtil.publishEvent(new NotifyMsgEvent<>(this, notifyType, payload.getFoot()));
    }

    private void enqueuePersistRetry(String raw, int retryCount, Exception e) {
        if (retryCount > MAX_PERSIST_RETRY_TIMES) {
            stringRedisTemplate.opsForList().rightPush(FAVOR_PERSIST_DEAD_QUEUE_KEY, raw);
            log.error("点赞落库达到最大重试次数, movedToDeadQueue, raw={}", raw, e);
            return;
        }
        PersistRetryPayload payload = new PersistRetryPayload();
        payload.setRaw(raw);
        payload.setRetryCount(retryCount);
        stringRedisTemplate.opsForList().rightPush(FAVOR_PERSIST_RETRY_QUEUE_KEY, JsonUtil.toStr(payload));
        log.warn("点赞落库失败, 已入重试队列, retryCount={}, raw={}", retryCount, raw, e);
    }

    private void enqueueNotifyRetry(NotifyRetryPayload payload, String reason) {
        int retryCount = payload.getRetryCount() == null ? 1 : payload.getRetryCount() + 1;
        payload.setRetryCount(retryCount);
        if (retryCount > MAX_NOTIFY_RETRY_TIMES) {
            stringRedisTemplate.opsForList().rightPush(FAVOR_NOTIFY_DEAD_QUEUE_KEY, JsonUtil.toStr(payload));
            log.error("点赞通知达到最大重试次数, movedToDeadQueue, reason={}, payload={}", reason, JsonUtil.toStr(payload));
            return;
        }
        stringRedisTemplate.opsForList().rightPush(FAVOR_NOTIFY_RETRY_QUEUE_KEY, JsonUtil.toStr(payload));
        log.warn("点赞通知入重试队列, retryCount={}, reason={}", retryCount, reason);
    }

    @PreDestroy
    public void gracefulShutdown() {
        shuttingDown = true;
        log.info("点赞异步链路开始优雅停机");
        drainQueuesBeforeShutdown();
        shutdownExecutor("favorPersistExecutor", favorPersistExecutor);
        shutdownExecutor("favorNotifyExecutor", favorNotifyExecutor);
        log.info("点赞异步链路优雅停机完成");
    }

    private void drainQueuesBeforeShutdown() {
        for (int i = 0; i < SHUTDOWN_DRAIN_MAX_ROUNDS; i++) {
            String favorRaw = stringRedisTemplate.opsForList().leftPop(FAVOR_EVENT_QUEUE_KEY);
            if (favorRaw != null) {
                processFavorEvent(favorRaw, 0, null, null);
                continue;
            }

            String persistRetryRaw = stringRedisTemplate.opsForList().leftPop(FAVOR_PERSIST_RETRY_QUEUE_KEY);
            if (persistRetryRaw != null) {
                PersistRetryPayload payload = JsonUtil.toObj(persistRetryRaw, PersistRetryPayload.class);
                processFavorEvent(payload.getRaw(), safeRetryCount(payload.getRetryCount()), null, null);
                continue;
            }

            String notifyRetryRaw = stringRedisTemplate.opsForList().leftPop(FAVOR_NOTIFY_RETRY_QUEUE_KEY);
            if (notifyRetryRaw != null) {
                NotifyRetryPayload payload = JsonUtil.toObj(notifyRetryRaw, NotifyRetryPayload.class);
                try {
                    dispatchNotify(payload);
                } catch (Exception e) {
                    enqueueNotifyRetry(payload, "shutdown-drain-failed");
                }
                continue;
            }
            break;
        }
    }

    private void shutdownExecutor(String name, ExecutorService executorService) {
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(30, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
                if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                    log.warn("线程池关闭超时, name={}", name);
                }
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private int safeRetryCount(Integer retryCount) {
        return retryCount == null ? 0 : retryCount;
    }

    private ContextAwareRejectableTask wrapContextTask(Runnable runnable) {
        return new ContextAwareRejectableTask(runnable);
    }

    private List<String> loadOrMoveProcessingBatch(String sourceQueueKey, String processingQueueKey, int batchSize) {
        List<String> processing = stringRedisTemplate.opsForList().range(processingQueueKey, 0, batchSize - 1L);
        if (processing != null && !processing.isEmpty()) {
            return processing;
        }
        List<String> moved = new ArrayList<>(batchSize);
        for (int i = 0; i < batchSize; i++) {
            String raw = stringRedisTemplate.opsForList().rightPopAndLeftPush(sourceQueueKey, processingQueueKey);
            if (raw == null) {
                break;
            }
            moved.add(raw);
        }
        return moved;
    }

    private void acknowledge(String processingQueueKey, String storedRaw) {
        if (processingQueueKey != null && storedRaw != null) {
            stringRedisTemplate.opsForList().remove(processingQueueKey, 1, storedRaw);
        }
    }

    private void normalizeEvent(FavorEvent event, String raw) {
        if (event.getEventId() == null || event.getEventId().isBlank()) {
            event.setEventId(UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString());
        }
        if (event.getOccurredAt() == null) {
            event.setOccurredAt(System.currentTimeMillis());
        }
        if (event.getOperationVersion() == null) {
            event.setOperationVersion(event.getOccurredAt());
        }
    }

    private Long nextOperationVersion(FavorEvent event) {
        String key = String.format(FAVOR_OPERATION_VERSION_KEY, event.getArticleId(), event.getUserId());
        return stringRedisTemplate.execute(operationVersionScript, Collections.singletonList(key),
                String.valueOf(event.getOccurredAt()), String.valueOf(TimeUnit.DAYS.toSeconds(7)));
    }

    private boolean isCompleted(String eventId) {
        return Boolean.TRUE.equals(stringRedisTemplate.hasKey(String.format(FAVOR_COMPLETED_EVENT_KEY, eventId)));
    }

    private void markCompleted(String eventId) {
        stringRedisTemplate.opsForValue().set(
                String.format(FAVOR_COMPLETED_EVENT_KEY, eventId), "1", COMPLETED_EVENT_TTL);
    }

    public FavorQueueStatus queueStatus() {
        FavorQueueStatus status = new FavorQueueStatus();
        status.setPending(size(FAVOR_EVENT_QUEUE_KEY));
        status.setProcessing(size(FAVOR_EVENT_PROCESSING_QUEUE_KEY));
        status.setPersistRetry(size(FAVOR_PERSIST_RETRY_QUEUE_KEY));
        status.setPersistRetryProcessing(size(FAVOR_PERSIST_RETRY_PROCESSING_QUEUE_KEY));
        status.setNotifyRetry(size(FAVOR_NOTIFY_RETRY_QUEUE_KEY));
        status.setNotifyRetryProcessing(size(FAVOR_NOTIFY_RETRY_PROCESSING_QUEUE_KEY));
        status.setPersistDead(size(FAVOR_PERSIST_DEAD_QUEUE_KEY));
        status.setNotifyDead(size(FAVOR_NOTIFY_DEAD_QUEUE_KEY));
        return status;
    }

    public boolean replayPersistDeadEvent() {
        String raw = stringRedisTemplate.opsForList().rightPopAndLeftPush(
                FAVOR_PERSIST_DEAD_QUEUE_KEY, FAVOR_EVENT_QUEUE_KEY);
        return raw != null;
    }

    public boolean replayNotifyDeadEvent() {
        String raw = stringRedisTemplate.opsForList().rightPopAndLeftPush(
                FAVOR_NOTIFY_DEAD_QUEUE_KEY, FAVOR_NOTIFY_RETRY_QUEUE_KEY);
        return raw != null;
    }

    private long size(String key) {
        Long size = stringRedisTemplate.opsForList().size(key);
        return size == null ? 0L : size;
    }

    private void aggregatePraiseCounter(UserFootDO foot, OperateTypeEnum operateType) {
        if (foot == null) {
            return;
        }
        if (operateType != OperateTypeEnum.PRAISE && operateType != OperateTypeEnum.CANCEL_PRAISE) {
            return;
        }

        int delta = operateType == OperateTypeEnum.PRAISE ? 1 : -1;
        articlePraiseDelta.computeIfAbsent(foot.getDocumentId(), k -> new LongAdder()).add(delta);
        authorPraiseDelta.computeIfAbsent(foot.getDocumentUserId(), k -> new LongAdder()).add(delta);

        int shard = Math.floorMod(foot.getUserId().intValue(), DEFAULT_LIKE_USER_SHARD_COUNT);
        String shardedKey = String.format(ARTICLE_LIKE_USER_SHARD_KEY, foot.getDocumentId(), shard);
        if (delta > 0) {
            stringRedisTemplate.opsForSet().add(shardedKey, String.valueOf(foot.getUserId()));
        } else {
            stringRedisTemplate.opsForSet().remove(shardedKey, String.valueOf(foot.getUserId()));
        }
    }

    private void flushDeltaByPipeline(Map<Long, LongAdder> deltaMap,
                                      java.util.function.Function<Long, String> keyMapper,
                                      String field) {
        List<Map.Entry<Long, Long>> deltas = new ArrayList<>();
        for (Map.Entry<Long, LongAdder> entry : deltaMap.entrySet()) {
            long delta = entry.getValue().sumThenReset();
            if (delta != 0) {
                deltas.add(Map.entry(entry.getKey(), delta));
            }
        }
        if (deltas.isEmpty()) {
            return;
        }
        RedisClient.PipelineAction pipelineAction = RedisClient.pipelineAction();
        for (Map.Entry<Long, Long> entry : deltas) {
            Long id = entry.getKey();
            Long delta = entry.getValue();
            pipelineAction.add(keyMapper.apply(id), field,
                    (connection, key, value) -> connection.hIncrBy(key, value, delta));
        }
        pipelineAction.execute();
    }

    @Data
    public static class FavorEvent {
        private String eventId;
        private Long articleId;
        private Long authorId;
        private Long userId;
        private Integer operateType;
        private Long occurredAt;
        private Long operationVersion;
    }

    @Data
    public static class PersistRetryPayload {
        private String raw;
        private Integer retryCount;
    }

    @Data
    public static class NotifyRetryPayload {
        private String notifyType;
        private UserFootDO foot;
        private Integer retryCount;
    }

    @Data
    public static class FavorQueueStatus {
        private long pending;
        private long processing;
        private long persistRetry;
        private long persistRetryProcessing;
        private long notifyRetry;
        private long notifyRetryProcessing;
        private long persistDead;
        private long notifyDead;
    }

    private class NotifyDispatchTask implements RejectAwareRunnable {
        private final NotifyRetryPayload payload;
        private final String processingQueueKey;
        private final String storedRaw;

        private NotifyDispatchTask(NotifyRetryPayload payload, String processingQueueKey, String storedRaw) {
            this.payload = payload;
            this.processingQueueKey = processingQueueKey;
            this.storedRaw = storedRaw;
        }

        @Override
        public void run() {
            try {
                dispatchNotify(payload);
                acknowledge(processingQueueKey, storedRaw);
            } catch (Exception e) {
                enqueueNotifyRetry(payload, "notify-dispatch-failed");
                acknowledge(processingQueueKey, storedRaw);
            }
        }

        @Override
        public void onRejected(String poolName) {
            enqueueNotifyRetry(payload, "executor-rejected:" + poolName);
            acknowledge(processingQueueKey, storedRaw);
        }
    }
}
