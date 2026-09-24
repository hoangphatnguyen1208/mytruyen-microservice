# Go worker → Catalog import (incremental rollout)

> Direction changed: preserve the original worker business logic, adapting APIs
> or endpoint URLs only. The typed draft/versioned import described below remains
> an optional experiment, not the target consumer workflow. Current work and
> remaining compatibility gaps are in [worker-legacy-compat.md](worker-legacy-compat.md).

## Implemented in this increment

- Identity role `IMPORTER` (role ID `3`), provisioned only through an existing
  administrator. Public registration does not grant this role.
- Gateway route `/api/v1/internal/import/**`, authenticated like other protected
  routes. Catalog verifies the JWT itself and requires `IMPORTER` or `ADMIN`.
- Catalog book import/lookup and durable external-ID mapping via JPA/Flyway V5.
- Import creates drafts, does not accept upstream counters or publication flags,
  and detects edits outside the importer before accepting an update.

The Go source now lives in `worker/` in this repository. The standalone repository
was retained unchanged; its credentials, binary and deployment workflows were not
imported. `worker/cmd/import-book` provides an explicit metadata-only canary using
the new contract. This is not a completed worker cutover: the default queue
consumer still uses legacy API payloads and queue endpoints. Do not point that
consumer at this backend in production yet. Unit tests require no live crawl or Docker.

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
example only; the referenced record must exist. Resolve references through the
reference-import API below; do not grant ADMIN to the crawler.

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

1. Verify the source DTOs and metadata-only canary in `worker/cmd/import-book`
   against an authorized staging source. Reference mapping and typed book-job
   conversion are implemented, but not yet connected to the queue consumer.
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

## Reference import (Catalog V6)

`PUT /api/v1/internal/import/{kind}/metruyencv/{external_id}` supports `authors`,
`genres`, `tags`, and `book-statuses`. GET on the same URL resolves the mapping.
Both operations require IMPORTER or ADMIN.

- Authors accept the existing `AuthorWrite` fields: `name`, `local_name`, `avatar`.
- Taxonomy accepts `name`, `slug`, `description`; tags additionally require
  `type`. Do not send `type` for genres/statuses except as null.
- Response data contains `source`, `external_id`, `kind`, `reference_id` and
  `outcome`. Reference ID is a **string**, UUID for authors and decimal Catalog ID
  for taxonomy. Never use the source's ID as the returned internal ID.
- Repeated identical requests return the same ID and outcome `unchanged`.
- Changed source metadata or an admin-edited reference returns 409; lookup after
  an admin edit returns `manual_review`. No automatic renaming/merging in this
  increment, because references are shared across books.
- Distinct author source IDs may have identical names; names do not identify an
  author. Slug collisions on taxonomy require review and are not auto-linked.

`reference_import_mappings` has unique `(source, kind, external_id)` and separate
nullable foreign keys for each reference type, enforced by a CHECK selecting
exactly the matching target. Each reference has one import owner. Restrictive
foreign keys preserve provenance: deleting a mapped reference returns 409 rather
than dropping its mapping and silently recreating it on the next crawl. Mapping
cleanup/merge is an explicit future maintenance workflow, not a worker operation.

Source-row locking and transactional creation provide the same duplicate-delivery
protection as book imports. Read locks stabilize replay checks against admin
updates. No schema changes to existing taxonomy tables are required.
