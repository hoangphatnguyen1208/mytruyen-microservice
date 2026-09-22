# Phase 03a — Catalog persistence

Implemented the database foundation of Catalog using Spring Data JPA/Hibernate and Flyway V2. Scope: authors, statuses, genres, tags, books, chapter metadata/content and local statistics/projection tables; repositories separate public visibility queries from unrestricted administration reads.

Public queries exclude unpublished/deleted parents and chapters. Flyway defines uniqueness, non-negative counters, positive chapter indexes and local FK deletion behavior. Hibernate validates schema, uses optimistic versions on core entities and keeps OSIV disabled.

No Catalog CRUD endpoints, JWT policy, import operation, publish workflow, outbox relay or Engagement consumer have been implemented yet. This is intentionally one small stage, not completion of the full Catalog migration. No production data has been changed.

Next: authenticated taxonomy and book CRUD, chapter/content workflows with transactional stats, versioned outbox writes and HTTP compatibility tests. Then rehearse ID-preserving import and PostgreSQL/Docker deployment.
