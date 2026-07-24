ALTER TABLE `user_foot`
    ADD COLUMN `favor_version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '点赞操作单调版本，用于拒绝乱序事件' AFTER `praise_stat`;
