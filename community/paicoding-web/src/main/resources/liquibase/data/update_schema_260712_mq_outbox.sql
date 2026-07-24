CREATE TABLE IF NOT EXISTS `mq_outbox_event` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `event_id` varchar(64) NOT NULL,
  `topic` varchar(128) NOT NULL,
  `tag` varchar(64) NOT NULL,
  `aggregate_id` varchar(128) NOT NULL,
  `payload` text NOT NULL,
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0 pending,1 sending,2 retry,3 sent,4 dead',
  `retry_count` int NOT NULL DEFAULT 0,
  `next_retry_time` timestamp NULL DEFAULT NULL,
  `last_error` varchar(512) NOT NULL DEFAULT '',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_event_id` (`event_id`),
  KEY `idx_dispatch` (`status`, `next_retry_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
