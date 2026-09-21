"""Tests for password helpers and RS256 token verification."""

from app.core.security import decode_token, get_password_hash, verify_password


def test_password_hashing():
    password = "testpassword123"
    hashed = get_password_hash(password)

    assert hashed != password
    assert verify_password(password, hashed) is True
    assert verify_password("wrongpassword", hashed) is False


def test_decode_valid_rs256_token(admin_token, test_admin):
    payload = decode_token(admin_token)

    assert payload is not None
    assert payload["sub"] == str(test_admin.id)
    assert payload["roles"] == ["ROLE_ADMIN"]


def test_decode_invalid_token():
    assert decode_token("invalid.token.here") is None


def test_password_hash_uses_a_random_salt():
    password = "testpassword123"
    first_hash = get_password_hash(password)
    second_hash = get_password_hash(password)

    assert first_hash != second_hash
    assert verify_password(password, first_hash) is True
    assert verify_password(password, second_hash) is True
