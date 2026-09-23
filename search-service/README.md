# Search service — legacy text-search read path

GET /api/v1/search/meili?query=...&limit=10&page=1 preserves the old route, response envelope and Meilisearch rank. Required query is at most 500 characters (empty is allowed, as in the old backend); limit is 1–100 and page is 1–10000. Invalid parameters return FastAPI 422.

The service queries Meilisearch for IDs only, then calls Catalog's public GET /api/v1/books/batch once. Catalog returns current DTOs and filters drafts/deletions; Search restores hit order and removes duplicate IDs. It does not query Catalog's database or forward user credentials. An unavailable Catalog fails the request rather than returning stale index documents.

Pagination deliberately retains the old estimatedTotalHits semantics. With a stale index, a page can be short and totals can include hidden/deleted books. The current page is not backfilled and totals are not an exact live Catalog count. Book DTO differences (creator_id, latest_chapter integer) remain documented in ../docs/migration/book-chapter-parity.md.

## Runtime

- MEILI_URL: default http://localhost:7700.
- MEILI_MASTER_KEY: secret used only for Meilisearch requests; use a restricted search key in deployment when available.
- MEILI_INDEX: default books (alphanumeric, underscore, hyphen).
- CATALOG_URL: default http://localhost:8082.
- REQUEST_TIMEOUT: total search-operation deadline in seconds, default 5, maximum 30.

HTTP connections are pooled, redirects disabled, response bodies capped at 8 MB. Dependency failures return sanitized 503, timeouts 504, malformed/oversized responses 502. /health is process liveness, not dependency readiness.

Hybrid/audio/YouTube endpoints continue returning 503, as the legacy backend did. Disabled ML code and heavy dependencies were not copied.

## Important limitation

Search requires a books index containing IDs that match Catalog. The offline rebuild command below now seeds or replaces that index. Automatic incremental indexing and outbox/consumer integration remain unfinished: edits after rebuild are not automatically reflected in ranking/matching. No production data/index was changed by this migration.

## Offline index rebuild

1. Back up Meilisearch. Pause Catalog book/author/taxonomy/chapter writes and every competing index writer/rebuild job. Keep writes paused until the command finishes or an uncertain task status is resolved. The flag below is an operator acknowledgement, not an automatic lock.
2. Configure CATALOG_URL, MEILI_URL, MEILI_INDEX and MEILI_MASTER_KEY. The rebuild key needs index create/get/swap, settings get/update, documents add, stats and task-read permissions for both target and generated staging names. Do not expose this write key to clients; normal search should use a restricted key.
3. From search-service run:

```powershell
uv run python -m app.rebuild --catalog-writes-paused
# Or inside an already-configured Compose service:
docker compose exec search-service python -m app.rebuild --catalog-writes-paused
```

The command reads public Catalog books in ascending ID pages of 100, extracts only id/name/author, and verifies ordered IDs, constant total, complete pages and final Meilisearch document count. It copies existing target settings; a fresh index searches name/author by default. Existing custom settings must remain compatible with these three indexed fields. A new UUID-named staging index is used for every run.

Each index/settings/document operation must finish successfully before the next phase. After validation, both index contents/settings are swapped atomically using Meilisearch's swap-indexes API. If the target did not exist, an empty target is created immediately before swap. The resulting previous_index name retains the old live data (or an empty placeholder on first build); no automatic deletion occurs. Failed attempts also retain staging for diagnosis.

Default maximum is 1,000,000 documents and 120 seconds per task; override with --max-documents and --task-timeout (up to 3600 seconds). Empty Catalog results abort unless --allow-empty is deliberately specified; that option will replace the live index with an empty one. Network bodies and requests remain bounded.

Task IDs and staging names are printed to stderr; final index/document count/previous_index is JSON on stdout. If a request/task times out, the server may still complete it: inspect the logged Meilisearch task before retrying or unpausing writers, especially after swap. Never blindly repeat a swap. Retain backups until verification; cleanup requires a separate deliberate operation.

This is a maintenance-window rebuild, not a live database snapshot. Count/order checks catch some concurrent changes, but cannot detect edits that keep IDs/count unchanged; pausing writers is mandatory. No distributed rebuild lock or continuous synchronization is claimed.

API reference: [Meilisearch swap-indexes specification](https://specs.meilisearch.dev/specifications/text/0191-swap-indexes-api.html).

## Verification

Run uv run pytest. Tests use HTTPX mock transports; no real Meilisearch, Catalog, or external source is contacted. Test with real dependencies and current frontend payloads before cutover.
