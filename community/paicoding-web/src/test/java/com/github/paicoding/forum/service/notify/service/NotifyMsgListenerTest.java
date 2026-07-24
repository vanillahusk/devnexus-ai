package com.github.paicoding.forum.service.notify.service;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.service.comment.repository.entity.CommentDO;
import com.github.paicoding.forum.service.notify.facade.NotifyCommandFacade;
import com.github.paicoding.forum.service.notify.service.impl.NotifyMsgListener;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class NotifyMsgListenerTest {
    private final NotifyCommandFacade facade = mock(NotifyCommandFacade.class);
    private final NotifyMsgListener listener = new NotifyMsgListener(facade);

    @Test
    void shouldLeaveCommentNotificationToOutboxConsumer() {
        listener.onApplicationEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.COMMENT, new CommentDO()));
        listener.onApplicationEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.REPLY, new CommentDO()));

        verifyNoInteractions(facade);
    }

    @Test
    void shouldKeepNonCommentLocalEventCompatibility() {
        listener.onApplicationEvent(new NotifyMsgEvent<>(this, NotifyTypeEnum.PRAISE, new UserFootDO()));

        verify(facade).saveArticleNotify(any());
    }
}
