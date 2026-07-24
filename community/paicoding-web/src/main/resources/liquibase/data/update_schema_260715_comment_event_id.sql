ALTER TABLE `comment`
    ADD COLUMN `source_event_id` VARCHAR(128) NULL COMMENT '异步评论写入事件ID，用于数据库级幂等' AFTER `audit_status`;

CREATE UNIQUE INDEX `uk_comment_source_event_id` ON `comment` (`source_event_id`);
