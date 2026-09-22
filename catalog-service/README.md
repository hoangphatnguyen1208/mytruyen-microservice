# Catalog service — JPA and catalog APIs

This stage provides Flyway schema, JPA repositories, JWT authorization and taxonomy/book HTTP APIs. Chapter publication transactions remain the next stage.

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

Publishing a book makes its metadata visible, including a book with no chapters. Chapter publication is a separate, not-yet-exposed workflow and must require content. No user-data import is included.

## HTTP contract (phase 03b)

All paths start with `/api/v1`. Public GET endpoints never reveal draft or soft-deleted books, even when the caller is ADMIN. Writes require a signed Identity access token with `ROLE_ADMIN`; `creator_id` comes from its UUID subject, never the request or forwarded headers.

- Books: GET/POST `/books`; GET/PATCH/DELETE `/books/id/{id}` and `/books/slug/{slug}`.
- Draft administration: GET `/admin/catalog/books` and `/admin/catalog/books/id/{id}`; ADMIN only, also routed through Gateway.
- Taxonomy: GET/POST `/genres`, `/tags`, `/book-statuses`; GET each `/{slug}`. PATCH/DELETE tags/statuses by slug. Genre writes retain PATCH `/genres/update/{id}` and DELETE `/genres/delete/{id}`.
- Authors: GET/POST `/authors`, GET `/authors/id/{id}`, legacy GET `/authors/{name}`, PATCH/DELETE `/authors/{id}`. Ambiguous names return 409; prefer IDs.

Create book fields: required `name`, `slug`, `status_id`, `kind`, `sex`, `synopsis`; optional `author_id`, `poster`, `note`, `chapter_per_week`, `published`, `genre_ids`, `tag_ids`. Creation initializes local counters to zero. PATCH merges only writable fields; null clears nullable fields/collections, required null values fail validation. Unknown fields, IDs, creator and counters are rejected with 400. Missing references return 404; uniqueness/referenced deletion conflicts return 409. Deleting a book is soft deletion and retains its slug reservation. Setting published false clears published_at; republishing sets a new timestamp. Book updates lock the row; no client If-Match/version precondition is implemented yet.

Lists accept page (1-based), limit (1–100). Books additionally accept status (status ID) and sort: name, created_at (default), updated_at, published_at; descending with ID tie-breaker. Books return pagination metadata. Taxonomy/authors return a data array. Responses use status_code/success/message/data. Compatibility is partial: creator is exposed as creator_id, not a nested Identity user; latest_chapter is an integer index. Unsupported legacy sorting/filter parameters are not implemented; clients must be checked before cutover.

JWT verification requires RS256 with a minimum 2048-bit RSA key, issuer, audience, UUID subject, issued-at, expiration and a roles array. Configure JWT_PUBLIC_KEY_BASE64 (DER public key), JWT_ISSUER and JWT_AUDIENCE consistently with Identity. Verification is offline: logout/session revocation becomes effective here when access tokens expire. No Identity DB access, outbox events, search synchronization or Engagement projection consumer is included yet.

## Build and verification

Use Java 17 and run `gradlew.bat test bootJar`. Tests execute the same Flyway V1/V2 migrations on H2 PostgreSQL mode, then Hibernate schema validation. They cover JSON round-trip, relationships, constraints, visibility, deletion behavior, summary calculations and stale writes. Docker/PostgreSQL verification remains a deployment gate; H2 does not prove all PostgreSQL locking/planner behavior.

For runtime use, configure DB_URL, DB_USERNAME, DB_PASSWORD and RabbitMQ settings from the root Compose. `ddl-auto=validate` is required; do not enable create/update. Existing monolith data requires a separate ID-preserving import and sequence reset, not automatic startup import.
