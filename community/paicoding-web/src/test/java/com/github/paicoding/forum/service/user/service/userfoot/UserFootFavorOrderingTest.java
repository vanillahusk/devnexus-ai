package com.github.paicoding.forum.service.user.service.userfoot;

import com.github.paicoding.forum.api.model.enums.DocumentTypeEnum;
import com.github.paicoding.forum.api.model.enums.OperateTypeEnum;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import com.github.paicoding.forum.service.user.repository.dao.UserFootDao;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import com.github.paicoding.forum.service.user.service.UserFootService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserFootFavorOrderingTest {
    private final UserFootDao dao = mock(UserFootDao.class);
    private final MqOutboxService outboxService = mock(MqOutboxService.class);
    private final UserFootServiceImpl service = new UserFootServiceImpl(dao, outboxService);

    @Test
    void shouldIgnoreOlderFavorEvent() {
        UserFootDO current = foot(2, 200L);
        when(dao.getByDocumentAndUserIdForUpdate(14L, 1, 7L)).thenReturn(current);

        UserFootService.UserFootUpdateResult result = service.saveOrUpdateUserFootWithOutbox(
                DocumentTypeEnum.ARTICLE, 14L, 1L, 7L,
                OperateTypeEnum.PRAISE, "old-praise", 100L);

        assertFalse(result.changed());
        assertEquals(2, result.foot().getPraiseStat());
        assertEquals(200L, result.foot().getFavorVersion());
        verify(dao, never()).updateById(current);
        verify(outboxService, never()).saveFavorNotify(
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldPersistAndPublishNewerFavorEvent() {
        UserFootDO current = foot(2, 200L);
        when(dao.getByDocumentAndUserIdForUpdate(14L, 1, 7L)).thenReturn(current);

        UserFootService.UserFootUpdateResult result = service.saveOrUpdateUserFootWithOutbox(
                DocumentTypeEnum.ARTICLE, 14L, 1L, 7L,
                OperateTypeEnum.PRAISE, "new-praise", 300L);

        assertTrue(result.changed());
        assertEquals(1, result.foot().getPraiseStat());
        assertEquals(300L, result.foot().getFavorVersion());
        verify(dao).updateById(current);
        verify(outboxService).saveFavorNotify(
                org.mockito.ArgumentMatchers.eq("new-praise"), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.eq(current));
    }

    private UserFootDO foot(int praiseStat, long version) {
        UserFootDO foot = new UserFootDO();
        foot.setId(1L);
        foot.setUserId(7L);
        foot.setDocumentId(14L);
        foot.setDocumentType(1);
        foot.setPraiseStat(praiseStat);
        foot.setFavorVersion(version);
        return foot;
    }
}
