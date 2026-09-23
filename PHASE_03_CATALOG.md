# Phase 03a — Catalog persistence

Implemented the database foundation of Catalog using Spring Data JPA/Hibernate and Flyway V2. Scope: authors, statuses, genres, tags, books, chapter metadata/content and local statistics/projection tables; repositories separate public visibility queries from unrestricted administration reads.

Public queries exclude unpublished/deleted parents and chapters. Flyway defines uniqueness, non-negative counters, positive chapter indexes and local FK deletion behavior. Hibernate validates schema, uses optimistic versions on core entities and keeps OSIV disabled.

## Phase 03b — taxonomy and book APIs

Added offline Identity JWT validation, ADMIN-only writes, author/taxonomy CRUD and JPA book CRUD. Public reads filter drafts/deletions; separate ADMIN read endpoints expose drafts. Book creation initializes stats/projection rows, references are validated transactionally, writes lock books, deletion is soft. Gateway forwards the new admin Catalog namespace. See catalog-service/README.md for payloads and compatibility limits.

HTTP integration tests cover permissions, invalid tokens, draft/publish/delete visibility, pagination, read-only/unknown fields, invalid-reference rollback and taxonomy foreign-key conflicts. These run against H2 PostgreSQL mode, not real PostgreSQL.

Verification: Catalog 14 tests (8 persistence + 6 HTTP) passed; Gateway 13 tests passed; Compose configuration validates with .env.example. Catalog bootJar builds successfully. A real PostgreSQL concurrency/integration run is still required before deployment.

## Phase 03c — draft chapter and content CRUD

Added chapter DTOs, mapper, transactional service, public chapter reads and ADMIN-only draft/content writes. Supports book ID and slug addressing, separate admin reads, pagination, soft deletion, server-calculated content hash/word count and parent-before-child locks. No Flyway/schema changes. Existing published chapters are read-only until the publication/statistics workflow is implemented.

Added HTTP tests for draft/content lifecycle, validation/permissions, rollback, published and deleted-parent visibility, pagination/slug routes and concurrent duplicate creation. All tests use H2 PostgreSQL mode; PostgreSQL lock behavior still requires a real-database run.

Verification for phase 03c: Catalog test + bootJar succeeded with 19 tests (11 HTTP + 8 persistence), zero failures. Gateway route/configuration was unchanged: its existing chapters and admin/catalog prefixes already cover these endpoints.

## Phase 03d — publication, statistics and transactional outbox

Added ADMIN publish/unpublish endpoints with required content and idempotent state transitions. Published metadata/content edits and soft deletion now update book statistics atomically. Removing published content requires unpublishing first. Publication under a draft parent remains hidden publicly.

V3 adds catalog_outbox; all chapter commands record versioned change notifications in the same transaction as the chapter/content and recomputed statistics. Events have no content text. Relay, book/taxonomy events, propagated HTTP correlation and consumers remain deferred; this is not yet search integration.

Tests cover publication lifecycle, combined statistics under concurrent sibling publication, failed-command rollback, explicit transaction rollback and event versions/idempotency. Test database remains H2 PostgreSQL mode, not real PostgreSQL.

Verification: Catalog test + bootJar passed with 22 tests (14 HTTP/integration + 8 persistence), zero failures. Flyway V1/V2/V3 and Hibernate schema validation run in these tests. PostgreSQL deployment/concurrency verification is still required.

No import operation, outbox relay or Engagement consumer has been implemented yet. This is intentionally a small stage, not completion of the full Catalog migration. No production data has been changed.

## Phase 03e — legacy Book/Chapter contract migration

Compared Book/Chapter controllers, schemas and queries against mytruyen-be. Restored sort=field / -field direction semantics, allowlisted book sorting across local content/engagement projections, legacy global chapter ordering and ID/slug pagination defaults. Sorting happens before database pagination with stable ID tie-breakers; invalid fields return 400.

Restored chapter PATCH published support through the same publication validation/transaction used by explicit publish/unpublish endpoints. Caller-controlled IDs/counters and public draft visibility remain deliberately restricted.

Verification: 25 Catalog tests (17 HTTP/integration + 8 persistence) and bootJar passed on H2 PostgreSQL mode. Added tests for scalar/counter/rating sorts, missing projection rows, null values, stable pages, chapter defaults and atomic legacy publication PATCH.

See [Book/Chapter parity](docs/migration/book-chapter-parity.md) for unconverted features and intentional response differences. This is not complete frontend compatibility.

## Legacy text-search read path and topboxes

Ported GET /search/meili into Search: ID-only Meilisearch query, single public Catalog batch hydration, rank preservation and filtered draft/deleted records. Added GET /books/batch and ported /books/topboxes with the original upstream JSON shape and bounded timeout/input. Disabled hybrid/audio/YouTube features remain 503; dead ML code was not copied.

Search requires an already-populated matching books index. Initial rebuild and automatic indexing are still unfinished. Estimated pagination totals retain legacy semantics and may exceed visible results when the index is stale.

Verification: Catalog 30 tests and bootJar passed; Search 28 tests passed; Compose config validated. Dependency tests use local/mock servers, not real Meilisearch/topboxes; persistence tests still use H2.

Next, feature-first: finish index rebuild/synchronization, then crawler/import. PostgreSQL, real upstream and frontend/Gateway contract checks remain deployment gates.
