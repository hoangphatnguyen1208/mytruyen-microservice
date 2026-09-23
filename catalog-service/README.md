# Catalog service — JPA and catalog APIs

This stage provides Flyway schema, JPA repositories, JWT authorization, taxonomy/book APIs, chapter/content CRUD and transactional chapter publication with statistics and outbox writes.

## Schema ownership

Flyway V1 remains unchanged. V2 creates:

- `authors`, `book_statuses`, `genres`, `tags`.
- `books`, `book_genres`, `book_tags`.
- `chapters`, `chapter_contents`.
- `book_content_stats`, `book_engagement_projection`.

Book, chapter and taxonomy IDs are BIGINT identity columns. Author and external creator IDs are UUID. `creator_id` has no cross-service foreign key; Catalog never queries Identity's database. Author names are not unique because different authors can share a pen name. Status/genre/tag slugs and book slugs are unique.

`chapter_contents` uses chapter_id as its primary key and `@MapsId` for the one-to-one relation. Its TEXT content is not mapped back as an eager relation on Chapter, so chapter-list queries cannot accidentally retrieve content. Poster data retains JSONB compatibility with the monolith.

Book, author, chapter and content use `@Version`. Shared taxonomy is not cascade-deleted by JPA. Physical book deletion cascades only its owned database rows; deleting taxonomy still used by a book fails. Soft deletion is an explicit deleted_at field and repository predicate, not an implicit ORM filter that hides rows from administration.

`book_content_stats` stores published chapter/word counts, latest chapter index and last publication time. `book_engagement_projection` is a future local read model owned upstream by Engagement. Chapter commands lock the parent book and maintain chapter/content/stats/outbox in one transaction. Statistics count published, non-deleted chapters even while the parent book is a draft; public endpoints still hide the draft parent. Projection consumers must compare source_version before applying snapshots.

Compared with DATABASE_DESIGN.md, public-read indexes use full composite indexes instead of PostgreSQL partial indexes in V2. This keeps the same migration executable in the lightweight H2 test environment; tune to partial indexes in a later PostgreSQL migration after measuring production queries. V3 adds catalog_outbox for chapter events. Inbox and external-source mapping tables remain deferred.

## Repository contract

- `findPublic*` checks published and deleted_at on both book and chapter; callers should use these methods for anonymous reads.
- Ordinary `findById`/admin methods intentionally include drafts; they must never be wired directly to public controllers.
- `lockById` requires an application transaction. Always lock book before chapter during writes to keep a consistent lock order.
- `summarizePublished` calculates chapter counts/word counts/latest index for a book and handles an empty set; it does not update the stats table.
- Map entities to DTOs inside service transactions. Open Session in View is disabled. Collections use batch fetching; page queries fetch only singular author/status associations.

Publishing a book makes its metadata visible, including a book with no chapters. Chapter publication is separate and requires nonblank content. No user-data import is included.

## HTTP contract (phase 03b)

All paths start with `/api/v1`. Public GET endpoints never reveal draft or soft-deleted books, even when the caller is ADMIN. Writes require a signed Identity access token with `ROLE_ADMIN`; `creator_id` comes from its UUID subject, never the request or forwarded headers.

- Books: GET/POST `/books`; GET/PATCH/DELETE `/books/id/{id}` and `/books/slug/{slug}`.
- Draft administration: GET `/admin/catalog/books` and `/admin/catalog/books/id/{id}`; ADMIN only, also routed through Gateway.
- Taxonomy: GET/POST `/genres`, `/tags`, `/book-statuses`; GET each `/{slug}`. PATCH/DELETE tags/statuses by slug. Genre writes retain PATCH `/genres/update/{id}` and DELETE `/genres/delete/{id}`.
- Authors: GET/POST `/authors`, GET `/authors/id/{id}`, legacy GET `/authors/{name}`, PATCH/DELETE `/authors/{id}`. Ambiguous names return 409; prefer IDs.

Create book fields: required `name`, `slug`, `status_id`, `kind`, `sex`, `synopsis`; optional `author_id`, `poster`, `note`, `chapter_per_week`, `published`, `genre_ids`, `tag_ids`. Creation initializes local counters to zero. PATCH merges only writable fields; null clears nullable fields/collections, required null values fail validation. Unknown fields, IDs, creator and counters are rejected with 400. Missing references return 404; uniqueness/referenced deletion conflicts return 409. Deleting a book is soft deletion and retains its slug reservation. Setting published false clears published_at; republishing sets a new timestamp. Book updates lock the row; no client If-Match/version precondition is implemented yet.

Lists accept page (1-based), limit (1–100). Books additionally accept status (status ID) and allowlisted sort fields: scalar metadata, chapter/word counters, new_chap_at, latest_chapter and local engagement counters/rating. Unprefixed fields ascend; '-' descends; default is -created_at. ID is the stable tie-breaker; nullable book sort values are last. Books return pagination metadata. Taxonomy/authors return a data array. Responses use status_code/success/message/data. Compatibility is partial: creator is exposed as creator_id, not a nested Identity user; latest_chapter is an integer index. See ../docs/migration/book-chapter-parity.md for supported sorting and remaining contract differences; clients must be checked before cutover.

JWT verification requires RS256 with a minimum 2048-bit RSA key, issuer, audience, UUID subject, issued-at, expiration and a roles array. Configure JWT_PUBLIC_KEY_BASE64 (DER public key), JWT_ISSUER and JWT_AUDIENCE consistently with Identity. Verification is offline: logout/session revocation becomes effective here when access tokens expire. No Identity DB access, outbox delivery, search synchronization or Engagement projection consumer is included yet.

