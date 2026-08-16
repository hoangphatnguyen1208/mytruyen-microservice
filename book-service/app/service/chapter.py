from app.crud import chapter as crud_chapter, book as crud_book
from app.schema.response import Pagination
from app.models import Chapter, ChapterContent
from app.schema.chapter import (
    ChapterCreate,
    ChapterUpdate,
    ChapterRegister,
    ChapterContentCreate,
    ChapterContentUpdate,
    ChapterContentRegister,
)
from sqlmodel.ext.asyncio.session import AsyncSession

from app.utilities.exceptions.http.exc_400 import (
    http_exc_400_chapter_bad_request,
    http_exc_400_chapter_content_bad_request,
)
from app.utilities.exceptions.http.exc_404 import (
    http_exc_404_chapter_not_found_request,
    http_exc_404_chapter_content_not_found_request,
    http_exc_404_book_not_found_request,
)


async def get_all_chapters(
    session: AsyncSession, limit: int, page: int, sort: str | None = None
) -> tuple[list[Chapter], Pagination]:
    skip = (page - 1) * limit if page > 0 else 0
    chapters = await crud_chapter.get_all_chaptters(session, limit, skip, sort)
    total_items = await crud_chapter.get_chapter_count(session)
    pagination = Pagination(
        page=page,
        size=limit,
        total_items=total_items,
        total_pages=(total_items + limit - 1) // limit if limit > 0 else 1,
    )
    return chapters, pagination


async def get_chapters_by_book_id(
    session: AsyncSession, book_id: int, limit: int, page: int, sort: str | None = None
) -> tuple[list[Chapter], Pagination]:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    skip = (page - 1) * limit if page > 0 else 0
    chapters = await crud_chapter.get_chapters_by_book_id(
        session, book_id, limit, skip, sort
    )
    total_items = await crud_chapter.get_chapter_count_by_book_id(session, book_id)
    pagination = Pagination(
        page=page,
        size=limit,
        total_items=total_items,
        total_pages=(total_items + limit - 1) // limit if limit > 0 else 1,
    )
    return chapters, pagination


async def get_chapters_by_book_slug(
    session: AsyncSession,
    book_slug: str,
    limit: int,
    page: int,
    sort: str | None = None,
) -> tuple[list[Chapter], Pagination]:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    skip = (page - 1) * limit if page > 0 else 0
    chapters = await crud_chapter.get_chapters_by_book_id(
        session, existing_book.id, limit, skip, sort
    )
    total_items = await crud_chapter.get_chapter_count_by_book_id(
        session, existing_book.id
    )
    pagination = Pagination(
        page=page,
        size=limit,
        total_items=total_items,
        total_pages=(total_items + limit - 1) // limit if limit > 0 else 1,
    )
    return chapters, pagination


async def get_chapter_by_book_id_and_index(
    session: AsyncSession, book_id: int, chapter_index: int
) -> Chapter:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, book_id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    return chapter


async def get_chapter_by_book_slug_and_index(
    session: AsyncSession, book_slug: str, chapter_index: int
) -> Chapter:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, existing_book.id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    return chapter


async def create_chapter(session: AsyncSession, chapter_in: ChapterCreate) -> None:
    existing_book = await crud_book.get_book_by_id(session, chapter_in.book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{chapter_in.book_id}")
    existing_chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, chapter_in.book_id, chapter_in.index
    )
    if existing_chapter:
        raise http_exc_400_chapter_bad_request(slug=str(chapter_in.index))
    await crud_chapter.create_chapter(session, chapter_in)


async def create_chapter_by_slug(
    session: AsyncSession,
    book_slug: str,
    creator_id: str,
    chapter_data: ChapterRegister,
) -> None:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    existing_chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, existing_book.id, chapter_data.index
    )
    if existing_chapter:
        raise http_exc_400_chapter_bad_request(slug=str(chapter_data.index))
    chapter_in = ChapterCreate.model_validate(
        chapter_data, update={"book_id": existing_book.id, "creator_id": creator_id}
    )
    await crud_chapter.create_chapter(session, chapter_in)


async def update_chapter(
    session: AsyncSession, chapter_id: int, chapter_in: ChapterUpdate
) -> Chapter:
    existing_chapter = await crud_chapter.get_chapter_by_id(session, chapter_id)
    if not existing_chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_id}")
    updated_chapter = await crud_chapter.update_chapter(session, chapter_id, chapter_in)
    return updated_chapter


async def delete_chapter(session: AsyncSession, chapter_id: int) -> None:
    existing_chapter = await crud_chapter.get_chapter_by_id(session, chapter_id)
    if not existing_chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_id}")
    await crud_chapter.delete_chapter(session, chapter_id)


async def get_chapter_content_by_book_id_and_index(
    session: AsyncSession, book_id: int, chapter_index: int
) -> ChapterContent:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, book_id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    return chapter_content


async def get_chapter_content_by_book_slug_and_index(
    session: AsyncSession, book_slug: str, chapter_index: int
) -> ChapterContent:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, existing_book.id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    return chapter_content


async def create_chapter_content(
    session: AsyncSession,
    book_id: int,
    chapter_index: int,
    creator_id: str,
    chapter_content_data: ChapterContentRegister,
) -> ChapterContent:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, book_id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    existing_chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if existing_chapter_content:
        raise http_exc_400_chapter_content_bad_request(slug=str(chapter.id))
    content_in = ChapterContentCreate.model_validate(
        chapter_content_data,
        update={"chapter_id": chapter.id, "creator_id": creator_id},
    )
    chapter_content = await crud_chapter.create_chapter_content(session, content_in)
    return chapter_content


async def create_chapter_content_by_slug(
    session: AsyncSession,
    book_slug: str,
    chapter_index: int,
    chapter_content_data: ChapterContentRegister,
) -> ChapterContent:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, existing_book.id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    existing_chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if existing_chapter_content:
        raise http_exc_400_chapter_content_bad_request(slug=str(chapter.id))
    content_in = ChapterContentCreate.model_validate(
        chapter_content_data, update={"chapter_id": chapter.id}
    )
    chapter_content = await crud_chapter.create_chapter_content(session, content_in)
    return chapter_content


async def update_chapter_content(
    session: AsyncSession,
    book_id: int,
    chapter_index: int,
    content_in: ChapterContentUpdate,
) -> ChapterContent:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, book_id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    updated_content = await crud_chapter.update_chapter_content(
        session, chapter.id, content_in
    )
    return updated_content


async def update_chapter_content_by_slug(
    session: AsyncSession,
    book_slug: str,
    chapter_index: int,
    content_in: ChapterContentUpdate,
) -> ChapterContent:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, existing_book.id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    updated_content = await crud_chapter.update_chapter_content(
        session, chapter.id, content_in
    )
    return updated_content


async def delete_chapter_content(
    session: AsyncSession, book_id: int, chapter_index: int
) -> None:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, book_id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    await crud_chapter.delete_chapter_content(session, chapter_content.id)


async def delete_chapter_content_by_slug(
    session: AsyncSession, book_slug: str, chapter_index: int
) -> None:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_index(
        session, existing_book.id, chapter_index
    )
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(
        session, chapter.id
    )
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    await crud_chapter.delete_chapter_content(session, chapter_content.id)
