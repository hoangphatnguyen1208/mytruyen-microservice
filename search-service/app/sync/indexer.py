from typing import Literal

from pydantic import ConfigDict, ValidationError

from app.rebuild import Book, Rebuilder, RebuildError
from app.service import Hit


class Event(Hit):
    # id uses the same signed BIGINT validation as search hits.
    model_config = ConfigDict(extra="forbid", populate_by_name=True)
    schema_version: Literal[1]


class InvalidEvent(ValueError):
    pass


def book_id(body: bytes) -> int:
    import json

    if len(body) > 1024:
        raise InvalidEvent("Event too large")
    try:
        value = json.loads(body)
        if not isinstance(value, dict) or set(value) != {"schema_version", "book_id"}:
            raise InvalidEvent("Invalid event fields")
        if type(value["schema_version"]) is not int or type(value["book_id"]) is not int:
            raise InvalidEvent("Invalid event types")
        return Event(id=value["book_id"], schema_version=value["schema_version"]).id
    except (ValueError, TypeError, ValidationError) as exc:
        raise InvalidEvent("Invalid event") from exc


class Indexer(Rebuilder):
    async def initialize(self):
        target = self.settings.meili_index
        if await self.request("GET", f"/indexes/{target}", allow_missing=True) is None:
            await self.task("POST", "/indexes", json={"uid": target, "primaryKey": "id"})
        # Run only after acquiring the single active consumer slot, never during rebuild.
        await self.task("PATCH", f"/indexes/{target}/settings", json={
            "searchableAttributes": ["name", "author"],
            "displayedAttributes": ["id", "name", "author"],
        })

    async def synchronize(self, identifier: int):
        payload = await self.request("GET", "/api/v1/books/batch", catalog=True,
                                     params={"ids": identifier})
        if payload.get("success") is not True or not isinstance(payload.get("data"), list):
            raise RebuildError("Invalid Catalog batch")
        rows = payload["data"]
        path = f"/indexes/{self.settings.meili_index}/documents"
        if not rows:
            # Only a successful empty Catalog result authorizes removal; failures never do.
            await self.task("DELETE", f"{path}/{identifier}")
            return
        if len(rows) != 1:
            raise RebuildError("Unexpected Catalog batch size")
        try:
            book = Book.model_validate(rows[0])
        except ValidationError as exc:
            raise RebuildError("Invalid Catalog book") from exc
        if book.id != identifier or not book.published:
            raise RebuildError("Catalog returned mismatched or hidden book")
        await self.task("POST", path, json=[{
            "id": book.id, "name": book.name, "author": book.author.name if book.author else "",
        }])