## Chapter/content APIs (phase 03c)

Under `/api/v1`, public reads require both chapter and parent book to be published and not deleted. Drafts remain hidden even when an ADMIN uses a public URL. ADMIN reads use the same suffixes under `/admin/catalog/chapters` instead of `/chapters`.

- GET `/chapters`: all public chapter metadata, paginated.
- GET `/chapters/id/{book_id}` or `/chapters/slug/{book_slug}`: chapter metadata for one book.
- GET either path with `/{index}`: one chapter. Lists do not load chapter content.
- POST `/chapters/id/{book_id}` or `/chapters/slug/{book_slug}`: create a draft with `index` (positive integer) and `name` (nonblank, maximum 500 characters). Optional `published` may only be false/null; true is rejected.
- PATCH `/chapters/id/{chapter_id}`: partial update of index/name/published. IDs, book_id, creator_id, word_count, timestamps and version are not writable.
- DELETE `/chapters/id/{chapter_id}`: soft-delete a chapter, clear its publication state and update published counters. The legacy DELETE `/chapters/slug/{chapter_id}` alias still takes a chapter ID, not a slug. Deleted chapter indexes remain reserved; no restore API yet.
- GET/POST/PATCH/DELETE `/chapters/content/id/{book_id}/{index}` or `/chapters/content/slug/{book_slug}/{index}`: read/create/update/delete content. POST/PATCH accept only `content` (nonblank, maximum 1,000,000 characters). POST conflicts when content already exists; PATCH requires existing content. Deleting content physically removes only that draft's content row and resets its word count to zero; chapter metadata remains.

All writes require ADMIN. New chapter creator_id is the JWT subject. Every write locks parent book before chapter, then updates metadata/content in one JPA transaction. SHA-256 is calculated over the exact UTF-8 content. Word count means Unicode-whitespace-delimited tokens, not linguistic words or HTML-aware counting; clients should submit plain text. Draft content edits advance chapter version but do not alter published book statistics. Deleting a chapter retains its content for later recovery workflows, but hides it from every endpoint.

## Publication and outbox (phase 03d)

- POST `/api/v1/chapters/id/{chapter_id}/publish`: ADMIN only; requires nonblank stored content, recalculates word count/hash and sets published_at. Publication under a draft parent is allowed, but remains hidden publicly until the book is published.
- POST `/api/v1/chapters/id/{chapter_id}/unpublish`: ADMIN only; clears published_at. Repeating either operation in the same state preserves version/timestamp and creates no extra event.
- PATCH chapter metadata or content also works while published; recalculates counters atomically. PATCH also accepts published for legacy compatibility and invokes the same content/publication invariants atomically with metadata changes; explicit endpoints remain available.
- Deleting a published chapter removes it from counters and retains its hidden content. Deleting only its content returns 409 until explicitly unpublished.

Every successful chapter mutation records an outbox event after flushing the chapter's JPA version. The parent lock serializes sibling writes; published chapter count, sum of words, maximum chapter index and most recent publication time are recomputed from chapters. Empty aggregates use 0/0/null/null. This favors correctness over incremental counter complexity; measure aggregate-query cost before optimizing large books. Editing content/name does not change publication time; republishing starts a new publication time.

Flyway V3 creates `catalog_outbox`: event_id, aggregate_type=Chapter, aggregate_id, aggregate_version, event_type, schema_version, correlation_id, payload, occurred_at and published_at. Unique aggregate type/ID/version prevents duplicate records for the same chapter version. Payloads contain only book_id, chapter_id, index, published and deleted, not chapter text. These are change notifications, not complete search snapshots or ordered book-stat snapshots. Correlation IDs are generated per event for now, not propagated from HTTP. No cross-service FK.

Events cover ChapterCreated/Updated/Deleted/Published/Unpublished and ChapterContentCreated/Updated/Deleted. No RabbitMQ publishing runs in the transaction. Book/taxonomy events, shared HTTP correlation, relay retries/confirms, consumers, replay and initial snapshot remain the next event-integration stage. Do not treat this as end-to-end search synchronization. No restore workflow yet.

Lists use page >= 1, limit 1–100 and allowlisted metadata sorts with ASC for unprefixed fields and DESC for '-'. Default global ordering is book_id/index/id; per-book ordering is index/id. Default limit is 10 for global/slug lists and 30 for ID-based lists. Missing/hidden parents return 404. Responses use the existing envelope; create now returns chapter metadata rather than null. Content responses identify chapter_id, not a separate legacy content ID. Chapter view/comment counters are omitted until Engagement is implemented. These differences require frontend contract checks before cutover.

## Build and verification commands

Use Java 17 and run `gradlew.bat test bootJar`. Tests execute the same Flyway V1/V2/V3 migrations on H2 PostgreSQL mode, then Hibernate schema validation. They cover JSON round-trip, relationships, constraints, visibility, deletion behavior, summary calculations and stale writes. Docker/PostgreSQL verification remains a deployment gate; H2 does not prove all PostgreSQL locking/planner behavior.

For runtime use, configure DB_URL, DB_USERNAME, DB_PASSWORD and RabbitMQ settings from the root Compose. `ddl-auto=validate` is required; do not enable create/update. Existing monolith data requires a separate ID-preserving import and sequence reset, not automatic startup import.
