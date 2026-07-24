package com.github.paicoding.forum.service.user.service.favor;

import com.github.paicoding.forum.service.user.repository.dao.UserFootDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class FavorReconciliationService {
    private static final int SHARD_COUNT = 16;
    private static final int SAMPLE_LIMIT = 20;
    private static final String SHARD_KEY = "favor:liked:article:%d:%d";

    private final UserFootDao userFootDao;
    private final StringRedisTemplate redisTemplate;
    private final FavorAsyncWriteService favorAsyncWriteService;

    public ReconciliationResult inspect(Long articleId) {
        return reconcile(articleId, false);
    }

    public ReconciliationResult repair(Long articleId) {
        return reconcile(articleId, true);
    }

    public ReconciliationResult reconcile(Long articleId, boolean repair) {
        if (articleId == null || articleId <= 0) {
            throw new IllegalArgumentException("articleId must be positive");
        }
        FavorAsyncWriteService.FavorQueueStatus queueStatus = favorAsyncWriteService.queueStatus();
        boolean inFlight = hasInFlight(queueStatus);

        Set<Long> databaseUsers = new HashSet<>(userFootDao.listPraisedUserIds(articleId));
        Set<Long> redisUsers = loadRedisUsers(articleId);
        Set<Long> missingInRedis = difference(databaseUsers, redisUsers);
        Set<Long> staleInRedis = difference(redisUsers, databaseUsers);

        boolean repaired = false;
        String reason = "inspection only";
        if (repair && inFlight) {
            reason = "repair skipped because favor queues are not idle";
        } else if (repair) {
            applyDiff(articleId, missingInRedis, staleInRedis);
            repaired = true;
            reason = "redis sets reconciled from mysql";
        }
        return new ReconciliationResult(articleId, databaseUsers.size(), redisUsers.size(),
                missingInRedis.size(), staleInRedis.size(), sample(missingInRedis), sample(staleInRedis),
                inFlight, repaired, reason);
    }

    @Scheduled(cron = "${favor.reconcile.cron:0 30 3 * * ?}")
    public void reconcileRecentlyChangedArticles() {
        FavorAsyncWriteService.FavorQueueStatus status = favorAsyncWriteService.queueStatus();
        if (hasInFlight(status)) {
            log.info("skip scheduled favor reconciliation because queues are not idle");
            return;
        }
        Date since = new Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(1));
        for (Long articleId : userFootDao.listRecentlyChangedArticleIds(since, 1000)) {
            ReconciliationResult result = repair(articleId);
            if (result.missingInRedis() > 0 || result.staleInRedis() > 0) {
                log.warn("favor reconciliation repaired differences, result={}", result);
            }
        }
    }

    private Set<Long> loadRedisUsers(Long articleId) {
        Set<Long> users = new HashSet<>();
        SetOperations<String, String> operations = redisTemplate.opsForSet();
        for (int shard = 0; shard < SHARD_COUNT; shard++) {
            Set<String> members = operations.members(String.format(SHARD_KEY, articleId, shard));
            if (members == null) {
                continue;
            }
            for (String member : members) {
                try {
                    users.add(Long.valueOf(member));
                } catch (NumberFormatException e) {
                    log.warn("ignore invalid favor redis member, articleId={}, member={}", articleId, member);
                }
            }
        }
        return users;
    }

    private void applyDiff(Long articleId, Set<Long> missing, Set<Long> stale) {
        SetOperations<String, String> operations = redisTemplate.opsForSet();
        for (Long userId : missing) {
            operations.add(shardKey(articleId, userId), String.valueOf(userId));
        }
        for (Long userId : stale) {
            operations.remove(shardKey(articleId, userId), String.valueOf(userId));
        }
    }

    private String shardKey(Long articleId, Long userId) {
        return String.format(SHARD_KEY, articleId, Math.floorMod(userId.intValue(), SHARD_COUNT));
    }

    private boolean hasInFlight(FavorAsyncWriteService.FavorQueueStatus status) {
        return status.getPending() + status.getProcessing() + status.getPersistRetry()
                + status.getPersistRetryProcessing() > 0;
    }

    private Set<Long> difference(Set<Long> left, Set<Long> right) {
        Set<Long> result = new LinkedHashSet<>(left);
        result.removeAll(right);
        return result;
    }

    private List<Long> sample(Set<Long> values) {
        List<Long> sample = new ArrayList<>(SAMPLE_LIMIT);
        for (Long value : values) {
            if (sample.size() == SAMPLE_LIMIT) {
                break;
            }
            sample.add(value);
        }
        return sample;
    }

    public record ReconciliationResult(Long articleId, int databaseLikedUsers, int redisLikedUsers,
                                       int missingInRedis, int staleInRedis,
                                       List<Long> missingSample, List<Long> staleSample,
                                       boolean queuesInFlight, boolean repaired, String reason) {
    }
}
