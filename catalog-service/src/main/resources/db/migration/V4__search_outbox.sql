-- Invalidation jobs, separate from versioned Chapter domain events.
-- No FK: deletion must not erase an undelivered search removal.
CREATE TABLE search_outbox (
    event_id UUID PRIMARY KEY,
    book_id BIGINT NOT NULL CHECK (book_id > 0),
    occurred_at TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at TIMESTAMP WITH TIME ZONE
);
CREATE INDEX ix_search_outbox_pending ON search_outbox (published_at, occurred_at, event_id);
