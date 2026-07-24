package com.github.paicoding.forum.service.comment.service;

import com.github.paicoding.forum.core.cache.RedisClient;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.web.QuickForumApplication;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = QuickForumApplication.class, properties = {
        "paicoding.mq.provider=none",
        "spring.liquibase.enabled=false",
        "paicoding.comment.rate-limit.limit=2"
})
@EnabledIfSystemProperty(named = "comment.tree.redis.integration.enabled", matches = "true")
class CommentHotspotGovernanceIntegrationTest {
    private static final long ARTICLE_ID = 9_000_000_202L;
    private static final long TOP_COMMENT_ID = 9_000_000_203L;

    @Autowired
    private CommentHotspotGovernanceService service;

    @Autowired
    private CommentRateLimitService rateLimitService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @MockBean
    private MessageQueueService messageQueueService;

    @AfterEach
    void cleanUp() {
        RedisClient.del("comment:tree:version:" + ARTICLE_ID);
        RedisClient.del("comment:tree:cache:" + ARTICLE_ID + ":1:10:v1");
        RedisClient.del("comment:tree:cache:" + ARTICLE_ID + ":1:10:v2");
        RedisClient.del("comment:tree:reply:counter:" + ARTICLE_ID);
        RedisClient.del("comment:tree:hot:index:" + ARTICLE_ID);
        long currentWindow = System.currentTimeMillis() / 1000L;
        stringRedisTemplate.delete("comment:write:limiter:" + ARTICLE_ID + ":0:" + currentWindow);
        stringRedisTemplate.delete("comment:write:limiter:" + ARTICLE_ID + ":0:" + (currentWindow - 1));
    }

    @Test
    void shouldCacheForestAndInvalidateWithVersion() {
        cleanUp();
        AtomicInteger loadCount = new AtomicInteger();

        service.loadForestSnapshot(ARTICLE_ID, 1L, 10L, () -> snapshot(loadCount.incrementAndGet()));
        service.loadForestSnapshot(ARTICLE_ID, 1L, 10L, () -> snapshot(loadCount.incrementAndGet()));
        assertEquals(1, loadCount.get(), "相同版本的评论树只能回源一次");

        service.onCommentChanged(ARTICLE_ID);
        service.loadForestSnapshot(ARTICLE_ID, 1L, 10L, () -> snapshot(loadCount.incrementAndGet()));
        assertEquals(2, loadCount.get(), "评论变化后必须通过版本号失效旧快照");
    }

    @Test
    void shouldMergeReplyCountersAndBuildHotIndex() {
        cleanUp();
        service.onReplyDelta(ARTICLE_ID, TOP_COMMENT_ID, 1);
        service.onReplyDelta(ARTICLE_ID, TOP_COMMENT_ID, 1);
        service.flushReplyDelta();

        assertEquals(2, service.queryReplyCount(ARTICLE_ID, TOP_COMMENT_ID, 0));
        assertEquals(TOP_COMMENT_ID, service.queryHotTopCommentId(ARTICLE_ID));
    }

    @Test
    void shouldExecuteAtomicPerArticleRateLimitLua() {
        cleanUp();
        rateLimitService.check(ARTICLE_ID, 0L);
        rateLimitService.check(ARTICLE_ID, 0L);

        assertThrows(RuntimeException.class, () -> rateLimitService.check(ARTICLE_ID, 0L));
    }

    private CommentHotspotGovernanceService.CommentForestSnapshot snapshot(long id) {
        CommentDO comment = new CommentDO();
        comment.setId(id);
        return CommentHotspotGovernanceService.CommentForestSnapshot.of(List.of(comment), List.of());
    }
}
