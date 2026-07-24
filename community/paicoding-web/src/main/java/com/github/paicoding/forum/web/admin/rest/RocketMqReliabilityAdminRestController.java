package com.github.paicoding.forum.web.admin.rest;

import com.github.paicoding.forum.api.model.context.ReqInfoContext;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.web.mq.RocketMqDeadLetterReplayService;
import com.github.paicoding.forum.service.notify.service.MqOutboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("api/admin/rocketmq/reliability")
@Permission(role = UserRole.ADMIN)
public class RocketMqReliabilityAdminRestController {
    private final RocketMqDeadLetterReplayService replayService;
    private final MqOutboxService outboxService;

    @PostMapping("dlq/replay-corrected")
    public ResVo<RocketMqDeadLetterReplayService.ReplayResult> replayCorrected(
            @RequestBody RocketMqDeadLetterReplayService.ReplayRequest request) {
        return ResVo.ok(replayService.replayCorrected(request, ReqInfoContext.getReqInfo().getUserId()));
    }

    @PostMapping("dlq/article-knowledge/replay-corrected")
    public ResVo<RocketMqDeadLetterReplayService.ReplayResult> replayCorrectedArticleKnowledge(
            @RequestBody RocketMqDeadLetterReplayService.ArticleKnowledgeReplayRequest request) {
        return ResVo.ok(replayService.replayCorrectedArticleKnowledge(
                request, ReqInfoContext.getReqInfo().getUserId()));
    }

    @GetMapping("outbox/status")
    public ResVo<MqOutboxService.OutboxStatus> outboxStatus() {
        return ResVo.ok(outboxService.status());
    }

    @GetMapping("outbox/abnormal")
    public ResVo<java.util.List<MqOutboxService.OutboxAbnormalEvent>> abnormalOutboxEvents(
            @RequestParam(defaultValue = "20") int limit) {
        return ResVo.ok(outboxService.abnormalEvents(limit));
    }

    @PostMapping("outbox/dead/{id}/replay")
    public ResVo<Boolean> replayDeadOutboxEvent(@PathVariable Long id) {
        return ResVo.ok(outboxService.replayDead(id));
    }
}
