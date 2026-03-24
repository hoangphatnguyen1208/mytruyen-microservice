import uuid
from app.schema import user
from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models import User

from app.schema.user import UserCreate

from app.core.security import get_password_hash, verify_password

async def create_user(session: AsyncSession, user_create: UserCreate):
    user = User.model_validate(
        user_create, update={"hashed_password": get_password_hash(user_create.password)}
    )
    session.add(user)
    await session.commit()
    await session.refresh(user)
    return user

async def get_user_by_id(session: AsyncSession, user_id: uuid.UUID) -> User:
    user_db =  await session.get(User, user_id)
    return user_db

async def get_user_by_email(session: AsyncSession, email: str) -> User:
    users = await session.exec(select(User).where(User.email == email))
    return users.first()

async def get_user_by_username(session: AsyncSession, username: str) -> User:
    users = await session.exec(select(User).where(User.username == username))
    return users.first()

async def get_users(session: AsyncSession) -> list[User]:
    users = await session.exec(select(User))
    return users.all()

async def update_user(session: AsyncSession, user_id: uuid.UUID, user_in: user.UserUpdate) -> User:
    user_data = user_in.model_dump()
    user_data = {k: v for k, v in user_data.items() if k in user_in.model_fields_set}
    user_db = await get_user_by_id(session, user_id)
    if not user_db:
        return None
    user_db.sqlmodel_update(user_data)
    session.add(user_db)
    await session.commit()
    await session.refresh(user_db)
    return user_db

async def delete_user(session: AsyncSession, user_id: uuid.UUID) -> None:
    user_db = await get_user_by_id(session, user_id)
    if not user_db:
        return None
    await session.delete(user_db)
    await session.commit()
    return None