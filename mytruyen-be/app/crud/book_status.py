from sqlmodel import select
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models import BookStatus
from app.schema.book_status import BookStatusCreate, BookStatusUpdate

async def create_book_status(db: AsyncSession, book_status: BookStatusCreate) -> BookStatus:
    db_book_status = BookStatus.model_validate(book_status)
    db.add(db_book_status)
    await db.commit()
    await db.refresh(db_book_status)
    return db_book_status

async def get_book_status_by_id(db: AsyncSession, book_status_id: int) -> BookStatus | None:
    statement = select(BookStatus).where(BookStatus.id == book_status_id)
    result = await db.exec(statement)
    return result.first()

async def get_book_status_by_slug(db: AsyncSession, slug: str) -> BookStatus | None:
    statement = select(BookStatus).where(BookStatus.slug == slug)
    result = await db.exec(statement)
    return result.first()

async def get_book_status_by_name(db: AsyncSession, name: str) -> BookStatus | None:
    statement = select(BookStatus).where(BookStatus.name == name)
    result = await db.exec(statement)
    return result.first()

async def get_book_statuses(db: AsyncSession) -> list[BookStatus]:
    statement = select(BookStatus)
    result = await db.exec(statement)
    return result.all()

async def update_book_status(db: AsyncSession, book_status_id: int, book_status_update: BookStatusUpdate) -> BookStatus:
    book_status_data = book_status_update.model_dump()
    book_status_data = {k: v for k, v in book_status_data.items() if k in book_status_update.model_fields_set}
    current_book_status = await get_book_status_by_id(db, book_status_id)
    current_book_status.sqlmodel_update(book_status_data)
    db.add(current_book_status)
    await db.commit()
    await db.refresh(current_book_status)
    return current_book_status

async def delete_book_status(db: AsyncSession, book_status_id: int) -> bool:
    book_status = await get_book_status_by_id(db, book_status_id)
    await db.delete(book_status)
    await db.commit()
    return True

