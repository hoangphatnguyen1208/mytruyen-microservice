import datetime

from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession
from app.models import User, RefreshToken
from app.core.security import verify_password

async def authenticate(session: AsyncSession, email: str, password: str) -> User:
    users = await session.exec(select(User).where(User.email == email))
    user_db = users.first()
    if not user_db:
        return None
    if not verify_password(password, user_db.hashed_password):
        return None
    return user_db

async def save_refresh_token(session: AsyncSession, user_id: str, refresh_token: str, expires_at: datetime) -> None:
    refresh_token_db = RefreshToken(user_id=user_id, token=refresh_token, expires_at=expires_at)
    session.add(refresh_token_db)
    await session.commit()
    return None

async def get_by_refresh_token(session: AsyncSession, refresh_token: str) -> RefreshToken:
    refresh_tokens = await session.exec(select(RefreshToken).where(RefreshToken.token == refresh_token))
    refresh_token_db = refresh_tokens.first()
    if not refresh_token_db:
        return None
    return refresh_token_db

async def revoke_refresh_token(session: AsyncSession, refresh_token: str) -> RefreshToken:
    refresh_token_db = await get_by_refresh_token(session, refresh_token)
    if not refresh_token_db:
        return None
    refresh_token_db.revoked = True
    await session.commit()
    await session.refresh(refresh_token_db)
    return refresh_token_db
