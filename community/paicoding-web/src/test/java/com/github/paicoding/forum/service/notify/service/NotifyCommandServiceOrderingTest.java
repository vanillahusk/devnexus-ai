package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.event.MessageQueueEvent;
import com.github.paicoding.forum.service.article.repository.dao.ArticleDao;
import com.github.paicoding.forum.service.comment.repository.dao.CommentDao;
import com.github.paicoding.forum.service.notify.repository.dao.NotifyMsgDao;
import com.github.paicoding.forum.service.notify.repository.dao.NotifyProjectionStateDao;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyMsgDO;
import com.github.paicoding.forum.service.notify.repository.entity.NotifyProjectionStateDO;
import com.github.paicoding.forum.service.notify.service.impl.NotifyCommandServiceImpl;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotifyCommandServiceOrderingTest {
    @Mock private ArticleDao articleDao;
    @Mock private CommentDao commentDao;
    @Mock private NotifyMsgDao notifyMsgDao;
    @Mock private NotifyProjectionStateDao projectionStateDao;

    private NotifyCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotifyCommandServiceImpl(articleDao, commentDao, notifyMsgDao, projectionStateDao);
    }

    @Test
    void shouldIgnoreLatePraiseAfterNewerCancellation() {
        MessageQueueEvent<UserFootDO> praise = event(NotifyTypeEnum.PRAISE, 10L);
        when(projectionStateDao.lockOrCreate("PRAISE:7:14"))
                .thenReturn(state(11L, 0));

        service.saveArticleNotify(praise);

        verify(notifyMsgDao, never()).save(any());
        verify(projectionStateDao, never()).advance(any(), any(), any(), any(Boolean.class));
    }

    @Test
    void shouldCreateNotificationAndAdvanceVersionForNewerPraise() {
        MessageQueueEvent<UserFootDO> praise = event(NotifyTypeEnum.PRAISE, 12L);
        when(projectionStateDao.lockOrCreate("PRAISE:7:14"))
                .thenReturn(state(11L, 0));
        when(notifyMsgDao.getByUserIdRelatedIdAndType(any())).thenReturn(null);
        when(projectionStateDao.advance(1L, 11L, 12L, true)).thenReturn(true);

        service.saveArticleNotify(praise);

        verify(notifyMsgDao).save(any(NotifyMsgDO.class));
        verify(projectionStateDao).advance(1L, 11L, 12L, true);
    }

    @Test
    void shouldRemoveNotificationAndKeepCancellationVersion() {
        MessageQueueEvent<UserFootDO> cancel = event(NotifyTypeEnum.CANCEL_PRAISE, 13L);
        when(projectionStateDao.lockOrCreate("PRAISE:7:14"))
                .thenReturn(state(12L, 1));
        NotifyMsgDO existing = new NotifyMsgDO();
        existing.setId(99L);
        when(notifyMsgDao.getByUserIdRelatedIdAndType(any())).thenReturn(existing);
        when(projectionStateDao.advance(1L, 12L, 13L, false)).thenReturn(true);

        service.removeArticleNotify(cancel);

        verify(notifyMsgDao).removeById(99L);
        verify(projectionStateDao).advance(1L, 12L, 13L, false);
    }

    private MessageQueueEvent<UserFootDO> event(NotifyTypeEnum type, long version) {
        UserFootDO foot = new UserFootDO();
        foot.setUserId(7L);
        foot.setDocumentId(14L);
        foot.setDocumentUserId(4L);
        foot.setFavorVersion(version);
        return new MessageQueueEvent<>(type, foot, 7L);
    }

    private NotifyProjectionStateDO state(long version, int desiredState) {
        NotifyProjectionStateDO state = new NotifyProjectionStateDO();
        state.setId(1L);
        state.setAggregateKey("PRAISE:7:14");
        state.setBusinessVersion(version);
        state.setDesiredState(desiredState);
        return state;
    }
}
