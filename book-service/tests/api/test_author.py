"""
Test cases for author endpoints.
"""
from httpx import AsyncClient
import pytest
from app.core.config import settings
import uuid

class TestAuthor:
    """Test class for author endpoints."""

    @pytest.mark.asyncio
    async def test_create_author_as_admin(self, client: AsyncClient, admin_token: str):
        """Test creating an author as admin."""
        payload = {
            "name": "New Author",
            "local_name": "New Author Local"
        }
        headers = {"Authorization": f"Bearer {admin_token}"}
        response = await client.post(f"{settings.API_V1_STR}/authors", json=payload, headers=headers)
        assert response.status_code == 201
        data = response.json()
        assert data["name"] == payload["name"]
        assert data["local_name"] == payload["local_name"]
    
    pytest.mark.asyncio
    async def test_create_author_requires_admin(self, client: AsyncClient, user_token: str):
        """Test that creating an author requires admin privileges."""
        payload = {
            "name": "Unauthorized Author",
            "local_name": "Unauthorized Author Local"
        }
        headers = {"Authorization": f"Bearer {user_token}"}
        response = await client.post(f"{settings.API_V1_STR}/authors", json=payload, headers=headers)
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_get_authors_list(self, client: AsyncClient, test_author):
        """Test getting the list of authors."""
        response = await client.get(f"{settings.API_V1_STR}/authors")
        assert response.status_code == 200
        data = response.json()
        assert isinstance(data, list)
        assert any(author["name"] == test_author.name for author in data)

    @pytest.mark.asyncio
    async def test_get_author_by_name(self, client: AsyncClient, test_author):
        """Test getting an author by name."""
        response = await client.get(f"{settings.API_V1_STR}/authors/{test_author.name}")
        assert response.status_code == 200
        data = response.json()
        assert data["id"] == str(test_author.id)
        assert data["name"] == test_author.name

    @pytest.mark.asyncio
    async def test_get_author_not_found(self, client: AsyncClient):
        """Test getting a non-existent author."""
        response = await client.get(f"{settings.API_V1_STR}/authors/nonexistent-author")
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_update_author_as_admin(self, client: AsyncClient, test_author, admin_token: str):
        """Test updating an author as admin."""
        payload = {
            "name": "Updated Author Name",
            "local_name": "Updated Author Local Name"
        }
        headers = {"Authorization": f"Bearer {admin_token}"}
        response = await client.patch(f"{settings.API_V1_STR}/authors/{test_author.id}", json=payload, headers=headers)
        assert response.status_code == 200
        data = response.json()
        assert data["name"] == payload["name"]
        assert data["local_name"] == payload["local_name"]

    @pytest.mark.asyncio
    async def test_delete_author_as_admin(self, client: AsyncClient, test_author, admin_token: str):
        """Test deleting an author as admin."""
        headers = {"Authorization": f"Bearer {admin_token}"}
        response = await client.delete(f"{settings.API_V1_STR}/authors/{test_author.id}", headers=headers)
        assert response.status_code == 204

    @pytest.mark.asyncio
    async def test_delete_author_not_found(self, client: AsyncClient, admin_token: str):
        """Test deleting a non-existent author."""
        headers = {"Authorization": f"Bearer {admin_token}"}
        fake_id = uuid.uuid4()
        response = await client.delete(f"{settings.API_V1_STR}/authors/{fake_id}", headers=headers)
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_delete_author_requires_admin(self, client: AsyncClient, test_author, user_token: str):
        """Test that deleting an author requires admin privileges."""
        headers = {"Authorization": f"Bearer {user_token}"}
        response = await client.delete(f"{settings.API_V1_STR}/authors/{test_author.id}", headers=headers)
        assert response.status_code == 403

    @pytest.mark.asyncio
    async def test_update_author_not_found(self, client: AsyncClient, admin_token: str):
        """Test updating a non-existent author."""
        payload = {
            "name": "Nonexistent Author",
            "local_name": "Nonexistent Author Local"
        }
        headers = {"Authorization": f"Bearer {admin_token}"}
        fake_id = uuid.uuid4()
        response = await client.patch(f"{settings.API_V1_STR}/authors/{fake_id}", json=payload, headers=headers)
        assert response.status_code == 404

    @pytest.mark.asyncio
    async def test_update_author_requires_admin(self, client: AsyncClient, test_author, user_token: str):
        """Test that updating an author requires admin privileges."""
        payload = {
            "name": "Unauthorized Update",
            "local_name": "Unauthorized Update Local"
        }
        headers = {"Authorization": f"Bearer {user_token}"}
        response = await client.patch(f"{settings.API_V1_STR}/authors/{test_author.id}", json=payload, headers=headers)
        assert response.status_code == 403