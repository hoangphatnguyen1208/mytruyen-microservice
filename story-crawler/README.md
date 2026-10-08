# MyTruyen Story Crawler

## Repository location

This directory is now the worker's development home in the MyTruyen monorepo.
Source was imported from `mytruyen-worker` commit `0c5e717`; the original repository
and its Git history remain untouched for reference. Its `.env`, binary, nested
`.git`, and automatic image-push/SSH-deploy workflows were not copied.
The Go module is `mytruyen-story-crawler`; run commands from this directory.
Build context is `story-crawler/` with `story-crawler/Dockerfile`. The worker is intentionally
not enabled in the default Compose stack while migration is incomplete.

## Internal import API

The queue consumer defaults to `MYTRUYEN_BACKEND_MODE=internal`. Backend data
requests use `/api/v1/internal/import/crawler/**`; task chaining uses
`/api/v1/internal/import/crawler/tasks/**`. Authentication still uses Identity.
The account needs `ROLE_IMPORTER` or `ROLE_ADMIN`. Set the backend base URL to
`http://localhost:8000/api/v1` when running through the API gateway.

On Book service, enable task publishing with `CRAWLER_ENABLED=true` and configure
`RABBITMQ_QUEUE_CRAWL`. Set `CRAWLER_STATUS_MAP` to map source status IDs to
local status slugs. The source `metruyencv` must be enabled in `import_sources`.
The old `/worker/**` and `/rabbitmq/**` endpoints have been removed.

Book, taxonomy and chapter imports retain the existing source ID mapping,
idempotency checks and protection against overwriting local edits. Chapters
contain metadata only: the source handler does not fetch chapter text.
The existing mapping tables remain in use, so switching routes needs no schema
migration. Only `internal` backend mode is supported.

Configuration retains existing environment names. Optional `CRAWL_CONCURRENCY`
overrides `CRAWL_COURUTINE_COUNT`; accepted range is 1–32. `HTTP_TIMEOUT` accepts
a Go duration greater than zero and at most 5m. Credentials come from environment
variables; never commit `.env` or real tokens. Do not run multiple consumers
against the same queue during cutover.

## Offline checks

```sh
go test ./...
go vet ./...
go test -race ./...
```

Tests use local HTTP test servers, not production credentials, RabbitMQ or a
live crawl source. Race testing requires a supported C toolchain.

## Experimental metadata-only canary (superseded migration approach)

The following command is not the current migration path. Its draft/versioned
semantics differ from the original worker; do not use it to validate legacy parity.

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
