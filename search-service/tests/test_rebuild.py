import asyncio
import json

import httpx
import pytest

from app.config import Settings
from app.rebuild import Rebuilder, RebuildError


class Backend:
    def __init__(self, *, existing=True, count=101, fail_documents=False, inconsistent=False, pending_swap=False):
        self.existing = existing
        self.count = count
        self.fail_documents = fail_documents
        self.inconsistent = inconsistent
        self.pending_swap = pending_swap
        self.calls = []
        self.tasks = {}
        self.documents = []
        self.staging = None

    def __call__(self, request):
        path = request.url.path
        body = json.loads(request.content) if request.content else None
        self.calls.append((request.method, path, body))
        if request.url.host == "catalog.test":
            assert "authorization" not in request.headers
            assert request.url.params["sort"] == "id"
            page = int(request.url.params["page"])
            start = (page - 1) * 100 + 1
            rows = [{"id": i, "name": f"Book {i}", "published": True,
                     "author": None if i % 2 else {"name": "Author"}} for i in range(start, min(start + 100, self.count + 1))]
            total = self.count + (1 if self.inconsistent and page > 1 else 0)
            return httpx.Response(200, json={"success": True, "data": rows,
                "pagination": {"page": page, "size": 100, "total_items": total}})
        assert request.headers["authorization"] == "Bearer writer-key"
        if request.method == "GET":
            if path == "/indexes/books":
                return httpx.Response(200 if self.existing else 404, json={"uid": "books"})
            if path.endswith("/settings"):
                return httpx.Response(200, json={"searchableAttributes": ["name", "author"], "synonyms": {"novel": ["story"]}})
            if path.endswith("/stats"):
                return httpx.Response(200, json={"numberOfDocuments": len(self.documents), "isIndexing": False})
            if path.startswith("/tasks/"):
                operation = self.tasks[int(path.split("/")[-1])]
                state = "succeeded"
                if self.fail_documents and operation.endswith("/documents"):
                    state = "failed"
                if self.pending_swap and operation == "/swap-indexes":
                    state = "processing"
                return httpx.Response(200, json={"status": state})
        if path == "/indexes" and body["uid"] != "books":
            self.staging = body["uid"]
        if path.endswith("/documents"):
            self.documents.extend(body)
        uid = len(self.tasks) + 1
        self.tasks[uid] = path
        return httpx.Response(202, json={"taskUid": uid})


def run(backend, **kwargs):
    async def action():
        config = Settings(meili_url="http://meili.test", catalog_url="http://catalog.test",
                          meili_master_key="writer-key")
        async with httpx.AsyncClient(transport=httpx.MockTransport(backend)) as client:
            return await Rebuilder(client, config, task_timeout=0.05).run(**kwargs)
    return asyncio.run(action())


def swapped(backend):
    return any(path == "/swap-indexes" for _, path, _ in backend.calls)


@pytest.mark.parametrize("existing", [True, False])
def test_builds_all_pages_and_swaps_only_after_success(existing):
    backend = Backend(existing=existing)
    result = run(backend, writes_paused=True)
    assert result == {"index": "books", "documents": 101, "previous_index": backend.staging}
    assert backend.documents[0] == {"id": 1, "name": "Book 1", "author": ""}
    assert backend.documents[1]["author"] == "Author"
    assert swapped(backend)
    assert not any(method == "DELETE" for method, _, _ in backend.calls)
    writes = [(path, body) for method, path, body in backend.calls if method in ("POST", "PATCH")]
    assert writes[-1] == ("/swap-indexes", [{"indexes": ["books", backend.staging]}])
    assert all(path != "/indexes/books/documents" for path, _ in writes)
    if existing:
        assert writes[1][1]["synonyms"] == {"novel": ["story"]}


def test_requires_explicit_write_pause_before_network():
    backend = Backend()
    with pytest.raises(RebuildError, match="Pause"):
        run(backend, writes_paused=False)
    assert backend.calls == []


@pytest.mark.parametrize("backend", [Backend(fail_documents=True), Backend(inconsistent=True)], ids=["failed-task", "changed-catalog"])
def test_failure_does_not_swap_or_delete_live_index(backend):
    with pytest.raises(RebuildError):
        run(backend, writes_paused=True)
    assert not swapped(backend)
    assert not any(method == "DELETE" for method, _, _ in backend.calls)


def test_empty_catalog_requires_override():
    backend = Backend(count=0)
    with pytest.raises(RebuildError, match="empty"):
        run(backend, writes_paused=True)
    assert not swapped(backend)
    result = run(Backend(count=0), writes_paused=True, allow_empty=True)
    assert result["documents"] == 0


def test_document_limit_prevents_swap():
    backend = Backend(count=101)
    with pytest.raises(RebuildError, match="max-documents"):
        run(backend, writes_paused=True, max_documents=100)
    assert not swapped(backend)


def test_swap_timeout_never_retries_or_deletes_backup():
    backend = Backend(count=1, pending_swap=True)
    with pytest.raises(RebuildError, match="may still complete"):
        run(backend, writes_paused=True)
    assert sum(path == "/swap-indexes" for _, path, _ in backend.calls) == 1
    assert not any(method == "DELETE" for method, _, _ in backend.calls)
