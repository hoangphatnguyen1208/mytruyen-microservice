# Preserve worker logic; adapt backend contracts

## Decision

Keep the original Go crawl/import workflow. Changing endpoint paths/base URL is
allowed, but the worker must not be required to adopt the experimental import
job's draft-only policy, optimistic versions or reference-ID mapping protocol.
Any internal ID translation belongs in the backend compatibility adapter.

Keep unchanged:
- Existing source endpoints, book enumeration and scheduled latest-book checks.
- Existing author lookup/name handling and creator fallback.
- Existing book create/update branching and source-counter payloads.
- Existing chapter binary-search discovery and source `word_count`/`published`.
- Existing HTTP-triggered task chaining and RabbitMQ `{type, book_id}` messages.

Timeouts, token refresh and HTTP error detection already added are technical
fixes and remain. This does not claim legacy binary search detects chapter gaps,
or that immediate requeue is a bounded retry policy; those limitations remain.

## Implemented: task API compatibility

Gateway forwards these authenticated POST routes to Catalog:

| Route | Request body | Queue message type |
|---|---|---|
| `/api/v1/rabbitmq/genres` | none | `crawl_genres` |
| `/api/v1/rabbitmq/tags` | none | `crawl_tags` |
| `/api/v1/rabbitmq/book-statuses` | none | `crawl_book_statuses` |
| `/api/v1/rabbitmq/all-books` | none | `crawl_all_books` |
| `/api/v1/rabbitmq/book` | `{"book_id":123}` | `crawl_book` |
| `/api/v1/rabbitmq/chapters` | `{"book_id":123}` | `crawl_chapters` |

Book IDs in messages remain source/legacy-visible IDs, not new Catalog IDs. No
schema-version fields are added. The response retains HTTP 200 and the existing
`success/status_code/message/data` envelope (`data: null`). The human-readable
success message is generic; worker logic does not inspect it.

Requires IMPORTER or ADMIN. Anonymous requests return 401, ordinary USER returns
403. Invalid/missing book IDs return 400. Queue/exchange cannot be chosen by the
caller. Publishing is disabled by default and returns 503 until explicitly enabled.

Configuration in Catalog (also passed by Compose):

- `LEGACY_WORKER_COMPAT_ENABLED=true`
- `RABBITMQ_QUEUE_CRAWL=<exact existing Go worker queue>`

Queue declaration matches the Go worker: durable, non-exclusive, non-auto-delete,
no arguments. Messages are persistent and sent through the default exchange.
HTTP success requires broker confirm and no mandatory return. Broker failure,
nack, returned/unroutable message or confirmation timeout returns 503. A lost
confirmation/HTTP response can still duplicate delivery; this is not exactly-once.
Do not assign this queue to the Python ingestion skeleton, which is not the Go
consumer. Do not enable production crawling just because these routes now exist.

## Implemented: protected data adapter

Set `MYTRUYEN_BACKEND_MODE=compat` and keep `MYTRUYEN_BACKEND` ending in
`/api/v1` (not `/worker`). Only backend book, chapter, author and taxonomy paths
are redirected under `/api/v1/worker`. Authentication and RabbitMQ paths remain
unchanged. Omitted mode defaults to `legacy` for existing standalone deployments.
The compatibility controllers require IMPORTER/ADMIN and are disabled unless
`LEGACY_WORKER_COMPAT_ENABLED=true`.

Migration V7 adds `legacy_worker_books`: a unique source ID -> unique internal
book ID link plus source payload snapshot. Worker responses use source IDs;
public APIs continue using internal Catalog IDs. Never infer links by title or
assume identical numeric IDs. Source counters are retained in the snapshot and
returned to the worker, without overwriting local engagement/stat projections.
Thus public counters can intentionally differ from source counters.

Configure `LEGACY_WORKER_STATUS_MAP` as JSON mapping actual source status IDs to
Catalog slugs, for example `{"9":"compat-status"}` is a TEST EXAMPLE ONLY.
Populate it from verified source data and create the corresponding statuses
before importing books. Missing mappings fail with 409, without orphan books.
Insertion order and auto-increment IDs are never used to guess status identity.

The adapter retains create/update branching, creator fallback and published
chapter metadata without content. Public content reads still return 404 when
content is absent; modern chapter creation still requires draft state. This
does not implement chapter-content crawling. Duplicate identical creates are
replay-safe; different payloads return 409. PATCH retains legacy overwrite
semantics, so source updates can overwrite editor changes to submitted fields.
Deleted mapped books/chapters are not silently resurrected.

For reviewed existing data, an ADMIN can call
`POST /api/v1/admin/catalog/worker/books/{sourceId}/bind` with
`{"book_id":123,"source_status_id":9}` (example IDs only). It links an existing
Catalog record without copying/moving chapters. Status must match; conflicts
fail. Review identity against the source before each binding. Existing public
URL/ID migration remains a separate deployment decision.

The experimental `cmd/import-book` remains separate and is not used by the
legacy consumer. Its mapping tables/protocol are not imposed on old handlers.

## Deployment order (operator runbook; not executed here)

1. Stop the old consumer and scheduled jobs; back up PostgreSQL and record the
   deployed revision. Do not run old and new consumers simultaneously.
2. Deploy Identity/Catalog/Gateway; let Flyway apply migrations. Keep the Go
   worker stopped. Provision a dedicated IMPORTER account, not an admin account.
3. Set Catalog compatibility flag, exact crawl queue and verified status-map.
   Set the worker credentials and source URL in a private environment file.
   `WORKER_RABBITMQ_URL` must use the Compose hostname `rabbitmq`, the correct
   vhost, and URL-encoded credentials. Never commit the environment file.
4. Seed/review statuses, authors and taxonomy; bind existing books explicitly.
   Backfill/migrate old data separately if it has not yet been moved to Catalog.
5. Run disposable PostgreSQL/RabbitMQ integration verification before production.
   Offline H2 tests do not establish PostgreSQL locking or broker delivery parity.
6. Only with permission to crawl, start a canary with a dedicated queue/source
   scope and check book/chapter results. Starting the worker also starts its
   existing scheduled latest-book polling; it is NOT a passive/manual-only mode.
7. To enable the prepared Compose service, the operator runs
   `docker compose --profile worker up -d --build worker`. Start with
   `CRAWL_CONCURRENCY=1`. Check auth errors, queue growth, 409 conflicts and memory
   before increasing load. No Docker or live crawler was started during this work.

The Python ingestion skeleton is now under `experimental-ingestion` profile;
it does not replace the Go worker and must not consume its queue.
Rollback: stop the Go worker first, preserve backups and mapping rows, redeploy
the previous application revision. Do not undo Flyway by deleting tables or
restore an old database over new writes without a reviewed recovery plan.

## Verification

Offline tests cover message bodies, task routes, authorization, publication,
source IDs/counters, idempotency, binding, deleted records and publisher errors.
The Go contract test executes original taxonomy/book/chapter/latest/all-books
handlers against actual Spring HTTP/JPA endpoints, with a fake source and mocked
RabbitMQ publisher. It also checks author-to-creator fallback and incremental
chapters. Catalog CI enables it using `RUN_WORKER_CONTRACT_TESTS=true`.

Locally, install Go and JDK 17, set that environment variable and run
`./gradlew test bootJar --no-daemon` from `catalog-service`. Run `go test ./...`,
`go vet ./...`, `go build ./...` from `worker`, and Gradle tests in the gateway.
The contract uses H2 and a test JWT; real PostgreSQL, RabbitMQ, Identity login,
Gateway forwarding and live source behavior still require deployment checks.
