import asyncio
import json

import httpx
from fastapi import HTTPException
from pydantic import BaseModel, Field, field_validator

from app.config import Settings


class Hit(BaseModel):
    id: int = Field(gt=0, le=9223372036854775807)

    @field_validator("id", mode="before")
    @classmethod
    def valid_id(cls, value):
        if type(value) is int:
            return value
        if isinstance(value, str) and len(value) <= 19 and value.isascii() and value.isdigit():
            return int(value)
        raise ValueError("Invalid book ID")


class SearchResult(BaseModel):
    hits: list[Hit] = Field(max_length=100)
    estimatedTotalHits: int = Field(default=0, ge=0, strict=True)


class SearchService:
    def __init__(self, client: httpx.AsyncClient, settings: Settings):
        self.client = client
        self.settings = settings

    async def _json(self, method: str, url: str, **kwargs):
        # Bound responses; never forward caller credentials or follow redirects.
        async with self.client.stream(method, url, **kwargs) as response:
            if response.status_code >= 300:
                raise HTTPException(503, "Search dependency unavailable")
            body = bytearray()
            async for chunk in response.aiter_bytes():
                body.extend(chunk)
                if len(body) > 8_000_000:
                    raise HTTPException(502, "Invalid search dependency response")
            return json.loads(body)

    async def search(self, query: str, limit: int, page: int):
        try:
            async with asyncio.timeout(self.settings.request_timeout):
                headers = {}
                key = self.settings.meili_master_key.get_secret_value()
                if key:
                    headers["Authorization"] = f"Bearer {key}"
                result = SearchResult.model_validate(await self._json(
                    "POST",
                    f"{str(self.settings.meili_url).rstrip('/')}/indexes/{self.settings.meili_index}/search",
                    headers=headers,
                    json={"q": query, "limit": limit, "offset": (page - 1) * limit, "attributesToRetrieve": ["id"]},
                ))
                if len(result.hits) > limit:
                    raise ValueError("Too many hits")
                ids = list(dict.fromkeys(hit.id for hit in result.hits))
                books = []
                if ids:
                    payload = await self._json(
                        "GET", f"{str(self.settings.catalog_url).rstrip('/')}/api/v1/books/batch",
                        params=[("ids", str(book_id)) for book_id in ids],
                    )
                    if not isinstance(payload, dict) or payload.get("success") is not True or not isinstance(payload.get("data"), list):
                        raise ValueError("Invalid catalog envelope")
                    found = {}
                    if len(payload["data"]) > len(ids):
                        raise ValueError("Invalid catalog batch")
                    for book in payload["data"]:
                        if not isinstance(book, dict) or type(book.get("id")) is not int or book["id"] not in ids:
                            raise ValueError("Invalid catalog book")
                        if book.get("published") is True:
                            found[book["id"]] = book
                    books = [found[book_id] for book_id in ids if book_id in found]
                total = result.estimatedTotalHits
                return {
                    "status_code": 200, "success": True, "message": "Search completed successfully",
                    "data": books,
                    "pagination": {"page": page, "size": limit, "total_items": total, "total_pages": (total + limit - 1) // limit},
                }
        except (TimeoutError, httpx.TimeoutException) as exc:
            raise HTTPException(504, "Search dependency timed out") from exc
        except httpx.HTTPError as exc:
            raise HTTPException(503, "Search dependency unavailable") from exc
        except ValueError as exc:
            raise HTTPException(502, "Invalid search dependency response") from exc
