# Phase 03a — Catalog persistence

Implemented the database foundation of Catalog using Spring Data JPA/Hibernate and Flyway V2. Scope: authors, statuses, genres, tags, books, chapter metadata/content and local statistics/projection tables; repositories separate public visibility queries from unrestricted administration reads.

Public queries exclude unpublished/deleted parents and chapters. Flyway defines uniqueness, non-negative counters, positive chapter indexes and local FK deletion behavior. Hibernate validates schema, uses optimistic versions on core entities and keeps OSIV disabled.

## Phase 03b — taxonomy and book APIs

Added offline Identity JWT validation, ADMIN-only writes, author/taxonomy CRUD and JPA book CRUD. Public reads filter drafts/deletions; separate ADMIN read endpoints expose drafts. Book creation initializes stats/projection rows, references are validated transactionally, writes lock books, deletion is soft. Gateway forwards the new admin Catalog namespace. See catalog-service/README.md for payloads and compatibility limits.

HTTP integration tests cover permissions, invalid tokens, draft/publish/delete visibility, pagination, read-only/unknown fields, invalid-reference rollback and taxonomy foreign-key conflicts. These run against H2 PostgreSQL mode, not real PostgreSQL.

Verification: Catalog 14 tests (8 persistence + 6 HTTP) passed; Gateway 13 tests passed; Compose configuration validates with .env.example. Catalog bootJar builds successfully. A real PostgreSQL concurrency/integration run is still required before deployment.

No import operation, chapter publish workflow, outbox relay or Engagement consumer has been implemented yet. This is intentionally a small stage, not completion of the full Catalog migration. No production data has been changed.

Next: chapter/content workflows with transactional stats and versioned outbox writes. Then add relay/consumers and rehearse ID-preserving import and PostgreSQL/Docker deployment.
