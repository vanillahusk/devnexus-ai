package com.github.paicoding.forum.service.user.service.favor;

import com.github.paicoding.forum.core.util.JsonUtil;
import com.github.paicoding.forum.service.notify.service.MessageQueueService;
import com.github.paicoding.forum.service.user.service.UserFootService;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FavorAsyncWriteServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ListOperations<String, String> listOperations;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserFootService userFootService;
    @Mock
    private MessageQueueService messageQueueService;
    @Mock
    private ExecutorService persistExecutor;
    @Mock
    private ExecutorService notifyExecutor;

    private FavorAsyncWriteService service;

    @BeforeEach
    void setUp() {
        service = new FavorAsyncWriteService();
        ReflectionTestUtils.setField(service, "stringRedisTemplate", redisTemplate);
        ReflectionTestUtils.setField(service, "userFootService", userFootService);
        ReflectionTestUtils.setField(service, "messageQueueService", messageQueueService);
        ReflectionTestUtils.setField(service, "favorPersistExecutor", persistExecutor);
        ReflectionTestUtils.setField(service, "favorNotifyExecutor", notifyExecutor);
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1_000L);
        lenient().doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(persistExecutor).execute(any(Runnable.class));
    }

    @Test
    void shouldGenerateStableEventMetadataWhenEnqueued() {
        FavorAsyncWriteService.FavorEvent event = new FavorAsyncWriteService.FavorEvent();
        event.setArticleId(1L);
        event.setAuthorId(2L);
        event.setUserId(3L);
        event.setOperateType(1);

        service.enqueue(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(listOperations).rightPush(anyString(), payloadCaptor.capture());
        FavorAsyncWriteService.FavorEvent stored = JsonUtil.toObj(
                payloadCaptor.getValue(), FavorAsyncWriteService.FavorEvent.class);
        assertNotNull(stored.getEventId());
        assertFalse(stored.getEventId().isBlank());
        assertNotNull(stored.getOccurredAt());
        assertEquals(1_000L, stored.getOperationVersion());
    }

    @Test
    void shouldRecoverCompletedEventFromProcessingQueueWithoutWritingTwice() {
        FavorAsyncWriteService.FavorEvent event = new FavorAsyncWriteService.FavorEvent();
        event.setEventId("event-1001");
        event.setArticleId(1L);
        event.setAuthorId(2L);
        event.setUserId(3L);
        event.setOperateType(1);
        String raw = JsonUtil.toStr(event);

        when(listOperations.range("favor:event:processing:queue", 0, 199L)).thenReturn(List.of(raw));
        when(redisTemplate.hasKey("favor:event:completed:event-1001")).thenReturn(true);

        service.flushFavorEvents();

        verify(listOperations).remove("favor:event:processing:queue", 1, raw);
        verify(userFootService, never()).saveOrUpdateUserFootWithOutbox(
                any(), any(), any(), any(), any(), anyString(), anyLong());
    }

    @Test
    void shouldNotCountOrNotifyWhenBusinessStateDidNotChange() {
        FavorAsyncWriteService.FavorEvent event = new FavorAsyncWriteService.FavorEvent();
        event.setEventId("event-duplicate-like");
        event.setArticleId(1L);
        event.setAuthorId(2L);
        event.setUserId(3L);
        event.setOperateType(2);
        String raw = JsonUtil.toStr(event);
        UserFootDO foot = new UserFootDO();

        when(listOperations.range("favor:event:processing:queue", 0, 199L)).thenReturn(List.of(raw));
        when(redisTemplate.hasKey("favor:event:completed:event-duplicate-like")).thenReturn(false);
        when(userFootService.saveOrUpdateUserFootWithOutbox(
                any(), any(), any(), any(), any(), anyString(), anyLong()))
                .thenReturn(new UserFootService.UserFootUpdateResult(foot, false));

        service.flushFavorEvents();

        verify(valueOperations).set(anyString(), anyString(), any());
        verify(messageQueueService, never()).publish(any(), anyString());
        verify(listOperations).remove("favor:event:processing:queue", 1, raw);
    }

    @Test
    void shouldExposeQueueBacklogForOperationalEvidence() {
        when(listOperations.size(anyString())).thenReturn(3L);

        FavorAsyncWriteService.FavorQueueStatus status = service.queueStatus();

        assertEquals(3L, status.getPending());
        assertEquals(3L, status.getProcessing());
        assertEquals(3L, status.getPersistDead());
        assertEquals(3L, status.getNotifyDead());
    }

    @Test
    void shouldUseProductionRateLimitDefaults() {
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString()))
                .thenReturn(1L);

        service.allowFavorRequest(11L, 22L);

        verify(redisTemplate).execute(any(DefaultRedisScript.class), anyList(), 
                org.mockito.ArgumentMatchers.eq("60"), org.mockito.ArgumentMatchers.eq("5"));
    }

    @Test
    void shouldAllowPressureProfileToDisableRateLimit() {
        ReflectionTestUtils.setField(service, "rateLimitEnabled", false);

        assertEquals(true, service.allowFavorRequest(11L, 22L));
        verify(redisTemplate, never()).execute(any(DefaultRedisScript.class), anyList(), anyString(), anyString());
    }
}
