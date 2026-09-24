CREATE TABLE import_sources (
    code VARCHAR(50) PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT TRUE
);
INSERT INTO import_sources(code) VALUES ('metruyencv');

CREATE TABLE book_import_mappings (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL REFERENCES import_sources(code),
    external_id VARCHAR(150) NOT NULL,
    book_id BIGINT NOT NULL REFERENCES books(id),
    last_book_version BIGINT NOT NULL,
    last_payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_book_import_external UNIQUE (source, external_id),
    CONSTRAINT uq_book_import_owner UNIQUE (book_id)
);
