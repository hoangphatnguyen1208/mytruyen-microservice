"""
Test cases cho Security functions
"""
import pytest
from app.core.security import (
    create_access_token,
    verify_password,
    get_password_hash,
    decode_token
)
from datetime import timedelta
import jwt
from app.core.config import settings


class TestSecurity:
    """Test security functions"""

    @pytest.mark.asyncio
    async def test_password_hashing(self):
        """Test hash và verify password"""
        password = "testpassword123"
        hashed = get_password_hash(password)
        
        # Hash nên khác password gốc
        assert hashed != password
        
        # Verify password đúng
        assert verify_password(password, hashed) is True
        
        # Verify password sai
        assert verify_password("wrongpassword", hashed) is False

    @pytest.mark.asyncio
    async def test_create_access_token(self):
        """Test tạo access token"""
        subject = "user123"
        expires_delta = timedelta(minutes=15)
        token = create_access_token(subject=subject, expires_delta=expires_delta)
        
        # Token không nên rỗng
        assert token is not None
        assert len(token) > 0
        
        # Decode và verify token
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM]
        )
        assert payload["id"] == subject

    @pytest.mark.asyncio
    async def test_create_access_token_with_expiration(self):
        """Test tạo token với thời gian expiry tùy chỉnh"""
        id = "user123"
        expires_delta = timedelta(minutes=30)
        token = create_access_token(
            data={"id": id},
            expires_delta=expires_delta
        )
        
        assert token is not None
        
        # Decode và verify
        payload = jwt.decode(
            token,
            settings.JWT_SECRET_KEY,
            algorithms=[settings.JWT_ALGORITHM]
        )
        assert payload["id"] == id
        assert "exp" in payload

    @pytest.mark.asyncio
    async def test_decode_token_valid(self):
        """Test decode token hợp lệ"""
        id = "user123"
        expires_delta = timedelta(minutes=15)
        token = create_access_token(data={"id": id}, expires_delta=expires_delta)
        
        decoded_id = decode_token(token)
        assert decoded_id == id

    @pytest.mark.asyncio
    async def test_decode_token_invalid(self):
        """Test decode token không hợp lệ"""
        invalid_token = "invalid.token.here"
        
        decoded = decode_token(invalid_token)
        assert decoded is None

    @pytest.mark.asyncio
    async def test_decode_token_expired(self):
        """Test decode token đã hết hạn"""
        subject = "user123"
        # Tạo token đã expired
        expires_delta = timedelta(seconds=-1)
        token = create_access_token(
            subject=subject,
            expires_delta=expires_delta
        )
        
        decoded = decode_token(token)
        assert decoded is None

    @pytest.mark.asyncio
    async def test_password_hash_different_for_same_password(self):
        """Test hash cùng password cho kết quả khác nhau (salt)"""
        password = "testpassword123"
        hash1 = get_password_hash(password)
        hash2 = get_password_hash(password)
        
        # Mỗi lần hash nên cho kết quả khác nhau do salt
        assert hash1 != hash2
        
        # Nhưng cả hai đều verify được với password gốc
        assert verify_password(password, hash1) is True
        assert verify_password(password, hash2) is True
