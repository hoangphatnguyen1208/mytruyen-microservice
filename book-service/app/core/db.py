from sqlalchemy.ext.asyncio import create_async_engine, async_sessionmaker
from sqlmodel.ext.asyncio.session import AsyncSession
from app.core.config import settings

async_engine = create_async_engine(
    settings.POSTGRES_URL,
    pool_size=settings.POOL_SIZE,
    max_overflow=settings.MAX_OVERFLOW,
)

async_session_factory = async_sessionmaker(
    bind=async_engine, class_=AsyncSession, expire_on_commit=False
)
