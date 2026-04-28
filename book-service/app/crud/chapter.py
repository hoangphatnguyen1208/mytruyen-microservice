from sqlmodel import func, select, insert, update
from sqlmodel.ext.asyncio.session import AsyncSession

from app.models import Chapter, ChapterContent, Book
from app.schema.chapter import ChapterContentCreate, ChapterCreate

from datetime import datetime

async def create_chapter(session: AsyncSession, chapter_in: ChapterCreate) -> bool:
    chapter = Chapter.model_validate(chapter_in)

    session.add(chapter)

    await session.exec(
        update(Book)
        .where(Book.id == chapter.book_id)
        .values(new_chap_at=func.now())
    )

    await session.commit()
    
    return True

async def create_chapter_list(session: AsyncSession, chapter_in_list: list[ChapterCreate]):
    chapters = [Chapter.model_validate(chapter_in) for chapter_in in chapter_in_list]
    statement = insert(Chapter).values([chapter.model_dump() for chapter in chapters])
    await session.exec(statement)
    await session.commit()

async def get_all_chaptters(session: AsyncSession):
    statement = select(Chapter).order_by(Chapter.book_id.asc(), Chapter.index.asc())
    result = await session.exec(statement)
    return result.all()

async def get_chapter_count(session: AsyncSession) -> int:
    statement = select(func.count()).select_from(Chapter)
    result = await session.exec(statement)
    return result.scalar_one()

async def get_chapters_by_book_id(session: AsyncSession, book_id: int) -> list[Chapter]:
    statement = select(Chapter).where(Chapter.book_id == book_id).order_by(Chapter.index.asc())
    result = await session.exec(statement)
    return result.all()

async def get_chapter_by_book_id_and_chapter_index(session: AsyncSession, book_id: int, chapter_index: int) -> Chapter | None:
    statement = select(Chapter).where(Chapter.book_id == book_id, Chapter.index == chapter_index)
    result = await session.exec(statement)
    return result.first()

async def get_chapter_by_id(session: AsyncSession, chapter_id: int) -> Chapter | None:
    statement = select(Chapter).where(Chapter.id == chapter_id)
    result = await session.exec(statement)
    return result.first()

async def update_chapter(session: AsyncSession, chapter_id: int, chapter_in: ChapterCreate) -> Chapter:
    chapter_data = chapter_in.model_dump()
    chapter_data = {k: v for k, v in chapter_data.items() if k in chapter_in.model_fields_set}
    chapter = await get_chapter_by_id(session, chapter_id)
    chapter.sqlmodel_update(chapter_data)
    session.add(chapter)
    await session.commit()
    await session.refresh(chapter)
    return chapter

async def delete_chapter(session: AsyncSession, chapter_id: int) -> bool:
    chapter = await get_chapter_by_id(session, chapter_id)
    await session.delete(chapter)
    await session.commit()
    return True

async def create_chapter_content(session: AsyncSession, content_in: ChapterContentCreate) -> ChapterContent:
    content = ChapterContent.model_validate(content_in)
    session.add(content)
    await session.commit()
    await session.refresh(content)
    return content

async def get_chapter_content(session: AsyncSession) -> int:
    statement = select(func.count()).select_from(ChapterContent)
    result = await session.exec(statement)
    return result.scalar_one()

async def get_chapter_content_by_chapter_id(session: AsyncSession, chapter_id: int) -> ChapterContent | None:
    statement = select(ChapterContent).where(ChapterContent.chapter_id == chapter_id)
    result = await session.exec(statement)
    return result.first()

async def update_chapter_content(session: AsyncSession, chapter_id: int, content_in: ChapterContentCreate) -> ChapterContent:
    content_data = content_in.model_dump()
    content_data = {k: v for k, v in content_data.items() if k in content_in.model_fields_set}
    content = await get_chapter_content_by_chapter_id(session, chapter_id)
    content.sqlmodel_update(content_data)
    session.add(content)
    await session.commit()
    await session.refresh(content)
    return content

async def delete_chapter_content(session: AsyncSession, content_id: int) -> bool:
    content = await get_chapter_content_by_chapter_id(session, content_id)
    await session.delete(content)
    await session.commit()
    return True
