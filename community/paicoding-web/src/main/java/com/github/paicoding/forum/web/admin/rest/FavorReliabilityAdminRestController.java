package com.github.paicoding.forum.web.admin.rest;

import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.user.service.favor.FavorAsyncWriteService;
import com.github.paicoding.forum.service.user.service.favor.FavorReconciliationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 点赞异步链路的运行态检查和人工补偿入口。
 *
 * <p>接口只对管理员开放，避免普通用户触发死信重放。</p>
 */
@RestController
@RequestMapping("api/admin/favor/reliability")
@Permission(role = UserRole.ADMIN)
public class FavorReliabilityAdminRestController {

    private final FavorAsyncWriteService favorAsyncWriteService;
    private final FavorReconciliationService reconciliationService;

    public FavorReliabilityAdminRestController(FavorAsyncWriteService favorAsyncWriteService,
                                               FavorReconciliationService reconciliationService) {
        this.favorAsyncWriteService = favorAsyncWriteService;
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("status")
    public ResVo<FavorAsyncWriteService.FavorQueueStatus> status() {
        return ResVo.ok(favorAsyncWriteService.queueStatus());
    }

    @PostMapping("persist-dead/replay-one")
    public ResVo<Boolean> replayOnePersistDeadEvent() {
        return ResVo.ok(favorAsyncWriteService.replayPersistDeadEvent());
    }

    @PostMapping("notify-dead/replay-one")
    public ResVo<Boolean> replayOneNotifyDeadEvent() {
        return ResVo.ok(favorAsyncWriteService.replayNotifyDeadEvent());
    }

    @GetMapping("reconcile/{articleId}")
    public ResVo<FavorReconciliationService.ReconciliationResult> inspect(@PathVariable Long articleId) {
        return ResVo.ok(reconciliationService.inspect(articleId));
    }

    @PostMapping("reconcile/{articleId}/repair")
    public ResVo<FavorReconciliationService.ReconciliationResult> repair(@PathVariable Long articleId) {
        return ResVo.ok(reconciliationService.repair(articleId));
    }
}
