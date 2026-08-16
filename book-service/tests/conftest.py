import os
import uuid
from typing import AsyncGenerator

import jwt
import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy import JSON
from sqlalchemy.dialects import postgresql
from sqlalchemy.ext.asyncio import create_async_engine
from sqlmodel import SQLModel
from sqlmodel.ext.asyncio.session import AsyncSession

for key, value in {
    "PROJECT_NAME": "test", "API_V1_STR": "/api/v1", "API_V2_STR": "/api/v2",
    "POSTGRES_SERVER": "localhost", "POSTGRES_PORT": "5432", "POSTGRES_DB": "test",
    "POSTGRES_USER": "test", "POSTGRES_PASSWORD": "test",
    "POSTGRES_URL": "postgresql+asyncpg://test:test@localhost/test",
    "POSTGRES_SYNC_URL": "postgresql://test:test@localhost/test",
    "POOL_SIZE": "5", "MAX_OVERFLOW": "10", "ACCESS_TOKEN_EXPIRE_MINUTES": "60",
    "REFRESH_TOKEN_EXPIRE_DAYS": "7", "JWT_SECRET_KEY": "01234567890123456789012345678901",
    "JWT_ALGORITHM": "HS256", "FIRST_ADMIN_EMAIL": "admin@example.com",
    "FIRST_ADMIN_PASSWORD": "unused", "REDIS_HOST": "localhost", "REDIS_PORT": "6379",
    "REDIS_PASSWORD": "test", "PINECONE_API_KEY": "disabled",
    "MEILI_URL": "http://localhost:7700", "MEILI_MASTER_KEY": "test",
    "RABBITMQ_URL": "amqp://guest:guest@localhost/", "RABBITMQ_QUEUE_CRAWL": "crawl",
}.items():
    os.environ.setdefault(key, value)

postgresql.JSONB = JSON

from app.api.deps import Principal, get_db
from app.core.config import settings
from app.main import app
from app.models import Author, Book, BookStatus, Chapter, ChapterContent, Genre, Tag

test_engine = create_async_engine("sqlite+aiosqlite:///:memory:")


@pytest.fixture
async def db_session() -> AsyncGenerator[AsyncSession, None]:
    async with test_engine.begin() as connection:
        await connection.run_sync(SQLModel.metadata.create_all)
    async with AsyncSession(test_engine, expire_on_commit=False) as session:
        yield session
        await session.rollback()
    async with test_engine.begin() as connection:
        await connection.run_sync(SQLModel.metadata.drop_all)


@pytest.fixture
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient, None]:
    async def override_get_db():
        yield db_session

    app.dependency_overrides[get_db] = override_get_db
    async with AsyncClient(transport=ASGITransport(app=app), base_url="http://test") as value:
        yield value
    app.dependency_overrides.clear()


@pytest.fixture
def test_user() -> Principal:
    return Principal(id=uuid.uuid4(), roles=["ROLE_USER"])


@pytest.fixture
def test_admin() -> Principal:
    return Principal(id=uuid.uuid4(), roles=["ROLE_ADMIN"])


@pytest.fixture
def test_admin2() -> Principal:
    return Principal(id=uuid.uuid4(), roles=["ROLE_ADMIN"])


@pytest.fixture
async def test_genre(db_session: AsyncSession) -> Genre:
    value = Genre(name="Fantasy", slug="fantasy", description="Fantasy genre")
    db_session.add(value)
    await db_session.commit()
    await db_session.refresh(value)
    return value


@pytest.fixture
async def test_tag(db_session: AsyncSession) -> Tag:
    value = Tag(name="Adventure", slug="adventure", type="general", description="Adventure tag")
    db_session.add(value)
    await db_session.commit()
    await db_session.refresh(value)
    return value


@pytest.fixture
async def test_author(db_session: AsyncSession) -> Author:
    value = Author(name="Test Author", local_name="Test Author Local")
    db_session.add(value)
    await db_session.commit()
    await db_session.refresh(value)
    return value


@pytest.fixture
async def test_book(db_session: AsyncSession, test_admin: Principal, test_author: Author) -> Book:
    status = BookStatus(name="Ongoing", slug="ongoing")
    db_session.add(status)
    await db_session.commit()
    await db_session.refresh(status)
    value = Book(
        name="Test Book", slug="test-book", kind=1, sex=0, status_id=status.id,
        synopsis="Test book description", author_id=test_author.id, creator_id=test_admin.id,
        poster={"poster_default": "http://example.com/poster.jpg"}, note="Test note",
    )
    db_session.add(value)
    await db_session.commit()
    await db_session.refresh(value)
    return value


@pytest.fixture
async def test_chapter(db_session: AsyncSession, test_book: Book, test_admin: Principal) -> Chapter:
    value = Chapter(creator_id=test_admin.id, book_id=test_book.id, index=1, name="Chapter 1")
    db_session.add(value)
    await db_session.commit()
    await db_session.refresh(value)
    return value


@pytest.fixture
async def test_chapter_content(db_session: AsyncSession, test_chapter: Chapter) -> ChapterContent:
    value = ChapterContent(chapter_id=test_chapter.id, content="This is a long content. " * 1000)
    db_session.add(value)
    await db_session.commit()
    await db_session.refresh(value)
    return value


def make_token(principal: Principal) -> str:
    return jwt.encode(
        {"sub": str(principal.id), "roles": principal.roles},
        settings.JWT_SECRET_KEY,
        algorithm=settings.JWT_ALGORITHM,
    )


@pytest.fixture
def user_token(test_user: Principal) -> str:
    return make_token(test_user)


@pytest.fixture
def admin_token(test_admin: Principal) -> str:
    return make_token(test_admin)


@pytest.fixture
def admin2_token(test_admin2: Principal) -> str:
    return make_token(test_admin2)

