# MyTruyen worker

## Repository location

This directory is now the worker's development home in the MyTruyen monorepo.
Source was imported from `mytruyen-worker` commit `0c5e717`; the original repository
and its Git history remain untouched for reference. Its `.env`, binary, nested
`.git`, and automatic image-push/SSH-deploy workflows were not copied.
The Go module remains `mytruyen-worker`; run commands from this directory.
Build context is `worker/` with `worker/Dockerfile`. The worker is intentionally
not enabled in the default Compose stack while migration is incomplete.

## Migration status

The Go worker is being migrated to the microservice backend in small steps.
The HTTP/configuration foundation and a one-book metadata import command are
implemented. The default queue consumer still uses legacy IDs, payloads and
RabbitMQ HTTP endpoints. **Do not switch that consumer to the new backend in
production yet.** Use only the explicit canary command for the new contract.

Completed foundation:
- Validated API/RabbitMQ settings and bounded concurrency (default 2).
- HTTP timeout (default 30s), shutdown cancellation and no automatic retry of
  writes on network failures or server errors.
- Lazy authentication, one retry after HTTP 401, serialized token refresh;
  source API uses re-login. Tokens and authentication bodies are not logged.
- Failed lookups do not trigger creates except when the API returns 404.
- Overlapping scheduled checks are skipped within one worker process.

Configuration retains existing environment names. Optional `CRAWL_CONCURRENCY`
overrides the legacy `CRAWL_COURUTINE_COUNT`; accepted range is 1–32.
`HTTP_TIMEOUT` accepts a Go duration greater than zero and at most 5m.
Backend base URL must include `/api/v1` where required. Credentials come from
environment variables; never commit `.env` or real tokens.

## Offline checks

```sh
go test ./...
go vet ./...
go test -race ./...
```

Tests use local HTTP test servers, not production credentials, RabbitMQ or a
live crawl source. Race testing requires a supported C toolchain.

## Remaining stages

1. Reviewed mapping backfill for existing records and conflict-resolution tools.
2. Connect the new typed metadata job to versioned queues with confirmed direct
   task publication instead of legacy backend RabbitMQ HTTP endpoints.
3. Idempotent draft chapter import, gap reconciliation and separate authorized
   content adapter before publication.
4. Versioned task messages, bounded retry/backoff, DLQ/replay and graceful queue
   lifecycle. Current legacy consumer still requeues failed jobs immediately.
5. Real PostgreSQL/RabbitMQ integration verification and staged cutover runbook.

Do not run old and new workers against the same queue during cutover. No Docker
or live crawler is needed for the offline checks above.

## Metadata-only canary (manual, writes data)

The new code is split into `internal/source` (source DTOs), `internal/backend`
(Catalog DTOs/API), and `internal/jobs` (orchestration). It preserves source IDs
as strings, maps author/status/genre/tag IDs through Catalog, and imports the
book as a draft. A failed lookup never means "create anyway" except for 404.
No source counts, ratings, publication flag, or source book ID are sent as
Catalog-owned fields. A 409 stops the job; there is no forced overwrite.

Use a staging backend with Catalog migrations V5/V6 and an Identity IMPORTER
account. Export `MYTRUYEN_BACKEND`, `MYTRUYEN_EMAIL`, `MYTRUYEN_PASSWORD`,
`METRUYEN_BACKEND`, `METRUYEN_EMAIL`, `METRUYEN_PASSWORD` and optionally
`HTTP_TIMEOUT`. The command does **not** auto-load `.env`, use RabbitMQ, or start
cron. `.env.example` documents variable names only; use your secure environment
loader. Backend URL includes `/api/v1`; source URL must match its real API base.

```sh
# Explicitly authorized canary only; this contacts the source and writes metadata.
go run ./cmd/import-book -book-id 12345 -apply
```

Without `-apply`, the command exits without loading credentials or making network
requests. It never imports chapter content, publishes a book, or schedules child
jobs. The Docker image includes `/app/import-book` as a separate command, but its
default entrypoint remains the legacy consumer. No image build has been verified
in this migration session.

Current source contract requires `author` (nullable), `genres` and `tags` arrays
to be present to avoid clearing relations from a partial response. Authors need
a stable source ID; creator/uploader is not silently treated as the author.
Status names come from `/books/options?v=1`; tag types must be unambiguous.
Poster accepts an object or string URL; note accepts a string, null, or empty
array. Unsupported shapes fail for review rather than inventing data.

Reference imports create/replay only in this phase. Changes to an existing
reference's name/metadata return 409, whether changed upstream or by an admin.
This prevents renaming a shared author/tag across many books without review.
Earlier reference writes may remain if a later book write fails; rerunning uses
the same mappings and does not duplicate them. The book plus its mapping and
outbox event remain atomic inside Catalog.
