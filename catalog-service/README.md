# Catalog service — persistence foundation

This stage provides Flyway schema, JPA entities/repositories and persistence tests. It does not yet expose domain HTTP endpoints. Catalog API authorization and publish transactions are the next stage.

## Schema ownership

Flyway V1 remains unchanged. V2 creates:

- `authors`, `book_statuses`, `genres`, `tags`.
- `books`, `book_genres`, `book_tags`.
- `chapters`, `chapter_contents`.
- `book_content_stats`, `book_engagement_projection`.

Book, chapter and taxonomy IDs are BIGINT identity columns. Author and external creator IDs are UUID. `creator_id` has no cross-service foreign key; Catalog never queries Identity's database. Author names are not unique because different authors can share a pen name. Status/genre/tag slugs and book slugs are unique.

`chapter_contents` uses chapter_id as its primary key and `@MapsId` for the one-to-one relation. Its TEXT content is not mapped back as an eager relation on Chapter, so chapter-list queries cannot accidentally retrieve content. Poster data retains JSONB compatibility with the monolith.

Book, author, chapter and content use `@Version`. Shared taxonomy is not cascade-deleted by JPA. Physical book deletion cascades only its owned database rows; deleting taxonomy still used by a book fails. Soft deletion is an explicit deleted_at field and repository predicate, not an implicit ORM filter that hides rows from administration.

`book_content_stats` stores published chapter/word counts, latest chapter index and last publication time. `book_engagement_projection` is a future local read model owned upstream by Engagement. Repositories currently only store these rows; automatic maintenance is not implemented in this persistence stage. Later publish commands must lock the parent book and update chapter/content/stats/outbox in one transaction. Projection consumers must compare source_version before applying snapshots.

Compared with DATABASE_DESIGN.md, public-read indexes use full composite indexes instead of PostgreSQL partial indexes in V2. This keeps the same migration executable in the lightweight H2 test environment; tune to partial indexes in a later PostgreSQL migration after measuring production queries. Outbox/inbox and external-source mapping tables are deferred until their workflows are implemented.

## Repository contract

- `findPublic*` checks published and deleted_at on both book and chapter; callers should use these methods for anonymous reads.
- Ordinary `findById`/admin methods intentionally include drafts; they must never be wired directly to public controllers.
- `lockById` requires an application transaction. Always lock book before chapter during writes to keep a consistent lock order.
- `summarizePublished` calculates chapter counts/word counts/latest index for a book and handles an empty set; it does not update the stats table.
- Map entities to DTOs inside service transactions. Open Session in View is disabled. Collections use batch fetching; page queries fetch only singular author/status associations.

Publishing without content is still possible at the storage level. The next application-service stage must enforce publish invariants; do not expose generic repository writes as APIs. No published API or user-data import is included in this stage.

## Build and verification

Use Java 17 and run `gradlew.bat test bootJar`. Tests execute the same Flyway V1/V2 migrations on H2 PostgreSQL mode, then Hibernate schema validation. They cover JSON round-trip, relationships, constraints, visibility, deletion behavior, summary calculations and stale writes. Docker/PostgreSQL verification remains a deployment gate; H2 does not prove all PostgreSQL locking/planner behavior.

For runtime use, configure DB_URL, DB_USERNAME, DB_PASSWORD and RabbitMQ settings from the root Compose. `ddl-auto=validate` is required; do not enable create/update. Existing monolith data requires a separate ID-preserving import and sequence reset, not automatic startup import.
