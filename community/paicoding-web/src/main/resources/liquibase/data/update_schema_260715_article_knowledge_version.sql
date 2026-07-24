ALTER TABLE `article`
    ADD COLUMN `knowledge_version` bigint NOT NULL DEFAULT 0 COMMENT '知识索引单调版本' AFTER `status`;
