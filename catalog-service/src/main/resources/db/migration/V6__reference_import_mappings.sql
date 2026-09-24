CREATE TABLE reference_import_mappings (
    id UUID PRIMARY KEY,
    source VARCHAR(50) NOT NULL REFERENCES import_sources(code),
    kind VARCHAR(20) NOT NULL,
    external_id VARCHAR(150) NOT NULL,
    author_id UUID REFERENCES authors(id),
    genre_id BIGINT REFERENCES genres(id),
    tag_id BIGINT REFERENCES tags(id),
    status_id BIGINT REFERENCES book_statuses(id),
    payload TEXT NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_reference_import_external UNIQUE(source,kind,external_id),
    CONSTRAINT uq_reference_import_author UNIQUE(author_id),
    CONSTRAINT uq_reference_import_genre UNIQUE(genre_id),
    CONSTRAINT uq_reference_import_tag UNIQUE(tag_id),
    CONSTRAINT uq_reference_import_status UNIQUE(status_id),
    CONSTRAINT ck_reference_import_target CHECK (
        (kind='authors' AND author_id IS NOT NULL AND genre_id IS NULL AND tag_id IS NULL AND status_id IS NULL) OR
        (kind='genres' AND author_id IS NULL AND genre_id IS NOT NULL AND tag_id IS NULL AND status_id IS NULL) OR
        (kind='tags' AND author_id IS NULL AND genre_id IS NULL AND tag_id IS NOT NULL AND status_id IS NULL) OR
        (kind='book-statuses' AND author_id IS NULL AND genre_id IS NULL AND tag_id IS NULL AND status_id IS NOT NULL)
    )
);
