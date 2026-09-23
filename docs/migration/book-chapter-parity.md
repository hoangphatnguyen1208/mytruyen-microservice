# Book/Chapter migration parity — 2026-09-23

Compared against mytruyen-be/app/api/v1/{book,chapter}.py, app/schema/{book,chapter}.py and app/crud/{book,chapter}.py. This is a source-level contract audit, not proof of frontend end-to-end compatibility.

| Old feature | New implementation | Status / intentional difference |
|---|---|---|
| Book list with status/page/limit | GET /api/v1/books | Supported; published/non-deleted visibility also applies to total_items |
| Book sort=field / -field | Database ordering, explicit allowlist | Restored ASC/DESC semantics; deterministic ID tie-breaker |
| Book lookup/write/delete by ID or slug | Existing BookController routes | Supported; delete is soft, slug remains reserved |
| Book creation updates Meilisearch synchronously | No synchronous indexing | Not migrated yet; search/index synchronization is a remaining feature |
| GET /books/topboxes?kind=&limit= | Catalog upstream proxy | Ported; raw JSON shape retained, bounded input/timeout, no redirects; upstream failures become 502/504 |
| Chapter global/per-book lists, ID/slug lookup | Existing ChapterController routes | Supported; global default is book_id,index,id; per-book default index,id |
| Chapter list by slug defaults limit=10 | Restored | ID-based list defaults limit=30; explicit limit works on either route |
| Chapter create by parent ID/slug | Existing POST routes | Draft-only until content exists; create returns metadata instead of old null |
| Chapter PATCH by chapter ID including published | Existing PATCH /chapters/id/{id} | Restored; metadata/publication/statistics/event update is atomic; nonblank content required |
| Chapter DELETE /id/{chapter_id}, /slug/{chapter_id} | Both aliases retained | Legacy slug DELETE actually receives chapter ID; soft deletion replaces hard deletion |
| Content GET/POST/PATCH by parent ID/slug + index | Existing content routes | Supported; server calculates hash/word count |
| Content DELETE by slug + index | Existing route, plus ID variant | Supported; unpublish first if chapter is published |
| Draft reads on public endpoints | Separate /admin/catalog reads | Deliberate security improvement: public APIs never reveal drafts/deleted data |
| Client-supplied IDs/counters/creator/book reassignment | Rejected | Deliberate integrity improvement; migration importer must preserve IDs separately |
| Nested Book.creator | creator_id only | Not parity yet; needs safe user projection/BFF composition, never cross-service DB access |
| Book.latest_chapter string | Nullable integer index | Deliberate type correction; adapt client types |
| Chapter view_count/comment_count | Omitted | Not implemented; do not invent live counters |
| Content response id | chapter_id | Different identity model: shared chapter/content primary key; adapt clients |

## Sort contract

Prefix '-' means descending; an unprefixed field means ascending. Unknown fields return 400 instead of dynamic attribute lookup/server errors. Direct entity paths and SQL-like strings are rejected.

Books support id, name, slug, kind, sex, status_id, chapter_per_week, published, created_at, updated_at, published_at, chapter_count, word_count, latest_chapter, new_chap_at, view_count, comment_count, review_count, bookmark_count, average_rating. Omitted/empty sort uses -created_at (old backend did not guarantee a default). Null dates/latest index sort last in either direction. Missing counter rows mean zero, matching response DTO defaults. Rating is rating_sum/review_count with zero for missing/zero reviews. Engagement values are local projections only; real-time population still awaits Engagement integration.

Chapters support id, book_id, index, name, word_count, published, created_at, updated_at, published_at. No sort uses content text or unimplemented engagement counters.

Book sorting is done in the database before pagination, including correlated lookups into the one-row-per-book stats/projection tables. No sorting only the current page and no per-result HTTP calls. PostgreSQL query-plan/performance validation remains necessary before deployment.

## Remaining migration work, feature-first

1. Complete automatic indexing for text search. GET /search/meili read path and offline staged rebuild are implemented; ongoing changes still require manual rebuild under paused writes.
2. Verify topboxes against the real upstream and frontend; tests currently use a local mock server.
3. Migrate crawler/import commands, retaining source IDs through a dedicated import workflow rather than public client-writable IDs.
4. Complete response compatibility/client adaptations listed above; add Gateway/frontend contract tests.
5. Rehearse actual data import and PostgreSQL tests. Existing H2 tests do not prove PostgreSQL execution plans or locking.
