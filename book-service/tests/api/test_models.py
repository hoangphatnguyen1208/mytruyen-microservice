import uuid

import pytest
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models import Book


@pytest.mark.asyncio
async def test_book_keeps_external_creator_id(db_session: AsyncSession, test_book: Book):
    assert isinstance(test_book.creator_id, uuid.UUID)


@pytest.mark.asyncio
async def test_book_relationships_are_owned_by_book_service(
    db_session: AsyncSession, test_book: Book, test_author
):
    await db_session.refresh(test_book, ["author", "status"])
    assert test_book.author.id == test_author.id
    assert test_book.status.slug == "ongoing"

