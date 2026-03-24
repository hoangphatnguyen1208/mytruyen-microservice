"""
Test cases for Authentication endpoints
"""
import pytest
from httpx import AsyncClient
from app.core.config import settings


class TestAuth:
    """Test class cho authentication endpoints"""

    @pytest.mark.asyncio
    async def test_register_success(self, client: AsyncClient):
        """Test đăng ký user mới thành công"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/register",
            json={
                "email": "newuser@example.com",
                "password": "newpassword123",
            }
        )
        assert response.status_code == 200
        data = response.json()
        assert data["message"] == "User registered successfully"

    @pytest.mark.asyncio
    async def test_register_duplicate_email(self, client: AsyncClient, test_user):
        """Test đăng ký với email đã tồn tại"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/register",
            json={
                "email": "testuser@example.com",
                "password": "password123",
            }
        )
        assert response.status_code == 400

    @pytest.mark.asyncio
    async def test_login_success(self, client: AsyncClient, test_user):
        """Test đăng nhập thành công"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/login",
            json={
                "email": "testuser@example.com",
                "password": "testpassword123"
            }
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    @pytest.mark.asyncio
    async def test_login_wrong_password(self, client: AsyncClient, test_user):
        """Test đăng nhập với mật khẩu sai"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/login",
            json={
                "email": "testuser@example.com",
                "password": "wrongpassword"
            }
        )
        assert response.status_code == 400

    @pytest.mark.asyncio
    async def test_login_nonexistent_user(self, client: AsyncClient):
        """Test đăng nhập với user không tồn tại"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/login",
            json={
                "email": "nonexistent@example.com",
                "password": "password123"
            }
        )
        assert response.status_code == 400

    @pytest.mark.asyncio
    async def test_login_access_token_with_oauth2(self, client: AsyncClient, test_user):
        """Test đăng nhập với OAuth2 password flow"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/login/access-token",
            data={
                "username": "testuser@example.com",
                "password": "testpassword123"
            }
        )
        assert response.status_code == 200
        data = response.json()
        assert "access_token" in data
        assert data["token_type"] == "bearer"

    @pytest.mark.asyncio
    async def test_login_access_token_wrong_credentials(self, client: AsyncClient):
        """Test OAuth2 login với credentials sai"""
        response = await client.post(
            f"{settings.API_V1_STR}/auth/login/access-token",
            data={
                "username": "wrong@example.com",
                "password": "wrongpassword"
            }
        )
        assert response.status_code == 400
