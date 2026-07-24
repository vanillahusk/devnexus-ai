package com.github.paicoding.forum.web.admin.rest;

import com.github.paicoding.forum.api.model.enums.CommentAuditStatusEnum;
import com.github.paicoding.forum.api.model.vo.ResVo;
import com.github.paicoding.forum.api.model.vo.constants.StatusEnum;
import com.github.paicoding.forum.core.permission.Permission;
import com.github.paicoding.forum.core.permission.UserRole;
import com.github.paicoding.forum.service.comment.service.CommentWriteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Permission(role = UserRole.ADMIN)
@RequestMapping(path = {"/api/admin/comment/audit/", "/admin/comment/audit/"})
public class CommentAuditAdminRestController {
    private final CommentWriteService commentWriteService;

    @PostMapping("review")
    public ResVo<Boolean> review(@RequestParam Long commentId, @RequestParam Integer status) {
        CommentAuditStatusEnum target = CommentAuditStatusEnum.fromCode(status);
        if (target != CommentAuditStatusEnum.APPROVED && target != CommentAuditStatusEnum.REJECTED) {
            return ResVo.fail(StatusEnum.ILLEGAL_ARGUMENTS_MIXED, "审核状态仅支持1(通过)或2(拒绝)");
        }
        commentWriteService.reviewComment(commentId, target);
        return ResVo.ok(true);
    }
}
