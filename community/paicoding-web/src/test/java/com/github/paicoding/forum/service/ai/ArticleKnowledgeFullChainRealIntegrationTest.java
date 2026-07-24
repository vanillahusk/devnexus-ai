package com.github.paicoding.forum.service.ai;

import com.github.paicoding.forum.api.model.enums.PushStatusEnum;
import com.github.paicoding.forum.api.model.enums.YesOrNoEnum;
import com.github.paicoding.forum.api.model.enums.ai.ArticleKnowledgeOperationEnum;
import com.github.paicoding.forum.api.model.event.ArticleKnowledgeEvent;
import com.github.paicoding.forum.api.model.vo.article.ArticlePostReq;
import com.github.paicoding.forum.core.common.CommonConstants;
import com.github.paicoding.forum.service.ai.index.ArticleKnowledgeIndexState;
import com.github.paicoding.forum.service.ai.repository.dao.ArticleKnowledgeIndexStateDao;
import com.github.paicoding.forum.service.ai.repository.entity.ArticleKnowledgeIndexStateDO;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.repository.mapper.ArticleDetailMapper;
import com.github.paicoding.forum.service.article.service.ArticleSettingService;
import com.github.paicoding.forum.service.notify.repository.dao.MqOutboxEventDao;
import com.github.paicoding.forum.service.notify.repository.entity.MqOutboxEventDO;
import com.github.paicoding.forum.service.notify.repository.enums.MqOutboxStatusEnum;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.web.QuickForumApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 真实验证业务事务/Outbox -> RocketMQ -> 独立 AIGC consumer -> Ragent -> 状态回写。
 * 默认禁用，只允许由有资源和清理边界的 worker 开启。
 */
@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=rocketmq",
        "paicoding.mq.rocketmq.name-server=127.0.0.1:9876",
        "rocketmq.name-server=127.0.0.1:9876",
        "paicoding.mq.outbox.flush-delay-ms=3600000",
        "ai.knowledge.ragent.enabled=false",
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.config.enabled=false"
})
@EnabledIfSystemProperty(named = "article.knowledge.full-chain.integration.enabled", matches = "true")
class ArticleKnowledgeFullChainRealIntegrationTest {
    private static final long ARTICLE_ID = 9_000_000_401L;
    private static final String AGGREGATE_ID = "article:" + ARTICLE_ID;
    private static final String MAPPING_KEY = "ai:knowledge:ragent:doc-mapping";
    private static final String STATUS_KEY = "ai:knowledge:ragent:sync-status";

    @Autowired
    private ArticleDao articleDao;
    @Autowired
    private ArticleDetailMapper articleDetailMapper;
    @Autowired
    private ArticleSettingService articleSettingService;
    @Autowired
    private MqOutboxService outboxService;
    @Autowired
    private MessageQueueService messageQueueService;
    @Autowired
    private MqOutboxEventDao outboxEventDao;
    @Autowired
    private ArticleKnowledgeIndexStateDao indexStateDao;
    @Autowired
    private StringRedisTemplate redis;

    @BeforeEach
    void setUp() {
        cleanUp();
        ArticleDO article = new ArticleDO();
        article.setId(ARTICLE_ID);
        article.setUserId(1L);
        article.setArticleType(1);
        article.setTitle("full-chain-before");
        article.setStatus(PushStatusEnum.OFFLINE.getCode());
        article.setKnowledgeVersion(0L);
        article.setDeleted(YesOrNoEnum.NO.getCode());
        articleDao.save(article);
        articleDao.saveArticleContent(ARTICLE_ID, "FULL-CHAIN-V1-" + ARTICLE_ID);
    }

    @AfterEach
    void tearDown() {
        cleanUp();
    }

    @Test
    void shouldConvergeOnlineUpdateAndOfflineThroughOutboxRocketMqAndAigc() {
        updateArticle("full-chain-online", PushStatusEnum.ONLINE);
        dispatchAndAwaitOutboxSent();
        ArticleKnowledgeIndexState online = awaitState(1L, ArticleKnowledgeOperationEnum.ONLINE);
        String firstDocId = mappingDocId();
        assertNotNull(firstDocId);

        // 新 eventId、相同业务版本与操作：必须由消费端按业务键判定为 DUPLICATE。
        publishDirect(ArticleKnowledgeEvent.create(
                ARTICLE_ID, 1L, ArticleKnowledgeOperationEnum.ONLINE));

        articleDao.updateArticleContent(ARTICLE_ID, "FULL-CHAIN-V2-" + ARTICLE_ID, true);
        updateArticle("full-chain-update", PushStatusEnum.ONLINE);
        dispatchAndAwaitOutboxSent();
        ArticleKnowledgeIndexState updated = awaitState(2L, ArticleKnowledgeOperationEnum.UPDATE);
        String secondDocId = mappingDocId();
        assertNotNull(secondDocId);
        assertNotEquals(firstDocId, secondDocId);
        assertNotEquals(online.eventId(), updated.eventId());

        // v2 已生效后再投递 v1；同一 articleId 作为顺序键，后续 v3 完成即证明它已先被消费。
        publishDirect(ArticleKnowledgeEvent.create(
                ARTICLE_ID, 1L, ArticleKnowledgeOperationEnum.UPDATE));

        updateArticle("full-chain-offline", PushStatusEnum.OFFLINE);
        dispatchAndAwaitOutboxSent();
        awaitState(3L, ArticleKnowledgeOperationEnum.OFFLINE);
        awaitMappingDeleted();

        // 先投递领先于事实源的 v4，消费者必须失败并由 RocketMQ 保留重试语义。
        ArticleKnowledgeEvent recoveryEvent = ArticleKnowledgeEvent.create(
                ARTICLE_ID, 4L, ArticleKnowledgeOperationEnum.ONLINE);
        publishDirect(recoveryEvent);
        waitForFirstFailedAttempt();

        // 不创建第二条事件，只推进 MySQL 事实版本；同一消息重试后应自动收敛。
        advanceFactWithoutOutbox("full-chain-recovered", 4L, PushStatusEnum.ONLINE);
        ArticleKnowledgeIndexState recovered = awaitState(4L, ArticleKnowledgeOperationEnum.ONLINE);
        assertEquals(recoveryEvent.getEventId(), recovered.eventId());
        assertNotNull(mappingDocId());
    }

