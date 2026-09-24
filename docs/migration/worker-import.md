# Go worker → Catalog import (incremental rollout)

## Implemented in this increment

- Identity role `IMPORTER` (role ID `3`), provisioned only through an existing
  administrator. Public registration does not grant this role.
- Gateway route `/api/v1/internal/import/**`, authenticated like other protected
  routes. Catalog verifies the JWT itself and requires `IMPORTER` or `ADMIN`.
- Catalog book import/lookup and durable external-ID mapping via JPA/Flyway V5.
- Import creates drafts, does not accept upstream counters or publication flags,
  and detects edits outside the importer before accepting an update.

This is not a completed worker cutover. Existing Go jobs still use legacy API
payloads and queue endpoints. Do not point the current crawler at this backend
in production yet. No live crawl or Docker startup is required for unit tests.

## Authentication and provisioning

1. Deploy Identity V3 before provisioning a worker account.
2. An administrator creates a dedicated account using `POST /api/v1/users` with
   `roles: [3]`. Do not give the worker ADMIN. Store its password outside Git.
3. Worker logs in through `/api/v1/auth/login` and refreshes through
   `/api/v1/auth/refresh-token`, using the existing signed access-token contract.
4. Deploy Catalog V5 and the gateway route before enabling import requests.

`internal` is an API namespace, not a network trust boundary. Use TLS and keep
Catalog's direct port private. Stateless Catalog JWT verification means role
revocation is not instantaneous there: previously issued access tokens remain
usable until expiration. Disable an import source to stop further imports while
investigating a compromised account, and rotate/revoke its Identity credentials.

## Book import API

`PUT /api/v1/internal/import/books/metruyencv/{external_id}`

```json
{
  "metadata": {
    "name": "Example story",
    "slug": "example-story",
    "status_id": 1,
    "kind": 1,
    "sex": 1,
    "synopsis": "Description",
    "author_id": null,
    "genre_ids": [],
    "tag_ids": []
  }
}
```

`status_id`, `author_id`, `genre_ids`, and `tag_ids` refer to **Catalog IDs**, not
source IDs. In particular, Catalog author IDs are UUIDs. Status `1` above is an
example only; the referenced record must exist. Taxonomy import is a subsequent
increment; do not work around it by granting ADMIN to the crawler.

Success uses HTTP 200 for create, update and replay:

```json
{
  "status_code": 200,
  "success": true,
  "message": "Success",
  "data": {
    "source": "metruyencv",
    "external_id": "12345",
    "book_id": 42,
    "version": 0,
    "outcome": "created"
  }
}
```

- First import omits `expected_version`. It creates the book, initial counters,
  mapping and search-outbox event in one transaction.
- Lookup with `GET` at the same URL returns internal ID/version and outcome
  `mapped` or `manual_review`. It works for drafts without making drafts public.
- For changed metadata, send `expected_version` from the last successful lookup
  or write. Metadata is a full snapshot, not a PATCH; omitted optional fields may
  be cleared. A stale/missing version returns 409.
- Replaying the same metadata returns `unchanged`, without a new book or search
  event, including when retrying a successful write whose response was lost.
- An edit through other Catalog APIs (including publish/unpublish) causes a
  version mismatch with the last import. Further imports return 409 for manual
  review. There is deliberately no force-overwrite flag.
- Deleted mapped books return 409 and are never automatically recreated.
- Name/slug collisions return 409; they never auto-link an existing book.
- Unknown fields (including `id`, `published`, `chapter_count`, `word_count`,
  `view_count`, rating and engagement counters) return 400.
- Missing source/reference/mapping returns 404. Disabled source returns 409.
- Only lowercase source codes and bounded ASCII external-ID path segments are
  accepted. Keep external IDs as strings to avoid numeric conversion loss.

After a 409, inspect/re-fetch rather than blindly retrying with a new version.
If source metadata must be retried, fetch it afresh too; an internal version is
not a source-side revision or proof that an old crawl snapshot is current.

## Database and concurrency

`import_sources` registers allowed source codes; initially only `metruyencv`.
New sources require deliberate provisioning, not automatic creation by a worker.

`book_import_mappings` stores source/external ID, internal book ID, last imported
book version, last metadata snapshot and timestamps. Constraints enforce one
mapping per `(source, external_id)` and one import owner per book. The foreign key
does not cascade-delete mappings; soft-deleted books keep their identity.

Each import locks its registered source row, then the mapped book. This makes
first-time parallel delivery idempotent even before the mapping exists and works
across application instances. It deliberately serializes imports per source for
the initial low-concurrency workload. Monitor lock wait time before increasing
worker concurrency; higher-throughput per-key locking is a later optimization.
Lookup also takes these locks for a consistent version/review result.

## Existing data and cutover

Do not infer mappings from matching titles, slugs or equal numeric IDs. Existing
data requires an explicit reviewed backfill of source provenance. Backfill and
manual conflict-resolution tooling are not implemented in this increment.

Before enabling the worker:

1. Complete taxonomy/author ID mapping and book-job conversion in Go.
2. Add chapter mapping/draft import; implement authorized content retrieval
   separately before enabling publication.
3. Replace legacy RabbitMQ HTTP endpoints with confirmed direct publication,
   versioned queues, bounded retries and DLQ/replay.
4. Test against real PostgreSQL and RabbitMQ in a disposable environment.
5. Pause legacy writes, review/backfill mappings, then canary a small set of books
   on a separate versioned queue. Never run both consumers on one queue.

Rollback means stopping the new worker and disabling import traffic, not dropping
the mapping tables. Preserve mappings and backups. Legacy worker writes are not
safe to resume without reconciling IDs and partial imports first.

## Verification scope

Catalog API tests cover duplicate/concurrent delivery, draft visibility, update
version checks, replay without extra outbox events, manual edits/deletion,
authorization, invalid references/read-only fields and slug collisions. Identity
tests cover provisioning and limited authority; gateway tests reject anonymous
import lookup. Tests use H2 PostgreSQL mode, not a live PostgreSQL server; real
PostgreSQL locking/migration and end-to-end worker checks remain required.
