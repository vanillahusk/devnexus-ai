package com.github.paicoding.forum.service.comment.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.core.cache.RedisClient;
import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Supplier;

@Slf4j
@Service
public class CommentHotspotGovernanceService {
    private static final String COMMENT_TREE_VERSION_KEY = "comment:tree:version:";
    private static final String COMMENT_TREE_CACHE_KEY = "comment:tree:cache:";
    private static final String COMMENT_REPLY_COUNTER_KEY = "comment:tree:reply:counter:";
    private static final String COMMENT_HOT_INDEX_KEY = "comment:tree:hot:index:";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private final Cache<String, CommentForestSnapshot> localForestCache = CacheBuilder.newBuilder()
            .maximumSize(512)
            .expireAfterWrite(45, TimeUnit.SECONDS)
            .build();

    private final ConcurrentHashMap<String, LongAdder> pendingReplyDelta = new ConcurrentHashMap<>();

    public CommentForestSnapshot loadForestSnapshot(Long articleId,
                                                    Long pageNum,
                                                    Long pageSize,
                                                    Supplier<CommentForestSnapshot> loader) {
        long version = getOrInitVersion(articleId);
        String cacheKey = buildPageCacheKey(articleId, pageNum, pageSize, version);

        CommentForestSnapshot local = localForestCache.getIfPresent(cacheKey);
        if (local != null) {
            return local;
        }

        String redisCache = RedisClient.getStr(cacheKey);
        if (StringUtils.isNotBlank(redisCache)) {
            try {
                CommentForestSnapshot snapshot = objectMapper.readValue(redisCache, new TypeReference<CommentForestSnapshot>() {
                });
                localForestCache.put(cacheKey, snapshot);
                return snapshot;
            } catch (Exception e) {
                log.warn("parse comment forest cache failed, key={}", cacheKey, e);
            }
        }

        CommentForestSnapshot snapshot = loader.get();
        if (snapshot == null) {
            snapshot = new CommentForestSnapshot();
        }
        localForestCache.put(cacheKey, snapshot);
        RedisClient.setStrWithExpire(cacheKey, JsonUtil.toStr(snapshot), 120L);
        return snapshot;
    }

    public void onCommentChanged(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return;
        }
        bumpVersion(articleId);
        localForestCache.asMap().keySet().removeIf(key -> key.startsWith(COMMENT_TREE_CACHE_KEY + articleId + ":"));
    }

    public void onReplyDelta(Long articleId, Long topCommentId, int delta) {
        if (articleId == null || articleId <= 0 || topCommentId == null || topCommentId <= 0 || delta == 0) {
            return;
        }
        String mergeKey = articleId + ":" + topCommentId;
        pendingReplyDelta.computeIfAbsent(mergeKey, key -> new LongAdder()).add(delta);
    }

    @Scheduled(fixedDelay = 1000)
    public void flushReplyDelta() {
        if (pendingReplyDelta.isEmpty()) {
            return;
        }

        Map<String, LongAdder> snapshot = new ConcurrentHashMap<>(pendingReplyDelta);
        pendingReplyDelta.keySet().removeAll(snapshot.keySet());
        if (snapshot.isEmpty()) {
            return;
        }

        RedisClient.PipelineAction action = RedisClient.pipelineAction();
        snapshot.forEach((key, adder) -> {
            int split = key.indexOf(':');
            if (split <= 0 || split >= key.length() - 1) {
                return;
            }
            String articleId = key.substring(0, split);
            String topCommentId = key.substring(split + 1);
            long delta = adder.sum();
            if (delta == 0) {
                return;
            }

            action.add(COMMENT_REPLY_COUNTER_KEY + articleId, topCommentId,
                    (connection, redisKey, field) -> connection.hIncrBy(redisKey, field, delta));
            action.add(COMMENT_HOT_INDEX_KEY + articleId, topCommentId,
                    (connection, redisKey, field) -> connection.zIncrBy(redisKey, delta, field));
        });
        action.execute();
    }

    public Integer queryReplyCount(Long articleId, Long topCommentId, Integer fallback) {
        if (articleId == null || topCommentId == null) {
            return fallback == null ? 0 : fallback;
        }
        Integer val = RedisClient.hGet(COMMENT_REPLY_COUNTER_KEY + articleId, String.valueOf(topCommentId), Integer.class);
        if (val == null) {
            return fallback == null ? 0 : fallback;
        }
        return Math.max(val, 0);
    }

    public Long queryHotTopCommentId(Long articleId) {
        if (articleId == null || articleId <= 0) {
            return null;
        }
        List<ImmutablePair<String, Double>> top = RedisClient.zTopNScore(COMMENT_HOT_INDEX_KEY + articleId, 1);
        if (top.isEmpty() || StringUtils.isBlank(top.get(0).getLeft())) {
            return null;
        }
        try {
            return Long.parseLong(top.get(0).getLeft());
        } catch (Exception e) {
            return null;
        }
    }

    private long getOrInitVersion(Long articleId) {
        String key = COMMENT_TREE_VERSION_KEY + articleId;
        String val = RedisClient.getStr(key);
        if (StringUtils.isBlank(val)) {
            RedisClient.setStrWithExpire(key, "1", 24 * 3600L);
            return 1L;
        }
        try {
            return Long.parseLong(val);
        } catch (Exception e) {
            return 1L;
        }
    }

    private void bumpVersion(Long articleId) {
        String key = COMMENT_TREE_VERSION_KEY + articleId;
        long version = getOrInitVersion(articleId) + 1;
        RedisClient.setStrWithExpire(key, String.valueOf(version), 24 * 3600L);
    }

    private String buildPageCacheKey(Long articleId, Long pageNum, Long pageSize, long version) {
        return COMMENT_TREE_CACHE_KEY + articleId + ":" + pageNum + ":" + pageSize + ":v" + version;
    }

    @Data
    public static class CommentForestSnapshot {
        private List<CommentDO> topComments = new ArrayList<>();
        private List<CommentDO> subComments = new ArrayList<>();

        public static CommentForestSnapshot of(List<CommentDO> topComments, List<CommentDO> subComments) {
            CommentForestSnapshot snapshot = new CommentForestSnapshot();
            snapshot.topComments = Objects.requireNonNullElse(topComments, Collections.emptyList());
            snapshot.subComments = Objects.requireNonNullElse(subComments, Collections.emptyList());
            return snapshot;
        }
    }
}