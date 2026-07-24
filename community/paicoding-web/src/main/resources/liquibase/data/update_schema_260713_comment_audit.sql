ALTER TABLE `comment`
    ADD COLUMN `audit_status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '审核状态：0待审核，1已通过，2已拒绝' AFTER `parent_comment_id`,
    ADD INDEX `idx_comment_visible_tree` (`article_id`, `audit_status`, `deleted`, `top_comment_id`, `id`);
