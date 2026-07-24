CREATE TABLE IF NOT EXISTS `article_knowledge_index_state` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `article_id` bigint NOT NULL,
  `article_version` bigint NOT NULL,
  `operation` varchar(16) NOT NULL,
  `event_id` varchar(64) NOT NULL,
  `idempotency_key` varchar(128) NOT NULL,
  `synced_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_article_id` (`article_id`),
  UNIQUE KEY `uk_idempotency_key` (`idempotency_key`),
  KEY `idx_version` (`article_version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
