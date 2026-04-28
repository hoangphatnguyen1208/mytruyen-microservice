"""
Pytest configuration và fixtures cho toàn bộ test suite
"""
import pytest
import asyncio
from typing import AsyncGenerator

# IMPORTANT: Override JSONB before importing models
from sqlalchemy import JSON
from sqlalchemy.dialects import postgresql
postgresql.JSONB = JSON

from sqlmodel import SQLModel, create_engine, Session
from sqlmodel.ext.asyncio.session import AsyncSession
from sqlalchemy.ext.asyncio import create_async_engine
from sqlalchemy.orm import sessionmaker
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.core.config import settings
from app.api.deps import get_db
from app.models import Author, ChapterContent, Chapter, User, Book, Genre, Tag, user_role
from app.core.security import get_password_hash
import uuid

# Test database URL
TEST_DATABASE_URL = "sqlite+aiosqlite:///:memory:"

# Tạo async engine cho test
test_engine = create_async_engine(
    TEST_DATABASE_URL,
    echo=False,
    future=True,
)

# Tạo session factory
async_session_maker = sessionmaker(
    test_engine, class_=AsyncSession, expire_on_commit=False
)


@pytest.fixture(scope="session")
def event_loop():
    """Tạo event loop cho toàn bộ test session"""
    loop = asyncio.get_event_loop_policy().new_event_loop()
    yield loop
    loop.close()


@pytest.fixture(scope="function")
async def db_session() -> AsyncGenerator[AsyncSession, None]:
    """Fixture cung cấp database session cho mỗi test"""
    async with test_engine.begin() as conn:
        await conn.run_sync(SQLModel.metadata.create_all)
    
    async with async_session_maker() as session:
        yield session
        await session.rollback()
    
    async with test_engine.begin() as conn:
        await conn.run_sync(SQLModel.metadata.drop_all)


@pytest.fixture(scope="function")
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient, None]:
    """Fixture cung cấp HTTP client cho test"""
    async def override_get_db():
        yield db_session
    
    app.dependency_overrides[get_db] = override_get_db
    
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test"
    ) as ac:
        yield ac
    
    app.dependency_overrides.clear()


@pytest.fixture
async def test_user(db_session: AsyncSession) -> User:
    """Tạo user thông thường cho test"""
    user = User(
        email="testuser@example.com",
        hashed_password=get_password_hash("testpassword123"),
        full_name="Test User",
        role=user_role.USER,
        is_active=True
    )
    db_session.add(user)
    await db_session.commit()
    await db_session.refresh(user)
    return user


@pytest.fixture
async def test_admin(db_session: AsyncSession) -> User:
    """Tạo admin user cho test"""
    admin = User(
        email="admin@example.com",
        hashed_password=get_password_hash("adminpassword123"),
        full_name="Admin User",
        role=user_role.ADMIN,
        is_active=True
    )
    db_session.add(admin)
    await db_session.commit()
    await db_session.refresh(admin)
    return admin

@pytest.fixture
async def test_admin2(db_session: AsyncSession) -> User:
    """Tạo admin user thứ hai cho test"""
    admin2 = User(
        email="admin2@example.com",
        hashed_password=get_password_hash("admin2password123"),
        full_name="Admin User 2",
        role=user_role.ADMIN,
        is_active=True
    )
    db_session.add(admin2)
    await db_session.commit()
    await db_session.refresh(admin2)
    return admin2


@pytest.fixture
async def test_genre(db_session: AsyncSession) -> Genre:
    """Tạo genre cho test"""
    genre = Genre(
        name="Fantasy",
        slug="fantasy",
        description="Fantasy genre"
    )
    db_session.add(genre)
    await db_session.commit()
    await db_session.refresh(genre)
    return genre


@pytest.fixture
async def test_tag(db_session: AsyncSession) -> Tag:
    """Tạo tag cho test"""
    tag = Tag(
        name="Adventure",
        slug="adventure",
        type="general",
        description="Adventure tag"
    )
    db_session.add(tag)
    await db_session.commit()
    await db_session.refresh(tag)
    return tag


@pytest.fixture
async def test_book(db_session: AsyncSession, test_admin: User) -> Book:
    """Tạo book cho test"""
    book = Book(
        name="Test Book",  # Đổi từ title -> name
        slug="test-book",
        kind=1,  # Required field
        sex=0,  # Required field (0: Nam, 1: Nữ, 2: Khác)
        status_id=1,  # Required field
        synopsis="Test book description",  # Đổi từ description -> synopsis
        author_id=test_admin.id,
        creator_id=test_admin.id,
        poster={
            "poster_default": "http://example.com/poster.jpg",
            "poster_600": "http://example.com/poster_600.jpg",
            "poster_300": "http://example.com/poster_300.jpg",
            "poster_150": "http://example.com/poster_150.jpg"
        },
        note="Test note",  # Required field
    )
    db_session.add(book)
    await db_session.commit()
    await db_session.refresh(book)
    return book

@pytest.fixture
async def test_chapter(db_session: AsyncSession, test_book: Book, test_admin: User) -> None:
    """Tạo chapter cho test"""
    chapter = Chapter(
        creator_id=test_admin.id,
        book_id=test_book.id,
        index=1,
        name="Chapter 1",
        content="This is the content of chapter 1.",
        is_published=True
    )
    db_session.add(chapter)
    await db_session.commit()
    await db_session.refresh(chapter)
    return chapter

@pytest.fixture
async def test_chapter_content(db_session: AsyncSession, test_chapter: Chapter) -> None:
    """Tạo chapter với nội dung dài cho test"""
    long_content = "This is a long content. " * 1000
    chapter_content = ChapterContent(
        chapter_id=test_chapter.id,
        content=long_content
    )
    db_session.add(chapter_content)
    await db_session.commit()
    await db_session.refresh(chapter_content)
    return chapter_content

@pytest.fixture
async def test_author(db_session: AsyncSession) -> Author:
    """Tạo author cho test"""
    author = Author(
        name="Test Author",
        local_name="Test Author Local"
    )
    db_session.add(author)
    await db_session.commit()
    await db_session.refresh(author)
    return author

@pytest.fixture
async def user_token(client: AsyncClient, test_user: User) -> str:
    """Lấy access token cho user thông thường"""
    response = await client.post(
        f"{settings.API_V1_STR}/auth/login",
        json={
            "email": "testuser@example.com",
            "password": "testpassword123"
        }
    )
    return response.json()["access_token"]


@pytest.fixture
async def admin_token(client: AsyncClient, test_admin: User) -> str:
    """Lấy access token cho admin"""
    response = await client.post(
        f"{settings.API_V1_STR}/auth/login",
        json={
            "email": "admin@example.com",
            "password": "adminpassword123"
        }
    )
    return response.json()["access_token"]

@pytest.fixture
async def admin2_token(client: AsyncClient, test_admin2: User) -> str:
    """Lấy access token cho admin thứ hai"""
    response = await client.post(
        f"{settings.API_V1_STR}/auth/login",
        json={
            "email": "admin2@example.com",
            "password": "admin2password123"
        }
    )
    return response.json()["access_token"]