    private void publishDirect(ArticleKnowledgeEvent event) {
        messageQueueService.publish(
                CommonConstants.ROCKETMQ_TOPIC_ARTICLE_KNOWLEDGE,
                CommonConstants.ROCKETMQ_TAG_ARTICLE_KNOWLEDGE_V1,
                event,
                event.getEventId());
    }

    private void waitForFirstFailedAttempt() {
        try {
            Thread.sleep(5_000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError("failure-recovery validation interrupted", interrupted);
        }
        ArticleKnowledgeIndexState state = indexStateDao.findState(ARTICLE_ID);
        assertNotNull(state);
        assertEquals(3L, state.articleVersion());
        assertEquals(ArticleKnowledgeOperationEnum.OFFLINE, state.operation());
    }

    private void advanceFactWithoutOutbox(String title, long version, PushStatusEnum status) {
        ArticleDO article = articleDao.getById(ARTICLE_ID);
        assertNotNull(article);
        article.setTitle(title);
        article.setStatus(status.getCode());
        article.setKnowledgeVersion(version);
        articleDao.updateById(article);
        articleDao.updateArticleContent(ARTICLE_ID, "FULL-CHAIN-RECOVERED-" + ARTICLE_ID, true);
    }

    private void updateArticle(String title, PushStatusEnum status) {
        ArticlePostReq request = new ArticlePostReq();
        request.setArticleId(ARTICLE_ID);
        request.setTitle(title);
        request.setStatus(status.getCode());
        articleSettingService.updateArticle(request);
    }

    private void dispatchAndAwaitOutboxSent() {
        outboxService.dispatch();
        await(Duration.ofSeconds(20), () -> outboxEventDao.lambdaQuery()
                .eq(MqOutboxEventDO::getAggregateId, AGGREGATE_ID)
                .eq(MqOutboxEventDO::getStatus, MqOutboxStatusEnum.SENT.getCode())
                .count() == expectedVersion());
    }

    private long expectedVersion() {
        ArticleDO article = articleDao.getById(ARTICLE_ID);
        return article == null || article.getKnowledgeVersion() == null ? 0L : article.getKnowledgeVersion();
    }

    private ArticleKnowledgeIndexState awaitState(long version, ArticleKnowledgeOperationEnum operation) {
        final ArticleKnowledgeIndexState[] found = new ArticleKnowledgeIndexState[1];
        await(Duration.ofSeconds(120), () -> {
            found[0] = indexStateDao.findState(ARTICLE_ID);
            return found[0] != null && found[0].articleVersion() == version
                    && found[0].operation() == operation;
        });
        assertEquals(version, found[0].articleVersion());
        assertEquals(operation, found[0].operation());
        return found[0];
    }

    private String mappingDocId() {
        Object value = redis.opsForHash().get(MAPPING_KEY, AGGREGATE_ID);
        return value instanceof String text ? text : null;
    }

    private void awaitMappingDeleted() {
        await(Duration.ofSeconds(30), () -> mappingDocId() == null);
        assertNull(mappingDocId());
    }

    private void await(Duration timeout, CheckedCondition condition) {
        long deadline = System.nanoTime() + timeout.toNanos();
        Throwable lastFailure = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.matches()) {
                    return;
                }
            } catch (Throwable failure) {
                lastFailure = failure;
            }
            try {
                Thread.sleep(500L);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("full-chain validation interrupted", interrupted);
            }
        }
        throw new AssertionError("condition not satisfied within " + timeout, lastFailure);
    }

    private void cleanUp() {
        redis.opsForHash().delete(MAPPING_KEY, AGGREGATE_ID);
        redis.opsForHash().delete(STATUS_KEY, AGGREGATE_ID);
        indexStateDao.lambdaUpdate().eq(ArticleKnowledgeIndexStateDO::getArticleId, ARTICLE_ID).remove();
        outboxEventDao.lambdaUpdate().eq(MqOutboxEventDO::getAggregateId, AGGREGATE_ID).remove();
        articleDetailMapper.deleteByMap(Map.of("article_id", ARTICLE_ID));
        articleDao.removeById(ARTICLE_ID);
    }

    @FunctionalInterface
    private interface CheckedCondition {
        boolean matches() throws Exception;
    }
}
