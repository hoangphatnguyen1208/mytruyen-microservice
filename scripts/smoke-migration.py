"""Write smoke test for a DISPOSABLE Compose stack, never a production database.

Requires httpx and ALLOW_DISPOSABLE_SMOKE=true. Credentials are read only from
BOOTSTRAP_ADMIN_EMAIL / BOOTSTRAP_ADMIN_PASSWORD, never command-line arguments.
Uses real Identity tokens; all HTTP requests go through Gateway.
"""
import argparse
import os
import time
import uuid

import httpx


def eventually(check, *, timeout=120):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        try:
            if check():
                return
        except (httpx.HTTPError, AssertionError, ValueError):
            pass
        time.sleep(1)
    raise AssertionError("Smoke check timed out; inspect service logs, outbox and DLQ")


def run(base_url, check_topboxes=False):
    if os.environ.get("ALLOW_DISPOSABLE_SMOKE") != "true":
        raise SystemExit("Refusing writes: set ALLOW_DISPOSABLE_SMOKE=true for a disposable stack only")
    admin_email = os.environ["BOOTSTRAP_ADMIN_EMAIL"]
    admin_password = os.environ["BOOTSTRAP_ADMIN_PASSWORD"]
    marker = "smoke" + uuid.uuid4().hex
    with httpx.Client(base_url=base_url.rstrip("/") + "/api/v1/", timeout=10, follow_redirects=False) as client:
        def call(method, path, body=None, token=None, status=200, **kwargs):
            headers = {"Authorization": "Bearer " + token} if token else {}
            response = client.request(method, path, json=body, headers=headers, **kwargs)
            assert response.status_code == status, f"{method} {path}: HTTP {response.status_code}, expected {status}"
            return response.json()

        eventually(lambda: client.get("stats/books/count").status_code == 200, timeout=240)
        eventually(lambda: client.post("auth/login", json={"email": admin_email, "password": admin_password}).status_code == 200,
                   timeout=120)
        admin = call("POST", "auth/login", {"email": admin_email, "password": admin_password})["data"]["access_token"]
        email, password = marker + "@example.test", "Test-" + uuid.uuid4().hex
        call("POST", "auth/register", {"email": email, "password": password}, status=201)
        user = call("POST", "auth/login", {"email": email, "password": password})["data"]["access_token"]
        call("GET", "admin/catalog/stats/books/count", status=401)
        call("GET", "admin/catalog/stats/books/count", token=user, status=403)
        call("POST", "books", {}, status=401)
        call("POST", "books", {}, token=user, status=403)
        before = {kind: call("GET", f"stats/{kind}/count")["data"]
                  for kind in ("books", "chapters", "chapter_content")}
        author = call("POST", "authors", {"name": "author" + uuid.uuid4().hex}, admin, 201)["data"]["id"]
        status_id = call("POST", "book-statuses", {"name": marker, "slug": marker}, admin, 201)["data"]["id"]
        book = call("POST", "books", {"name": marker, "slug": marker, "author_id": author,
            "status_id": status_id, "kind": 1, "sex": 0, "synopsis": "Disposable smoke test",
            "published": False}, admin, 201)["data"]["id"]
        assert call("GET", "stats/books/count")["data"] == before["books"]
        call("GET", f"books/id/{book}", status=404)

        def indexed(query, present):
            payload = call("GET", "search/meili", params={"query": query, "limit": 10})
            found = any(row["id"] == book for row in payload["data"])
            # Empty hydrated hits alone do not prove the stale Meili document was removed.
            return found if present else not found and payload["pagination"]["total_items"] == 0

        eventually(lambda: indexed(marker, False))
        call("PATCH", f"books/id/{book}", {"published": True}, admin)
        eventually(lambda: indexed(marker, True))
        renamed_author = "writer" + uuid.uuid4().hex
        call("PATCH", f"authors/{author}", {"name": renamed_author}, admin)
        eventually(lambda: indexed(renamed_author, True))
        renamed_book = "novel" + uuid.uuid4().hex
        call("PATCH", f"books/id/{book}", {"name": renamed_book}, admin)
        eventually(lambda: indexed(renamed_book, True))
        eventually(lambda: indexed(marker, False))

        chapter = call("POST", f"chapters/id/{book}", {"index": 1, "name": "Chapter 1"}, admin, 201)["data"]["id"]
        call("POST", f"chapters/content/id/{book}/1", {"content": "Nội dung kiểm thử"}, admin, 201)
        call("POST", f"chapters/id/{chapter}/publish", token=admin)
        for kind in before:
            assert call("GET", f"stats/{kind}/count")["data"] == before[kind] + 1
        call("PATCH", f"books/id/{book}", {"published": False}, admin)
        eventually(lambda: indexed(renamed_book, False))
        for kind in before:
            assert call("GET", f"stats/{kind}/count")["data"] == before[kind]
        call("PATCH", f"books/id/{book}", {"published": True}, admin)
        eventually(lambda: indexed(renamed_book, True))
        call("DELETE", f"books/id/{book}", token=admin)
        eventually(lambda: indexed(renamed_book, False))
        call("GET", f"chapters/content/id/{book}/1", status=404)
        if check_topboxes:
            call("GET", "books/topboxes", params={"kind": 1, "limit": 10})
    print("PASS: Identity login, Gateway authorization, Catalog stats/publication, Rabbit outbox and Meili synchronization")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--check-topboxes", action="store_true", help="Also require the external topboxes provider")
    args = parser.parse_args()
    run(args.base_url, args.check_topboxes)
