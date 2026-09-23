import asyncio
import json
from types import SimpleNamespace
from unittest.mock import AsyncMock

import httpx
import pytest

from app.config import Settings
from app.rebuild import RebuildError
from app.sync.indexer import Indexer, InvalidEvent, book_id
from app.sync.worker import process


@pytest.mark.parametrize("body", [b"null", b"[]", b"{}", b"x" * 1025,
    b'{"schema_version":2,"book_id":1}', b'{"schema_version":true,"book_id":1}',
    b'{"schema_version":1,"book_id":true}', b'{"schema_version":1,"book_id":0}',
    b'{"schema_version":1,"book_id":"1"}', b'{"schema_version":1,"book_id":1,"extra":1}'])
def test_rejects_invalid_events(body):
    with pytest.raises(InvalidEvent):
        book_id(body)


def test_valid_event():
    assert book_id(b'{"schema_version":1,"book_id":123}') == 123


def test_duplicate_notifications_rehydrate_current_state_then_delete():
    calls = []
    rows = [{"id": 1, "name": "Current name", "published": True, "author": {"name": "Renamed author"}}]

    def backend(request):
        calls.append(request)
        if request.url.host == "catalog.test":
            assert "authorization" not in request.headers
            assert request.url.params["ids"] == "1"
            return httpx.Response(200, json={"success": True, "data": rows})
        assert request.headers["authorization"] == "Bearer private-writer"
        if request.url.path.startswith("/tasks/"):
            return httpx.Response(200, json={"status": "succeeded"})
        return httpx.Response(202, json={"taskUid": 3})

    async def action():
        async with httpx.AsyncClient(transport=httpx.MockTransport(backend)) as client:
            indexer = Indexer(client, Settings(catalog_url="http://catalog.test", meili_url="http://meili.test",
                                               meili_master_key="private-writer"))
            await indexer.synchronize(1)
            await indexer.synchronize(1)
            rows.clear()
            await indexer.synchronize(1)
    asyncio.run(action())
    posts = [json.loads(call.content) for call in calls if call.method == "POST"]
    assert posts == [[{"id": 1, "name": "Current name", "author": "Renamed author"}]] * 2
    assert len([call for call in calls if call.method == "DELETE"]) == 1


@pytest.mark.parametrize("status,payload", [(503, {}), (200, {"success": False, "data": []}),
    (200, {"success": True, "data": [{"id": 2, "name": "Wrong book", "published": True}]}),
    (200, {"success": True, "data": [{"id": 1, "name": "Draft", "published": False}]})])
def test_catalog_failure_never_deletes_index_document(status, payload):
    async def action():
        def backend(request):
            assert request.url.host == "catalog.test"
            return httpx.Response(status, json=payload)
        async with httpx.AsyncClient(transport=httpx.MockTransport(backend)) as client:
            with pytest.raises(RebuildError):
                await Indexer(client, Settings(catalog_url="http://catalog.test")).synchronize(1)
    asyncio.run(action())


def message(body=b'{"schema_version":1,"book_id":1}'):
    return SimpleNamespace(body=body, ack=AsyncMock())


def test_ack_after_index_completion():
    async def action():
        msg = message()
        async def synchronize(identifier):
            msg.ack.assert_not_awaited()
        indexer = SimpleNamespace(synchronize=AsyncMock(side_effect=synchronize))
        dead = AsyncMock()
        await process(msg, indexer, dead)
        msg.ack.assert_awaited_once()
        dead.assert_not_awaited()
    asyncio.run(action())


def test_retries_then_acknowledges_confirmed_dead_letter():
    async def action():
        msg = message()
        indexer = SimpleNamespace(synchronize=AsyncMock(side_effect=RebuildError("offline")))
        async def dead_letter(original, reason):
            msg.ack.assert_not_awaited()
            assert reason == "dependency_failure"
        await process(msg, indexer, dead_letter, sleep=AsyncMock())
        assert indexer.synchronize.await_count == 5
        msg.ack.assert_awaited_once()
    asyncio.run(action())


def test_dlq_failure_and_cancellation_leave_original_unacknowledged():
    async def action():
        msg = message(b"invalid")
        with pytest.raises(RuntimeError):
            await process(msg, SimpleNamespace(), AsyncMock(side_effect=RuntimeError("broker offline")))
        msg.ack.assert_not_awaited()
        msg = message()
        with pytest.raises(asyncio.CancelledError):
            await process(msg, SimpleNamespace(synchronize=AsyncMock(side_effect=asyncio.CancelledError())), AsyncMock())
        msg.ack.assert_not_awaited()
    asyncio.run(action())


@pytest.mark.parametrize("task_status", ["failed", "processing"])
def test_failed_or_timed_out_meili_task_is_not_success(task_status):
    async def action():
        def backend(request):
            if request.url.host == "catalog.test":
                return httpx.Response(200, json={"success": True, "data": []})
            if request.url.path.startswith("/tasks/"):
                return httpx.Response(200, json={"status": task_status})
            return httpx.Response(202, json={"taskUid": 1})
        async with httpx.AsyncClient(transport=httpx.MockTransport(backend)) as client:
            with pytest.raises(RebuildError):
                await Indexer(client, Settings(catalog_url="http://catalog.test"), task_timeout=0.01).synchronize(1)
    asyncio.run(action())
