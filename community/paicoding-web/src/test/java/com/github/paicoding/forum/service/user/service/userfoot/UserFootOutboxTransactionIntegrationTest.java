package com.github.paicoding.forum.service.user.service.userfoot;

import com.github.paicoding.forum.api.model.enums.DocumentTypeEnum;
import com.github.paicoding.forum.api.model.enums.OperateTypeEnum;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import com.github.paicoding.forum.service.user.service.favor.FavorReconciliationService;
import com.github.paicoding.forum.service.user.repository.dao.UserFootDao;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.service.UserFootService;
import com.github.paicoding.forum.web.QuickForumApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * 使用真实 MySQL 验证 user_foot 与 mq_outbox_event 的事务原子性。
 * 仅在显式传入 -Doutbox.rollback.integration.enabled=true 时执行。
 */
@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=false"
})
@EnabledIfSystemProperty(named = "outbox.rollback.integration.enabled", matches = "true")
class UserFootOutboxTransactionIntegrationTest {
    private static final long TEST_USER_ID = 7L;
    private static final long TEST_DOCUMENT_ID = 9_000_000_007L;

    @Autowired
    private UserFootService userFootService;

    @Autowired
    private UserFootDao userFootDao;

    @Autowired
    private FavorReconciliationService reconciliationService;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @MockBean
    private MqOutboxService outboxService;

    @MockBean
    private MessageQueueService messageQueueService;

    @Test
    void shouldRollbackUserFootWhenOutboxInsertFails() {
        insertInitialFoot();

        try {
            doThrow(new IllegalStateException("injected outbox insert failure"))
                    .when(outboxService).saveFavorNotify(any(), any(), any());

            assertThrows(IllegalStateException.class, () -> userFootService.saveOrUpdateUserFootWithOutbox(
                    DocumentTypeEnum.ARTICLE, TEST_DOCUMENT_ID, 1L, TEST_USER_ID,
                    OperateTypeEnum.PRAISE, "rollback-proof-event", 1L));

            UserFootDO afterFailure = userFootDao.getByDocumentAndUserId(
                    TEST_DOCUMENT_ID, DocumentTypeEnum.ARTICLE.getCode(), TEST_USER_ID);
            assertEquals(2, afterFailure.getPraiseStat(),
                    "Outbox 插入失败时，点赞状态更新必须随外层事务一起回滚");
        } finally {
            deleteTestFoot();
        }
    }

    @Test
    void shouldRejectOlderFavorOperationAcrossTransactions() {
        insertInitialFoot();
        try {
            userFootService.saveOrUpdateUserFootWithOutbox(
                    DocumentTypeEnum.ARTICLE, TEST_DOCUMENT_ID, 1L, TEST_USER_ID,
                    OperateTypeEnum.CANCEL_PRAISE, "new-cancel", 200L);
            userFootService.saveOrUpdateUserFootWithOutbox(
                    DocumentTypeEnum.ARTICLE, TEST_DOCUMENT_ID, 1L, TEST_USER_ID,
                    OperateTypeEnum.PRAISE, "old-praise", 100L);

            UserFootDO finalFoot = userFootDao.getByDocumentAndUserId(
                    TEST_DOCUMENT_ID, DocumentTypeEnum.ARTICLE.getCode(), TEST_USER_ID);
            assertEquals(2, finalFoot.getPraiseStat(), "旧点赞事件不能覆盖较新的取消点赞状态");
            assertEquals(200L, finalFoot.getFavorVersion());
        } finally {
            deleteTestFoot();
        }
    }

    @Test
    void shouldRepairRedisFavorSetFromMysql() {
        insertInitialFoot();
        UserFootDO initial = userFootDao.getByDocumentAndUserId(
                TEST_DOCUMENT_ID, DocumentTypeEnum.ARTICLE.getCode(), TEST_USER_ID);
        initial.setPraiseStat(1);
        userFootDao.updateById(initial);
        String expectedKey = "favor:liked:article:" + TEST_DOCUMENT_ID + ":7";
        String staleKey = "favor:liked:article:" + TEST_DOCUMENT_ID + ":3";
        redisTemplate.delete(expectedKey);
        redisTemplate.opsForSet().add(staleKey, "99");

        try {
            FavorReconciliationService.ReconciliationResult result =
                    reconciliationService.repair(TEST_DOCUMENT_ID);

            assertEquals(1, result.missingInRedis());
            assertEquals(1, result.staleInRedis());
            assertEquals(true, result.repaired());
            assertEquals(true, redisTemplate.opsForSet().isMember(expectedKey, "7"));
            assertEquals(false, redisTemplate.opsForSet().isMember(staleKey, "99"));
        } finally {
            redisTemplate.delete(expectedKey);
            redisTemplate.delete(staleKey);
            deleteTestFoot();
        }
    }

    private void insertInitialFoot() {
        deleteTestFoot();
        UserFootDO initial = new UserFootDO();
        initial.setUserId(TEST_USER_ID);
        initial.setDocumentId(TEST_DOCUMENT_ID);
        initial.setDocumentType(DocumentTypeEnum.ARTICLE.getCode());
        initial.setDocumentUserId(1L);
        initial.setPraiseStat(2);
        initial.setFavorVersion(0L);
        initial.setCollectionStat(2);
        initial.setReadStat(2);
        initial.setCommentStat(2);
        userFootDao.save(initial);
    }

    private void deleteTestFoot() {
        userFootDao.lambdaUpdate()
                .eq(UserFootDO::getUserId, TEST_USER_ID)
                .eq(UserFootDO::getDocumentId, TEST_DOCUMENT_ID)
                .eq(UserFootDO::getDocumentType, DocumentTypeEnum.ARTICLE.getCode())
                .remove();
    }
}
