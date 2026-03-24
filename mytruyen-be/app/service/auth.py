from app.crud import auth as auth_crud, user
from app.crud import user as user_crud

from app.schema.auth import Token
from datetime import timedelta, datetime

from app.core.config import settings
from app.core.security import create_access_token, create_refresh_token

from app.models import User, RefreshToken

async def authenticate(session, email: str, password: str) -> User:
    return await auth_crud.authenticate(session, email, password)

async def get_user_by_refresh_token(session, refresh_token: str):
    refresh_token_db = await auth_crud.get_by_refresh_token(session, refresh_token)
    if not refresh_token_db:
        return None
    user_id = refresh_token_db.user_id
    user_db = await user_crud.get_user_by_id(session, user_id)
    return user_db

async def rotate_refresh_token(session, old_refresh_token: str):
    user = await get_user_by_refresh_token(session, old_refresh_token)
    if not user:
        return None
    revoked_token = await auth_crud.revoke_refresh_token(session, old_refresh_token)
    if not revoked_token:
        return None
    new_refresh_token = create_refresh_token()
    refresh_token_expires_at = timedelta(days=settings.REFRESH_TOKEN_EXPIRE_DAYS) + datetime.now()
    await auth_crud.save_refresh_token(session, revoked_token.user_id, new_refresh_token, expires_at=refresh_token_expires_at)
    access_token_expires = timedelta(minutes=settings.ACCESS_TOKEN_EXPIRE_MINUTES)
    access_token=create_access_token(
        data = {
            "id": str(user.id),
            "email": user.email,
            "role": user.role.value
        }, 
        expires_delta=access_token_expires
    )
    
    return access_token, new_refresh_token

async def save_refresh_token(session, user_id: str, refresh_token: str, expires_at: datetime):
    await auth_crud.save_refresh_token(session, user_id, refresh_token, expires_at)
