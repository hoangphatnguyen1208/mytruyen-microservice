"""
Test cases cho Book endpoints
"""
import pytest
from httpx import AsyncClient
from app.core.config import settings


class TestBook:
    """Test class cho book endpoints"""

    @pytest.mark.asyncio
    async def test_create_book_success(self, client: AsyncClient, admin_token: str, test_genre, test_tag):
        """Test tạo book mới thành công (admin)"""
        response = await client.post(
            f"{settings.API_V1_STR}/books",
            json={
                "name": "New Book",
                "slug": "new-book",
                "kind": 1,
                "sex": 0,
                "status_id":1,
                "chapter_per_week": 3,
                "published": True,
                "synopsis": "New book synopsis",
                "note": "Test note",
                "genre_ids": [test_genre.id],
                "tag_ids": [test_tag.id],
                "poster": {
                    "default": "http://example.com/poster.jpg",
                    "600": "http://example.com/poster_600.jpg",
                    "300": "http://example.com/poster_300.jpg",
                    "150": "http://example.com/poster_150.jpg"
                }
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "New Book"
        assert data["slug"] == "new-book"

    @pytest.mark.asyncio
    async def test_create_book_duplicate_slug(
        self, client: AsyncClient, admin_token: str, test_book, test_genre, test_tag
    ):
        """Test tạo book với slug đã tồn tại"""
        response = await client.post(
            f"{settings.API_V1_STR}/books",
            json={
                "name": "Duplicate Book",
                "slug": "test-book",
                "kind": 1,
                "sex": 0,
                "status_id": 1,
                "chapter_per_week": 3,
                "published": True,
                "synopsis": "Duplicate book synopsis",
                "note": "Test note",
                "genre_ids": [test_genre.id],
                "tag_ids": [test_tag.id],
                "poster": {
                    "default": "http://example.com/poster.jpg",
                    "600": "http://example.com/poster_600.jpg",
                    "300": "http://example.com/poster_300.jpg",
                    "150": "http://example.com/poster_150.jpg"
                }
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        # Should return 400 when slug already exists
        assert response.status_code == 400

    @pytest.mark.asyncio
    async def test_create_book_without_auth(self, client: AsyncClient, test_genre, test_tag):
        """Test tạo book không có authentication"""
        response = await client.post(
            f"{settings.API_V1_STR}/books",
            json={
                "name": "New Book",
                "slug": "new-book",
                "kind": 1,
                "sex": 0,
                "status_id": 1,
                "chapter_per_week": 3,
                "published": True,
                "synopsis": "New book synopsis",
                "note": "Test note",
                "genre_ids": [str(test_genre.id)],
                "tag_ids": [str(test_tag.id)],
                "poster": {
                    "default": "http://example.com/poster.jpg",
                    "600": "http://example.com/poster_600.jpg",
                    "300": "http://example.com/poster_300.jpg",
                    "150": "http://example.com/poster_150.jpg"
                }
            }
        )
        assert response.status_code == 401

    @pytest.mark.asyncio
    async def test_create_book_as_user(
        self, client: AsyncClient, user_token: str, test_genre, test_tag
    ):
        """Test tạo book với user thông thường (không phải admin)"""
        response = await client.post(
            f"{settings.API_V1_STR}/books",
            json={
                "name": "New Book",
                "slug": "user-book",
                "kind": 1,
                "sex": 0,
                "status_id": 1,
                "chapter_per_week": 3,
                "published": True,
                "synopsis": "User book synopsis",
                "note": "Test note",
                "genre_ids": [str(test_genre.id)],
                "tag_ids": [str(test_tag.id)],
                "poster": {
                    "default": "http://example.com/poster.jpg",
                    "600": "http://example.com/poster_600.jpg",
                    "300": "http://example.com/poster_300.jpg",
                    "150": "http://example.com/poster_150.jpg"
                }
            },
            headers={"Authorization": f"Bearer {user_token}"}
        )
        # Should fail because user is not admin
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_get_books(self, client: AsyncClient, test_book):
        """Test lấy danh sách tất cả books"""
        response = await client.get(f"{settings.API_V1_STR}/books")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) >= 1

    @pytest.mark.asyncio
    async def test_get_book_by_slug_success(self, client: AsyncClient, test_book):
        """Test lấy book theo slug thành công"""
        response = await client.get(
            f"{settings.API_V1_STR}/books/test-book"
        )
        assert response.status_code == 200
        data = response.json()
        assert data["slug"] == "test-book"

    @pytest.mark.asyncio
    async def test_get_book_by_slug_not_found(self, client: AsyncClient):
        """Test lấy book với slug không tồn tại"""
        response = await client.get(
            f"{settings.API_V1_STR}/books/nonexistent-book"
        )
        # Should return 404 when book not found
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_update_book_success(
        self, client: AsyncClient, admin_token: str, test_book
    ):
        """Test update book thành công"""
        response = await client.patch(
            f"{settings.API_V1_STR}/books/test-book",
            json={
                "name": "Updated Book Title",
                "synopsis": "Updated synopsis"
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == "Updated Book Title"

    @pytest.mark.asyncio
    async def test_update_book_not_found(
        self, client: AsyncClient, admin_token: str
    ):
        """Test update book không tồn tại"""
        response = await client.patch(
            f"{settings.API_V1_STR}/books/nonexistent-book",
            json={"name": "Updated Title"},
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        # Should return 404 when book not found
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_update_book_forbidden(
        self, client: AsyncClient, admin2_token: str, test_book
    ):
        """Test update book của người khác (forbidden)"""
        response = await client.patch(
            f"{settings.API_V1_STR}/books/test-book",
            json={"name": "Updated Title"},
            headers={"Authorization": f"Bearer {admin2_token}"}
        )
        # Should return 403 when user tries to update admin's book
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_delete_book_success(
        self, client: AsyncClient, admin_token: str, test_book
    ):
        """Test xóa book thành công"""
        response = await client.delete(
            f"{settings.API_V1_STR}/books/test-book",
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "Book deleted successfully"

    @pytest.mark.asyncio
    async def test_delete_book_not_found(
        self, client: AsyncClient, admin_token: str
    ):
        """Test xóa book không tồn tại"""
        response = await client.delete(
            f"{settings.API_V1_STR}/books/nonexistent-book",
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        # Should return 404 when book not found
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_delete_book_forbidden(
        self, client: AsyncClient, admin2_token: str, test_book
    ):
        """Test xóa book của người khác (forbidden)"""
        response = await client.delete(
            f"{settings.API_V1_STR}/books/test-book",
            headers={"Authorization": f"Bearer {admin2_token}"}
        )
        # Should return 403 when user tries to delete admin's book
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_delete_book_without_auth(self, client: AsyncClient, test_book):
        """Test xóa book không có authentication"""
        response = await client.delete(
            f"{settings.API_V1_STR}/books/test-book"
        )
        assert response.status_code == 401
