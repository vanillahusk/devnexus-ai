package com.github.paicoding.forum.service.rank;

import com.github.paicoding.forum.api.model.enums.NotifyTypeEnum;
import com.github.paicoding.forum.api.model.vo.notify.NotifyMsgEvent;
import com.github.paicoding.forum.service.rank.service.UserActivityRankService;
import com.github.paicoding.forum.service.rank.service.listener.UserActivityListener;
import com.github.paicoding.forum.service.rank.service.model.ActivityScoreBo;
import com.github.paicoding.forum.service.user.repository.entity.UserFootDO;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class UserActivityListenerTest {

    @Test
    void shouldUseEventActorWhenRecoveredEventHasNoRequestContext() {
        UserActivityRankService rankService = mock(UserActivityRankService.class);
        UserActivityListener listener = new UserActivityListener();
        ReflectionTestUtils.setField(listener, "userActivityRankService", rankService);
        UserFootDO foot = new UserFootDO();
        foot.setUserId(7L);
        foot.setDocumentId(14L);

        listener.notifyMsgListener(new NotifyMsgEvent<>(this, NotifyTypeEnum.PRAISE, foot));

        verify(rankService).addActivityScore(org.mockito.ArgumentMatchers.eq(7L), any(ActivityScoreBo.class));
    }
}
