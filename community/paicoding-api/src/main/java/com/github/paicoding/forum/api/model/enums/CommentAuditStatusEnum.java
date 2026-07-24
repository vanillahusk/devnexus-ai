package com.github.paicoding.forum.api.model.enums;

import lombok.Getter;

import java.util.Arrays;

/**
 * 评论审核状态。
 */
@Getter
public enum CommentAuditStatusEnum {
    PENDING(0, "待审核"),
    APPROVED(1, "已通过"),
    REJECTED(2, "已拒绝");

    private final int code;
    private final String desc;

    CommentAuditStatusEnum(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public static CommentAuditStatusEnum fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        return Arrays.stream(values())
                .filter(status -> status.code == code)
                .findFirst()
                .orElse(null);
    }
}
