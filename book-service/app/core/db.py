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

import redis

r = redis.Redis(
    host='redis-12984.c295.ap-southeast-1-1.ec2.cloud.redislabs.com',
    port=12984,
    decode_responses=True,
    username="default",
    password="9nkCo4EywtBxMpqAdnSUn4pMHoY3FvtN",
)
