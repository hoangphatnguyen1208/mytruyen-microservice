import uuid
import pytest
from httpx import AsyncClient
from app.core.config import settings


class TestChapterAPI:
    """Comprehensive tests for chapter endpoints (API-level).

    Endpoints in router `app.api.v1.chapter`:
    - GET  /api/v1/chapter/{book_id}                       -> list chapters for book
    - POST /api/v1/chapter/{book_id}                       -> create chapter (admin)
    - PATCH /api/v1/chapter/{chapter_id}                   -> update chapter (admin)
    - GET  /api/v1/chapter/content/{chapter_id}            -> get chapter content
    - POST /api/v1/chapter/content/{chapter_id}            -> create chapter content (admin)
    - PATCH /api/v1/chapter/content/{chapter_id}           -> update chapter content (admin)

    Fixtures used (from tests/conftest.py):
    - client, test_admin, admin_token, test_user, user_token,
      test_book, test_chapter, test_chapter_content
    """

    @pytest.mark.asyncio
    async def test_list_chapters_for_book(self, client: AsyncClient, test_book, test_chapter):
        resp = await client.get(f"{settings.API_V1_STR}/chapters/{test_book.id}")
        assert resp.status_code == 200
        data = resp.json()
        assert isinstance(data, list)
        assert any(ch.get("id") == str(test_chapter.id) for ch in data)

    @pytest.mark.asyncio
    async def test_create_chapter_requires_admin(self, client: AsyncClient, test_book, user_token: str):
        resp = await client.post(
            f"{settings.API_V1_STR}/chapters/{test_book.id}",
            json={
                "chapter_id": "ch-2",
                "chapter_index": 2,
                "name": "Unauthorized Chapter",
                "content": "...",
                "published": False,
                "word_count": 1000
            },
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert resp.status_code == 403

    @pytest.mark.asyncio
    async def test_create_chapter_success_as_admin(self, client: AsyncClient, test_book, admin_token: str):
        resp = await client.post(
            f"{settings.API_V1_STR}/chapters/{test_book.id}",
            json={
                "book_id": str(test_book.id),
                "index": 2,
                "name": "New Chapter",
                "published": True,
                "word_count": 1000
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data.get("name") == "New Chapter"

    @pytest.mark.asyncio
    async def test_update_chapter_success_as_admin(self, client: AsyncClient, test_chapter, admin_token: str):
        resp = await client.patch(
            f"{settings.API_V1_STR}/chapters/{test_chapter.id}",
            json={
                "index": test_chapter.index,
                "name": "Updated Title",
                "published": True
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data.get("name") == "Updated Title"

    @pytest.mark.asyncio
    async def test_update_chapter_not_found(self, client: AsyncClient, admin_token: str):
        resp = await client.patch(
            f"{settings.API_V1_STR}/chapters/{uuid.uuid4()}",
            json={
                "index": 1,
                "name": "Nonexistent Chapter",
                "published": True,
                "word_count": 1000
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert resp.status_code == 404

    @pytest.mark.asyncio
    async def test_get_chapter_by_id(self, client: AsyncClient, test_chapter):
        resp = await client.get(f"{settings.API_V1_STR}/chapters/{test_chapter.book_id}")
        assert resp.status_code == 200
        data = resp.json()
        assert any(ch.get("id") == str(test_chapter.id) for ch in data)

    @pytest.mark.asyncio
    async def test_get_chapter_content_success(self, client: AsyncClient, test_chapter):
        resp = await client.get(f"{settings.API_V1_STR}/chapters/content/{test_chapter.id}")
        assert resp.status_code == 200
        data = resp.json()
        assert data.get("id") == str(test_chapter.id)

    @pytest.mark.asyncio
    async def test_get_chapter_content_not_found(self, client: AsyncClient):
        resp = await client.get(f"{settings.API_V1_STR}/chapters/content/{uuid.uuid4()}")
        assert resp.status_code == 404

    @pytest.mark.asyncio
    async def test_create_chapter_content_as_admin(self, client: AsyncClient, test_chapter, admin_token: str):
        resp = await client.post(
            f"{settings.API_V1_STR}/chapters/content/{test_chapter.id}",
            json={"content": "This is chapter content."},
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert resp.status_code == 200

    @pytest.mark.asyncio
    async def test_create_chapter_duplicate_index(self, client: AsyncClient, test_book, admin_token: str, test_chapter):
        resp = await client.post(
            f"{settings.API_V1_STR}/chapters/{test_book.id}",
            json={
                "book_id": str(test_book.id),
                "index": test_chapter.index,
                "name": "Duplicate",
                "word_count": 1000,
                "published": False
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert resp.status_code == 400

    @pytest.mark.asyncio
    async def test_content_requires_admin_for_create(self, client: AsyncClient, test_chapter, user_token: str):
        resp = await client.post(
            f"{settings.API_V1_STR}/chapters/content/{test_chapter.id}",
            json={"content": "attempt"},
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert resp.status_code in (401, 403)

    @pytest.mark.asyncio
    async def test_update_chapter_content_as_user(self, client: AsyncClient, test_chapter, user_token: str):
        resp = await client.patch(
            f"{settings.API_V1_STR}/chapters/content/{test_chapter.id}",
            json={"content": "attempt"},
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert resp.status_code == 403

