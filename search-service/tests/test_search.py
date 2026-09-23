import asyncio
import json

import httpx
import pytest
from fastapi.testclient import TestClient

from app.config import Settings
from app.main import create_app


def client(handler, timeout=2):
    settings = Settings(meili_url="http://meili.test", catalog_url="http://catalog.test",
                        meili_master_key="test-secret", request_timeout=timeout)
    return TestClient(create_app(settings, httpx.MockTransport(handler)))


def test_search_hydrates_once_preserves_rank_and_drops_hidden_books():
    calls = []

    def handler(request):
        calls.append(request)
        if request.url.host == "meili.test":
            assert request.headers["authorization"] == "Bearer test-secret"
            assert json.loads(request.content) == {"q": "truyện", "limit": 5, "offset": 5, "attributesToRetrieve": ["id"]}
            return httpx.Response(200, json={"hits": [{"id": "3"}, {"id": 1}, {"id": 2}, {"id": 3}], "estimatedTotalHits": 21})
        assert "authorization" not in request.headers
        assert request.url.params.get_list("ids") == ["3", "1", "2"]
        return httpx.Response(200, json={"success": True, "data": [
            {"id": 1, "name": "One", "published": True}, {"id": 3, "name": "Three", "published": True},
            {"id": 2, "name": "Hidden", "published": False},
        ]})

    with client(handler) as api:
        response = api.get("/api/v1/search/meili", params={"query": "truyện", "limit": 5, "page": 2},
                           headers={"Authorization": "Bearer caller-token"})
    assert response.status_code == 200
    assert [book["id"] for book in response.json()["data"]] == [3, 1]
    assert response.json()["pagination"] == {"page": 2, "size": 5, "total_items": 21, "total_pages": 5}
    assert len(calls) == 2


def test_empty_hits_do_not_call_catalog():
    calls = []

    def handler(request):
        calls.append(request)
        return httpx.Response(200, json={"hits": [], "estimatedTotalHits": 0})

    with client(handler) as api:
        result = api.get("/api/v1/search/meili?query=").json()
    assert result["data"] == []
    assert result["pagination"]["total_pages"] == 0
    assert len(calls) == 1


@pytest.mark.parametrize("params", [
    {}, {"query": "a", "page": 0}, {"query": "a", "limit": 101},
    {"query": "a", "page": 10001}, {"query": "a" * 501},
])
def test_invalid_inputs_never_call_dependencies(params):
    def handler(request):
        raise AssertionError("Should not call network")

    with client(handler) as api:
        assert api.get("/api/v1/search/meili", params=params).status_code == 422


@pytest.mark.parametrize("body", [
    {"hits": [{"id": True}]}, {"hits": [{"id": "url/1"}]}, {"hits": [{"id": -1}]},
    {"hits": [{"id": 1.5}]}, {"hits": "not a list"}, {"hits": [], "estimatedTotalHits": -1},
])
def test_malformed_search_response_is_rejected(body):
    with client(lambda request: httpx.Response(200, json=body)) as api:
        response = api.get("/api/v1/search/meili?query=test")
    assert response.status_code == 502
    assert "test-secret" not in response.text


@pytest.mark.parametrize("status", [302, 401, 429, 500])
def test_dependency_errors_do_not_leak_details(status):
    with client(lambda request: httpx.Response(status, text="upstream-secret")) as api:
        response = api.get("/api/v1/search/meili?query=test")
    assert response.status_code == 503
    assert "upstream-secret" not in response.text


def test_timeout_is_bounded():
    async def handler(request):
        await asyncio.sleep(0.2)
        return httpx.Response(200, json={"hits": []})

    with client(handler, timeout=0.05) as api:
        assert api.get("/api/v1/search/meili?query=test").status_code == 504


def test_catalog_outage_does_not_fall_back_to_index_document():
    def handler(request):
        if request.url.host == "meili.test":
            return httpx.Response(200, json={"hits": [{"id": 1, "name": "STALE PRIVATE NAME"}]})
        return httpx.Response(500, text="internal detail")

    with client(handler) as api:
        response = api.get("/api/v1/search/meili?query=test")
    assert response.status_code == 503
    assert "STALE" not in response.text


@pytest.mark.parametrize("payload", [{"success": True, "data": [{"id": 2, "published": True}]},
                                    {"success": False, "data": []}, {"data": "invalid"}])
def test_malformed_catalog_payload_is_rejected(payload):
    def handler(request):
        if request.url.host == "meili.test":
            return httpx.Response(200, json={"hits": [{"id": 1}]})
        return httpx.Response(200, json=payload)

    with client(handler) as api:
        assert api.get("/api/v1/search/meili?query=test").status_code == 502


@pytest.mark.parametrize("body", [b"not-json", b" " * 8_000_001], ids=["invalid-json", "oversized"])
def test_invalid_or_oversized_response_is_rejected(body):
    with client(lambda request: httpx.Response(200, content=body)) as api:
        assert api.get("/api/v1/search/meili?query=test").status_code == 502


@pytest.mark.parametrize("method,path", [("GET", "hybrid"), ("POST", "audio"), ("POST", "youtube")])
def test_legacy_disabled_features_remain_disabled(method, path):
    with client(lambda request: pytest.fail("No network expected")) as api:
        assert api.request(method, "/api/v1/search/" + path).status_code == 503
