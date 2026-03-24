from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.schema.author import AuthorCreate, AuthorUpdate, AuthorPublic
from app.models import Author
import uuid

async def create_author(session: AsyncSession, author_create: AuthorCreate) -> Author:
    db_author = Author.model_validate(author_create)
    session.add(db_author)
    await session.commit()
    await session.refresh(db_author)
    return db_author

async def get_authors(session: AsyncSession, skip: int = 0, limit: int = 10) -> list[Author]:
    statement = select(Author).offset(skip).limit(limit)
    authors = await session.exec(statement)
    return authors.all()

async def get_author_by_id(session: AsyncSession, author_id: uuid.UUID) -> Author | None:
    author = await session.exec(select(Author).where(Author.id == author_id))
    return author.first()

async def get_author_by_name(session: AsyncSession, name: str) -> Author | None:
    author = await session.exec(select(Author).where(Author.name == name))
    return author.first()

async def update_author(session: AsyncSession, author_id: uuid.UUID, author_update: AuthorUpdate) -> Author:
    author_data = author_update.model_dump()
    author_data = {k: v for k, v in author_data.items() if k in author_update.model_fields_set}
    current_author = await get_author_by_id(session, author_id)
    current_author.sqlmodel_update(author_data)
    session.add(current_author)
    await session.commit()
    await session.refresh(current_author)
    return current_author

async def delete_author(session: AsyncSession, author_id: uuid.UUID) -> bool:
    author = await get_author_by_id(session, author_id)
    await session.delete(author)
    await session.commit()
    return True





