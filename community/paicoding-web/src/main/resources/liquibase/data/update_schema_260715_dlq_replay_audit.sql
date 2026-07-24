CREATE TABLE `mq_dlq_replay_audit` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `original_msg_id` varchar(128) NOT NULL COMMENT '原 RocketMQ 消息 ID',
  `original_event_id` varchar(128) NOT NULL COMMENT '原事件 ID',
  `correction_event_id` varchar(128) NOT NULL COMMENT '修正事件 ID',
  `topic` varchar(128) NOT NULL COMMENT '目标 Topic',
  `tag` varchar(128) NOT NULL COMMENT '目标 Tag',
  `business_key` varchar(256) DEFAULT NULL COMMENT '非敏感业务定位键',
  `reason` varchar(512) NOT NULL COMMENT '修正原因',
  `operator_id` bigint DEFAULT NULL COMMENT '管理员用户 ID',
  `status` varchar(16) NOT NULL COMMENT 'CREATED/SUBMITTED/FAILED',
  `error_summary` varchar(512) DEFAULT NULL COMMENT '脱敏错误摘要',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dlq_replay_correction_event` (`correction_event_id`),
  KEY `idx_dlq_replay_original_event` (`original_event_id`),
  KEY `idx_dlq_replay_status_time` (`status`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='RocketMQ DLQ 人工修正重放审计';
