CREATE TABLE `notify_projection_state` (
    `id` BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `aggregate_key` VARCHAR(160) NOT NULL COMMENT '通知投影聚合键',
    `business_version` BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '最后处理的业务版本',
    `desired_state` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '期望通知状态: 0不存在, 1存在',
    `create_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_notify_projection_aggregate` (`aggregate_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='版本化通知投影状态';
