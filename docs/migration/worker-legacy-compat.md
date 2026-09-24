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

## Remaining API parity work

1. Book compatibility: accept source ID and existing metadata/counter payloads;
   preserve worker-visible IDs on reads and updates. Existing records need an
   explicit migration/translation strategy; never infer identity from names.
2. Taxonomy compatibility: preserve existing find/create responses and UUID author
   IDs. In particular, the old worker sends source `status_id` but creates statuses
   without IDs; the backend needs an explicit status mapping/seed, not reliance
   on Go map iteration order or auto-increment insertion order.
3. Chapter compatibility: accept `published=true` plus `word_count` without
   forcing a separate worker content-fetch/publish pipeline. Old chapter metadata
   may be published even when content is absent. Implement this as a deliberate
   compatibility rule with tests, not a global bypass of modern API validation.
4. Define compatibility endpoint selection without exposing admin-only draft reads
   through anonymous public routes. Then test the unchanged handlers against the
   adapter before selecting a new base URL.

The default consumer has not been rewired, and the experimental `cmd/import-book`
has not been connected to it. Existing new import APIs and mappings are retained
but are not imposed on legacy worker calls.

## Verification

Offline tests cover exact old message bodies, all six routes, validation and
authorization, disabled mode, publisher ack/nack/return/failure/timeout handling.
RabbitTemplate is mocked; no real broker or live source is contacted. Real broker
delivery and book/chapter end-to-end parity are not yet verified. Docker remains
off during this work.
