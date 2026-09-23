"""Offline index replacement. Catalog writes and competing indexers must be paused."""
import argparse
import asyncio
import json
import sys
import uuid

import httpx
from pydantic import BaseModel, Field, StrictBool, ValidationError

from app.config import Settings
from app.service import Hit


class RebuildError(RuntimeError):
    pass


class Author(BaseModel):
    name: str


class Book(Hit):
    name: str = Field(min_length=1)
    published: StrictBool
    author: Author | None = None


class Rebuilder:
    def __init__(self, client, settings, task_timeout=120, progress=lambda message: None):
        self.client = client
        self.settings = settings
        self.task_timeout = task_timeout
        self.progress = progress

    async def request(self, method, path, *, catalog=False, allow_missing=False, **kwargs):
        base = self.settings.catalog_url if catalog else self.settings.meili_url
        headers = {}
        if not catalog:
            key = self.settings.meili_master_key.get_secret_value()
            if key:
                headers["Authorization"] = f"Bearer {key}"
        try:
            async with asyncio.timeout(self.settings.request_timeout):
                async with self.client.stream(method, str(base).rstrip("/") + path, headers=headers, **kwargs) as response:
                    if allow_missing and response.status_code == 404:
                        return None
                    if not 200 <= response.status_code < 300:
                        raise RebuildError(f"Dependency HTTP {response.status_code}")
                    data = bytearray()
                    async for chunk in response.aiter_bytes():
                        data.extend(chunk)
                        if len(data) > 8_000_000:
                            raise RebuildError("Dependency response too large")
                    result = json.loads(data)
                    if not isinstance(result, dict):
                        raise RebuildError("Invalid dependency response")
                    return result
        except (httpx.HTTPError, TimeoutError, ValueError) as exc:
            raise RebuildError("Dependency timed out or returned invalid data; inspect task status before retry") from exc

    async def task(self, method, path, **kwargs):
        task = await self.request(method, path, **kwargs)
        uid = task.get("taskUid")
        if type(uid) is not int or uid < 0:
            raise RebuildError("Missing Meilisearch task ID")
        self.progress(f"task={uid} operation={path}")
        try:
            async with asyncio.timeout(self.task_timeout):
                while True:
                    status = await self.request("GET", f"/tasks/{uid}")
                    state = status.get("status")
                    if state == "succeeded":
                        return
                    if state in ("failed", "canceled"):
                        raise RebuildError(f"Meilisearch task {uid} {state}; target was not automatically rolled back")
                    if state not in ("enqueued", "processing"):
                        raise RebuildError(f"Invalid task {uid} status")
                    await asyncio.sleep(0.1)
        except TimeoutError as exc:
            raise RebuildError(f"Task {uid} deadline exceeded; it may still complete, do not blindly retry") from exc

    async def run(self, *, writes_paused, allow_empty=False, max_documents=1_000_000):
        if not writes_paused:
            raise RebuildError("Pause Catalog writes and other index writers before rebuilding")
        if max_documents < 1 or not 0 < self.task_timeout <= 3600:
            raise RebuildError("Invalid rebuild limits")
        target = self.settings.meili_index
        staging = f"{target}_rebuild_{uuid.uuid4().hex}"
        self.progress(f"target={target} staging={staging}")
        existing = await self.request("GET", f"/indexes/{target}", allow_missing=True)
        settings = (await self.request("GET", f"/indexes/{target}/settings")) if existing is not None else {
            "searchableAttributes": ["name", "author"], "displayedAttributes": ["id", "name", "author"]
        }
        await self.task("POST", "/indexes", json={"uid": staging, "primaryKey": "id"})
        await self.task("PATCH", f"/indexes/{staging}/settings", json=settings)
        expected = None
        page, count, last_id = 1, 0, 0
        while True:
            payload = await self.request("GET", "/api/v1/books", catalog=True,
                                         params={"page": page, "limit": 100, "sort": "id"})
            rows, pagination = payload.get("data"), payload.get("pagination")
            if payload.get("success") is not True or not isinstance(rows, list) or not isinstance(pagination, dict):
                raise RebuildError("Invalid Catalog page")
            total = pagination.get("total_items")
            if type(total) is not int or total < 0 or total > max_documents:
                raise RebuildError("Catalog total invalid or exceeds max-documents")
            if expected is None:
                expected = total
                if expected == 0 and not allow_empty:
                    raise RebuildError("Catalog is empty; use allow-empty only if intentional")
            if total != expected or pagination.get("page") != page or pagination.get("size") != 100:
                raise RebuildError("Catalog changed or returned inconsistent pagination")
            if len(rows) != min(100, expected - count):
                raise RebuildError("Catalog page incomplete or changed")
            documents = []
            try:
                for raw in rows:
                    book = Book.model_validate(raw)
                    if not book.published or book.id <= last_id:
                        raise RebuildError("Catalog returned hidden, duplicate or unordered books")
                    last_id = book.id
                    documents.append({"id": book.id, "name": book.name, "author": book.author.name if book.author else ""})
            except ValidationError as exc:
                raise RebuildError("Invalid Catalog book") from exc
            if documents:
                await self.task("POST", f"/indexes/{staging}/documents", json=documents)
            count += len(documents)
            self.progress(f"indexed={count}/{expected}")
            if count == expected:
                break
            page += 1
        stats = await self.request("GET", f"/indexes/{staging}/stats")
        if stats.get("numberOfDocuments") != count or stats.get("isIndexing") is not False:
            raise RebuildError("Staging index count or indexing state mismatch")
        # Meilisearch swap requires both indexes to exist. Create the empty destination only after validation.
        if existing is None:
            await self.task("POST", "/indexes", json={"uid": target, "primaryKey": "id"})
        await self.task("POST", "/swap-indexes", json=[{"indexes": [target, staging]}])
        # Never auto-delete staging: after swap it contains the previous live index.
        return {"index": target, "documents": count, "previous_index": staging}


async def execute(args):
    settings = Settings()
    async with httpx.AsyncClient(timeout=settings.request_timeout, follow_redirects=False) as client:
        worker = Rebuilder(client, settings, args.task_timeout, lambda message: print(message, file=sys.stderr))
        return await worker.run(writes_paused=args.catalog_writes_paused,
                                allow_empty=args.allow_empty, max_documents=args.max_documents)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--catalog-writes-paused", action="store_true",
                        help="Confirm Catalog writes and all competing index writers are paused")
    parser.add_argument("--allow-empty", action="store_true")
    parser.add_argument("--max-documents", type=int, default=1_000_000)
    parser.add_argument("--task-timeout", type=float, default=120)
    args = parser.parse_args()
    try:
        print(json.dumps(asyncio.run(execute(args))))
    except RebuildError as exc:
        print(str(exc), file=sys.stderr)
        raise SystemExit(1)


if __name__ == "__main__":
    main()
