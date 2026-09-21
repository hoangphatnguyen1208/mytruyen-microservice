import os
import uuid
import base64
from datetime import datetime, timedelta, timezone
from typing import AsyncGenerator

import jwt
import pytest
from httpx import ASGITransport, AsyncClient
from sqlalchemy import JSON
from sqlalchemy.dialects import postgresql
from sqlalchemy.ext.asyncio import create_async_engine
from sqlmodel import SQLModel
from sqlmodel.ext.asyncio.session import AsyncSession
from cryptography.hazmat.primitives.serialization import load_der_private_key

TEST_PRIVATE_KEY_BASE64 = "MIIEvQIBADANBgkqhkiG9w0BAQEFAASCBKcwggSjAgEAAoIBAQDccgmD471t0R+/SXX2HoEgjbuz2jB8LsiH6MfraBBIMGMDZxurpN1tun9F7HzBaAnpJ9vlGbSJuza0BtPvSezMUd2aKPnfQu0lVKD3ITxjAjRVwoSwR/W2WaUn1niQXk8xwD7vbLOsDOMxOT4Zv1SbfTwO+DeVLk5o+zQw0qTSxWcNjBrw3dpT4KmpKBiea4ixZz2auiBHf2Ah03u5qBPEZKGhvkWODpLjiWEApO9q+O8LBEgi4GF0sARzSglKVUAV4bfiaIMtnoMmNhPT3cPDogaHuZ8cutRpEZHMEvhJN7724CHwxS3bTXPInaS4Enibn4os+TEUjLyrUx4xFTlhAgMBAAECggEAH3lUrHT+nchG3RvS8MHoM8qoqwQS+hf/34+3w3+HG5d0+45kH/yY9Mq00znxkfeVuqlLNwmVgjitlcSHy9llKsLhfdot2teGXlcX6FDhe01cRYZRRY3woglokCiJ7Cra6cKF+c8uU/k/Es8Wc7yiitS1l3mPDgiff1OmXvYkPEdY+0nvfHBY0XCdP/J3dowSTMT0t0TB7ZYkq9sNFc77UN8uLSQi4qtHjNG3F2QH7kepU7Eq4m9JoYm/M8ilD3fDj5ZQM1cDSThPMoa+0a+Yl3H9wpn5qUkxu7b31IAU0ksIohFVh4t1J4cbadTUufMXBjOjR2/D1fPyO9ctDpKFBQKBgQDfwOaVY/nDlKNpWvNAP/oG5lA1xjCyyq1aefryNWq6SPO3gH8MIIIAhe8BcvAmBBsq7Ggzu1kNvPs9cFDiESrwl/0gKRfgYk9KcLPU5EXyatpnVL+hx+bOyrksRTjOSzFviM4bo6/n0zK85yxtylmPPrcmRJMhedkJdj6edF09OwKBgQD8Nxb8hndUOp4OeBLhlq3kl3vH/r02mHqFNEnHTCbGWTN96lsaRWidoop64eoZ21Fzl38XLbbEHSvMB/vyt8nXWRImy2vCVTz9Jw0OlJXCG0mnjgkAqXtEqBflZA3BMkHfjjDllTPJjDb6YKDi6h2tHChlyyaGg9zs2+e9/HoqEwKBgBVSR2ay6Sj27/9pGEbmEcg4iConoZpX797wQrZz2qC3tOmmh/S64Eh2esjzj+i/eWtErcVIM/s4J+S54Cs6oZHdmdRHtiu+knmwdaJywiuQfRFdpQkgiGDqNmz+h6Q4zBQpwCIoHeoEWRBhIv2vS4t32XH/FNoax1C8gMkOo5fjAoGAGN9Z7fdYxz6snaKrwgF5DqT9uQBfKoYo9v/sErJo1ICxekZlS5bytTD1VR74VipxwuN6zg9dCcQSsKFM8Ge9iPYouxiufNCpHhH+0KRIjIbiYZq5Oo58MI4fJSkTziyloGVGXy2ymLqyJUjoNNh/qrWvKjK5juRsIhOhq/O9HG8CgYEA1kqZaRIyU8MEmuNhpC43BvefQB4f68pGaGOHe4WcWnNq25dPFvINJznIrElrIj7/2L3QEFcP73veExy+A9gIFJvtlJudYWA4uIN9hYQEMTVWZfVWbnKLs5A+g2Z+kvl3u1H3nbj+PttlToRaL3riJil52AdwGeuMU5/kDCJJ43I="
TEST_PUBLIC_KEY_BASE64 = "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA3HIJg+O9bdEfv0l19h6BII27s9owfC7Ih+jH62gQSDBjA2cbq6Tdbbp/Rex8wWgJ6Sfb5Rm0ibs2tAbT70nszFHdmij530LtJVSg9yE8YwI0VcKEsEf1tlmlJ9Z4kF5PMcA+72yzrAzjMTk+Gb9Um308Dvg3lS5OaPs0MNKk0sVnDYwa8N3aU+CpqSgYnmuIsWc9mrogR39gIdN7uagTxGShob5Fjg6S44lhAKTvavjvCwRIIuBhdLAEc0oJSlVAFeG34miDLZ6DJjYT093Dw6IGh7mfHLrUaRGRzBL4STe+9uAh8MUt201zyJ2kuBJ4m5+KLPkxFIy8q1MeMRU5YQIDAQAB"

for key, value in {
    "PROJECT_NAME": "test", "API_V1_STR": "/api/v1", "API_V2_STR": "/api/v2",
    "POSTGRES_SERVER": "localhost", "POSTGRES_PORT": "5432", "POSTGRES_DB": "test",
    "POSTGRES_USER": "test", "POSTGRES_PASSWORD": "test",
    "POSTGRES_URL": "postgresql+asyncpg://test:test@localhost/test",
    "POSTGRES_SYNC_URL": "postgresql://test:test@localhost/test",
    "POOL_SIZE": "5", "MAX_OVERFLOW": "10", "ACCESS_TOKEN_EXPIRE_MINUTES": "60",
    "REFRESH_TOKEN_EXPIRE_DAYS": "7", "JWT_PUBLIC_KEY_BASE64": TEST_PUBLIC_KEY_BASE64,
    "JWT_ALGORITHM": "RS256", "JWT_ISSUER": "mytruyen-auth", "JWT_AUDIENCE": "mytruyen-api",
    "FIRST_ADMIN_EMAIL": "admin@example.com",
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
    now = datetime.now(timezone.utc)
    private_key = load_der_private_key(base64.b64decode(TEST_PRIVATE_KEY_BASE64), password=None)
    return jwt.encode(
        {
            "sub": str(principal.id), "roles": principal.roles,
            "iss": settings.JWT_ISSUER, "aud": settings.JWT_AUDIENCE,
            "iat": now, "exp": now + timedelta(minutes=15),
        },
        private_key,
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

