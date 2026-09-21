import uuid
import jwt
import base64
from cryptography.hazmat.primitives.serialization import load_der_public_key
from passlib.context import CryptContext

from app.core.config import settings

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")

def decode_token(token: str) -> dict:
    """Decode JWT token và trả về subject (user_id)"""
    try:
        public_key = load_der_public_key(
            base64.b64decode(settings.JWT_PUBLIC_KEY_BASE64)
        )
        payload = jwt.decode(
            token,
            public_key,
            algorithms=[settings.JWT_ALGORITHM],
            issuer=settings.JWT_ISSUER,
            audience=settings.JWT_AUDIENCE,
            options={"require": ["exp", "iat", "sub", "iss", "aud"]},
        )
        return payload
    except jwt.ExpiredSignatureError:
        return None
    except (jwt.PyJWTError, jwt.DecodeError):
        return None

def get_password_hash(password: str) -> str:
    return pwd_context.hash(password)

def verify_password(plain_password: str, hashed_password: str) -> bool:
    return pwd_context.verify(plain_password, hashed_password)

def create_refresh_token() -> str:
    return str(uuid.uuid4())
