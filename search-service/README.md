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

This stage implements querying an EXISTING books index containing IDs that match Catalog. A fresh Compose deployment does not yet populate that index. Automatic book indexing, initial rebuild and outbox/consumer integration remain unfinished; do not cut over search until those are implemented and tested. No production data/index was changed by this migration.

## Verification

Run uv run pytest. Tests use HTTPX mock transports; no real Meilisearch, Catalog, or external source is contacted. Test with real dependencies and current frontend payloads before cutover.
