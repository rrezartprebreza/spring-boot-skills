-- PostgreSQL. Keep claim, business write and stored response in ONE transaction.
CREATE TABLE request_results (
    tenant_id UUID NOT NULL,
    operation VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(200) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_status INTEGER,
    response_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (tenant_id, operation, idempotency_key),
    CHECK (response_status IS NULL OR response_status BETWEEN 200 AND 599)
);

-- JDBC named-parameter statement, not a Flyway migration:
-- INSERT INTO request_results (tenant_id, operation, idempotency_key, request_hash)
-- VALUES (:tenant, :operation, :key, :hash)
-- ON CONFLICT (tenant_id, operation, idempotency_key) DO NOTHING
-- RETURNING idempotency_key;
-- A claimed row must be completed with the business result before committing.
-- No returned row: read existing hash/result in a NEW statement, then compare/replay.
