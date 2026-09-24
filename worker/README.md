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
The HTTP/configuration foundation is implemented; the existing jobs still use
legacy IDs, payloads and RabbitMQ HTTP endpoints. **Do not switch this worker to
the new backend in production yet.**

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

1. Catalog import contract, restricted import permission and transactional
   external-ID mappings, including review/backfill for existing records.
2. Typed import DTOs and taxonomy/book job conversion; confirmed direct task
   publication instead of legacy backend RabbitMQ HTTP endpoints.
3. Idempotent draft chapter import, gap reconciliation and separate authorized
   content adapter before publication.
4. Versioned task messages, bounded retry/backoff, DLQ/replay and graceful queue
   lifecycle. Current legacy consumer still requeues failed jobs immediately.
5. Real PostgreSQL/RabbitMQ integration verification and staged cutover runbook.

Do not run old and new workers against the same queue during cutover. No Docker
or live crawler is needed for the offline checks above.
