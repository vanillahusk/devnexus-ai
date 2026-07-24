-- Existing PostgreSQL/pgvector databases: enable two-Generation coexistence.
-- Run in a maintenance window before setting rag.index-generation.enabled=true.
BEGIN;

ALTER TABLE t_knowledge_vector
    ADD COLUMN IF NOT EXISTS collection_name VARCHAR(128);

UPDATE t_knowledge_vector
SET collection_name = COALESCE(NULLIF(metadata->>'collection_name', ''), 'default')
WHERE collection_name IS NULL;

ALTER TABLE t_knowledge_vector
    ALTER COLUMN collection_name SET NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 't_knowledge_vector'::regclass
          AND conname = 't_knowledge_vector_pkey'
          AND pg_get_constraintdef(oid) = 'PRIMARY KEY (id)'
    ) THEN
        ALTER TABLE t_knowledge_vector DROP CONSTRAINT t_knowledge_vector_pkey;
    END IF;
END $$;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conrelid = 't_knowledge_vector'::regclass
          AND contype = 'p'
    ) THEN
        ALTER TABLE t_knowledge_vector
            ADD CONSTRAINT t_knowledge_vector_pkey PRIMARY KEY (collection_name, id);
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_kv_collection
    ON t_knowledge_vector (collection_name);

CREATE TABLE IF NOT EXISTS t_index_generation (
    logical_collection   VARCHAR(128) PRIMARY KEY,
    active_generation    VARCHAR(128) NOT NULL,
    building_generation  VARCHAR(128),
    previous_generation  VARCHAR(128),
    status               VARCHAR(16) NOT NULL,
    start_watermark      BIGINT NOT NULL DEFAULT 0,
    applied_watermark    BIGINT NOT NULL DEFAULT 0,
    target_watermark     BIGINT NOT NULL DEFAULT 0,
    reconciled           BOOLEAN NOT NULL DEFAULT FALSE,
    rebuild_started_at   TIMESTAMP,
    switched_at          TIMESTAMP,
    updated_at           TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_index_generation_status
        CHECK (status IN ('ACTIVE', 'BUILDING', 'READY', 'FAILED'))
);

CREATE TABLE IF NOT EXISTS t_vector_document_identity (
    logical_collection VARCHAR(128) NOT NULL,
    doc_id             VARCHAR(64) NOT NULL,
    source_type        VARCHAR(32),
    business_id        VARCHAR(64),
    business_version   BIGINT,
    updated_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (logical_collection, doc_id)
);
CREATE INDEX IF NOT EXISTS idx_vector_document_business
    ON t_vector_document_identity (logical_collection, source_type, business_id, business_version);

COMMIT;
