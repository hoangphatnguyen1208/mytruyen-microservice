CREATE TABLE catalog_outbox (
    event_id UUID PRIMARY KEY,
    aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    aggregate_version BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    schema_version INTEGER NOT NULL DEFAULT 1,
    correlation_id UUID NOT NULL,
    payload TEXT NOT NULL,
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_catalog_outbox_version UNIQUE (aggregate_type, aggregate_id, aggregate_version)
);
CREATE INDEX ix_catalog_outbox_pending ON catalog_outbox(published_at, occurred_at, event_id);
