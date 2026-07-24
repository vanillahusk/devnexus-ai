package com.github.paicoding.forum.service.user.service.favor;

import com.github.paicoding.forum.service.user.repository.dao.UserFootDao;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FavorReconciliationServiceTest {
    private final UserFootDao dao = mock(UserFootDao.class);
    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    private final FavorAsyncWriteService asyncWriteService = mock(FavorAsyncWriteService.class);
    private final SetOperations<String, String> setOperations = mock(SetOperations.class);
    private final FavorReconciliationService service =
            new FavorReconciliationService(dao, redisTemplate, asyncWriteService);

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members(anyString())).thenReturn(Set.of());
        when(setOperations.members("favor:liked:article:14:7")).thenReturn(Set.of("7"));
        when(setOperations.members("favor:liked:article:14:9")).thenReturn(Set.of("9"));
        when(dao.listPraisedUserIds(14L)).thenReturn(List.of(7L, 8L));
    }

    @Test
    void shouldReportAndRepairRedisDifferencesWhenQueuesAreIdle() {
        when(asyncWriteService.queueStatus()).thenReturn(queueStatus(0));

        FavorReconciliationService.ReconciliationResult result = service.repair(14L);

        assertEquals(1, result.missingInRedis());
        assertEquals(1, result.staleInRedis());
        assertTrue(result.repaired());
        verify(setOperations).add("favor:liked:article:14:8", "8");
        verify(setOperations).remove("favor:liked:article:14:9", "9");
    }

    @Test
    void shouldRefuseRepairWhilePersistenceQueueHasEvents() {
        when(asyncWriteService.queueStatus()).thenReturn(queueStatus(1));

        FavorReconciliationService.ReconciliationResult result = service.repair(14L);

        assertFalse(result.repaired());
        assertTrue(result.queuesInFlight());
        verify(setOperations, never()).add(anyString(), anyString());
        verify(setOperations, never()).remove(anyString(), anyString());
    }

    private FavorAsyncWriteService.FavorQueueStatus queueStatus(long pending) {
        FavorAsyncWriteService.FavorQueueStatus status = new FavorAsyncWriteService.FavorQueueStatus();
        status.setPending(pending);
        return status;
    }
}
