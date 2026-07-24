package com.github.paicoding.forum.service.article.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.paicoding.forum.service.article.repository.entity.ArticleDO;
import com.github.paicoding.forum.service.article.service.ArticleReadService;
import com.github.paicoding.forum.core.cache.RedisClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class HotArticleBasicCacheService {

    private static final String HOT_ARTICLE_BASIC_KEY = "hot_article_basic:";
    private static final long REDIS_EXPIRE_SECONDS = 600L;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final Cache<Long, ArticleDO> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(5, TimeUnit.MINUTES)
            .build();

    @Autowired
    private ArticleReadService articleReadService;

    public ArticleDO queryBasicArticle(Long articleId) {
        ArticleDO local = localCache.getIfPresent(articleId);
        if (local != null) {
            return local;
        }

        Object redisObj = RedisClient.getObject(HOT_ARTICLE_BASIC_KEY + articleId);
        if (redisObj != null) {
            ArticleDO cached = OBJECT_MAPPER.convertValue(redisObj, ArticleDO.class);
            localCache.put(articleId, cached);
            return cached;
        }

        ArticleDO article = articleReadService.queryBasicArticle(articleId);
        if (article != null) {
            RedisClient.setObject(HOT_ARTICLE_BASIC_KEY + articleId, article);
            RedisClient.expire(HOT_ARTICLE_BASIC_KEY + articleId, REDIS_EXPIRE_SECONDS);
            localCache.put(articleId, article);
        }
        return article;
    }
}
