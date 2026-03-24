from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlmodel import SQLModel
from sqlmodel.ext.asyncio.session import AsyncSession

from app.crud import user as crud_user
from app.models import user_role as UserRole
from app.schema.user import UserCreate
from app.core.config import settings

async_engine = create_async_engine(
    settings.POSTGRES_URL
)

async_session_factory = async_sessionmaker(
    bind=async_engine,
    class_=AsyncSession,
    expire_on_commit=False
)

async def init_db(session: AsyncSession):
    # async with async_engine.begin() as conn:
    #     await conn.run_sync(SQLModel.metadata.create_all)
    existing = await crud_user.get_user_by_email(session, settings.FIRST_ADMIN_EMAIL)
    if not existing:
        admin_in = UserCreate(
            email=settings.FIRST_ADMIN_EMAIL,
            password=settings.FIRST_ADMIN_PASSWORD,
            role=UserRole.ADMIN
        )
        await crud_user.create_user(session, admin_in)
        print(f"Created initial admin user with email: {settings.FIRST_ADMIN_EMAIL}")

import redis

r = redis.Redis(
    host='redis-12984.c295.ap-southeast-1-1.ec2.cloud.redislabs.com',
    port=12984,
    decode_responses=True,
    username="default",
    password="9nkCo4EywtBxMpqAdnSUn4pMHoY3FvtN",
)
