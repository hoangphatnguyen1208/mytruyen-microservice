"""
Test cases cho Genre endpoints
"""
import pytest
from httpx import AsyncClient
from app.core.config import settings


class TestGenre:
    """Test class cho genre endpoints"""

    @pytest.mark.asyncio
    async def test_create_genre_success(self, client: AsyncClient, admin_token: str):
        """Test tạo genre mới thành công với admin token"""
        response = await client.post(
            f"{settings.API_V1_STR}/genres",
            json={
                "name": "Science Fiction",
                "slug": "science-fiction",
                "description": "Sci-fi genre"
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == "Science Fiction"
        assert data["slug"] == "science-fiction"

    @pytest.mark.asyncio
    async def test_get_genres(self, client: AsyncClient, test_genre):
        """Test lấy danh sách tất cả genres"""
        response = await client.get(f"{settings.API_V1_STR}/genres")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert len(data) >= 1

    @pytest.mark.asyncio
    async def test_get_genre_by_slug(self, client: AsyncClient, test_genre):
        """Test lấy genre theo slug"""
        response = await client.get(
            f"{settings.API_V1_STR}/genres/fantasy"
        )
        assert response.status_code == 200
        data = response.json()
        assert data["slug"] == "fantasy"
        assert data["name"] == "Fantasy"

    # @pytest.mark.asyncio
    # async def test_get_genre_by_id(self, client: AsyncClient, test_genre):
    #     """Test lấy genre theo ID"""
    #     response = await client.get(
    #         f"{settings.API_V1_STR}/genres/{test_genre.id}"
    #     )
    #     assert response.status_code == 200
    #     data = response.json()
    #     assert data["id"] == test_genre.id  # Compare with string UUID
    #     assert data["name"] == "Fantasy"

    @pytest.mark.asyncio
    async def test_update_genre_success(self, client: AsyncClient, test_genre, admin_token: str):
        """Test update genre thành công với admin token"""
        response = await client.patch(
            f"{settings.API_V1_STR}/genres/update/{test_genre.id}",
            json={
                "name": "Updated Fantasy",
                "description": "Updated description"
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == "Updated Fantasy"
        assert data["description"] == "Updated description"

    @pytest.mark.asyncio
    async def test_update_genre_not_found(self, client: AsyncClient, admin_token: str):
        """Test update genre không tồn tại với admin token"""
        fake_id = 1000
        response = await client.patch(
            f"{settings.API_V1_STR}/genres/update/{fake_id}",
            json={
                "name": "Updated Genre"
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_create_genre_without_auth(self, client: AsyncClient):
        """Test tạo genre không có authentication - phải fail"""
        response = await client.post(
            f"{settings.API_V1_STR}/genres",
            json={
                "name": "Science Fiction",
                "slug": "science-fiction",
                "description": "Sci-fi genre"
            }
        )
        assert response.status_code == 401  # Unauthorized

    @pytest.mark.asyncio
    async def test_create_genre_non_admin(self, client: AsyncClient, user_token: str):
        """Test tạo genre với user thông thường - phải bị forbidden"""
        response = await client.post(
            f"{settings.API_V1_STR}/genres",
            json={
                "name": "Science Fiction",
                "slug": "science-fiction",
                "description": "Sci-fi genre"
            },
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert response.status_code == 403  # Forbidden

    @pytest.mark.asyncio
    async def test_update_genre_without_auth(self, client: AsyncClient, test_genre):
        """Test update genre không có authentication - phải fail"""
        response = await client.patch(
            f"{settings.API_V1_STR}/genres/update/{test_genre.id}",
            json={
                "name": "Updated Fantasy"
            }
        )
        assert response.status_code == 401  # Unauthorized

    @pytest.mark.asyncio
    async def test_update_genre_non_admin(self, client: AsyncClient, test_genre, user_token: str):
        """Test update genre với user thông thường - phải bị forbidden"""
        response = await client.patch(
            f"{settings.API_V1_STR}/genres/update/{test_genre.id}",
            json={
                "name": "Updated Fantasy"
            },
            headers={"Authorization": f"Bearer {user_token}"}
        )
        assert response.status_code == 403  # Forbidden

    @pytest.mark.asyncio
    async def test_create_genre_duplicate_slug(self, client: AsyncClient, test_genre, admin_token: str):
        """Test tạo genre với slug đã tồn tại"""
        response = await client.post(
            f"{settings.API_V1_STR}/genres",
            json={
                "name": "Another Fantasy",
                "slug": "fantasy",  # Slug đã tồn tại từ test_genre
                "description": "Duplicate slug"
            },
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 400  # Bad Request

    @pytest.mark.asyncio
    async def test_get_genre_by_slug_not_found(self, client: AsyncClient):
        """Test lấy genre theo slug không tồn tại"""
        response = await client.get(
            f"{settings.API_V1_STR}/genres/non-existent-slug"
        )
        assert response.status_code == 404  # Not Found

    # @pytest.mark.asyncio
    # async def test_get_genre_by_id_not_found(self, client: AsyncClient):
    #     """Test lấy genre theo ID không tồn tại"""
    #     fake_id = 1000
    #     response = await client.get(
    #         f"{settings.API_V1_STR}/genres/{fake_id}"
    #     )
    #     assert response.status_code == 404  

    @pytest.mark.asyncio
    async def test_delete_genre_success(self, client: AsyncClient, test_genre,                                       
                                        admin_token: str):
        """Test xóa genre thành công với admin token"""
        response = await client.delete(
            f"{settings.API_V1_STR}/genres/delete/{test_genre.id}",
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "Genre deleted successfully"

    @pytest.mark.asyncio
    async def test_delete_genre_not_found(self, client: AsyncClient, admin_token: str):
        """Test xóa genre không tồn tại với admin token"""
        fake_id = 1000
        response = await client.delete(
            f"{settings.API_V1_STR}/genres/delete/{fake_id}",
            headers={"Authorization": f"Bearer {admin_token}"}
        )
        assert response.status_code == 404
