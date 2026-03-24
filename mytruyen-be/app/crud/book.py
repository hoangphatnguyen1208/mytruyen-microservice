from sqlmodel.ext.asyncio.session import AsyncSession
from sqlmodel import func, select
from sqlalchemy.orm import selectinload

from app.models import Book, Genre
from app.schema.book import BookCreate, BookUpdate

async def create_book(session: AsyncSession, book_create: BookCreate) -> Book:
    db_book = Book.model_validate(book_create)
    session.add(db_book)
    await session.flush()
    await session.refresh(db_book)
    if book_create.genre_ids:
        genres = await session.exec(select(Genre).where(Genre.id.in_(book_create.genre_ids)))
        db_book.genres = genres.all()
        session.add(db_book)
    await session.commit()
    await session.refresh(db_book)
    return db_book

async def get_book_count(session: AsyncSession) -> int:
    statement = select(func.count()).select_from(Book)
    result = await session.exec(statement)
    return result.scalar_one()

async def get_books(
    session: AsyncSession,
    skip: int = 0,
    limit: int = 10,
    sort: str | None = None,
) -> list[Book]:
    statement = select(Book)
    
    # Apply sorting
    if sort:
        if sort.startswith("-"):
            sort_field = sort[1:]
            statement = statement.order_by(getattr(Book, sort_field).desc())
        else:
            statement = statement.order_by(getattr(Book, sort).asc())
    
    # Apply pagination
    statement = statement.offset(skip).limit(limit)
    
    books = await session.exec(statement)
    return books.all()

async def get_book_by_id(session: AsyncSession, book_id: int) -> Book | None:
    book = await session.exec(select(Book).where(Book.id == book_id))
    return book.first()

async def get_book_by_slug(session: AsyncSession, slug: str) -> Book | None:
    statement = select(Book).where(Book.slug == slug)
    books = await session.exec(statement)
    return books.first()

async def update_book(session: AsyncSession, book_id: int, book: BookUpdate) -> Book:
    book_data = book.model_dump()
    book_data = {k: v for k, v in book_data.items() if k in book.model_fields_set}
    current_book = await get_book_by_id(session, book_id)
    current_book.sqlmodel_update(book_data)
    if 'genre_ids' in book_data:
        genres = await session.exec(select(Genre).where(Genre.id.in_(book_data['genre_ids'])))
        current_book.genres = genres.all()
    session.add(current_book)
    await session.commit()
    await session.refresh(current_book)
    return current_book

async def delete_book(session: AsyncSession, book_id: int) -> bool:
    book = await get_book_by_id(session, book_id)
    await session.delete(book)
    await session.commit()
    return True