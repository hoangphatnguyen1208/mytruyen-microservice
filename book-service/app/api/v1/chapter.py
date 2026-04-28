from fastapi import APIRouter, status

from app.crud import chapter as crud_chapter, book as crud_book
from app.schema.chapter import ChapterCreate, ChapterRegister, ChapterPublic, ChapterUpdate
from app.schema.chapter import ChapterContentCreate, ChapterContentRegister, ChapterContentPublic, ChapterContentUpdate
from app.api.deps import CurrentAdmin, SessionDep
from app.schema.response import Response, ResponseList

from app.utilities.exceptions.http.exc_400 import (
    http_exc_400_chapter_bad_request,
    http_exc_400_chapter_content_bad_request
)
from app.utilities.exceptions.http.exc_404 import (
    http_exc_404_chapter_not_found_request,
    http_exc_404_chapter_content_not_found_request,
    http_exc_404_book_not_found_request
)

router = APIRouter(prefix="/chapters", tags=["chapter"])

@router.get("", response_model=ResponseList[ChapterPublic])
async def get_all_chapters(session: SessionDep):
    chapters = await crud_chapter.get_all_chaptters(session)
    return ResponseList(status_code=200, success=True, message="All chapters retrieved successfully", data=chapters)

@router.get("/id/{book_id}", response_model=ResponseList[ChapterPublic])
async def get_chapter(session: SessionDep, book_id: int, index: int | None = None) -> ResponseList[ChapterPublic]:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    if index is not None:
        chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, existing_book.id, index)
        if not chapter:
            raise http_exc_404_chapter_not_found_request(string=f"{index}")
        return ResponseList(status_code=200, success=True, message="Chapter retrieved successfully", data=[chapter])
    chapters = await crud_chapter.get_chapters_by_book_id(session, existing_book.id)
    return ResponseList(status_code=200, success=True, message="Chapters retrieved successfully", data=chapters)

@router.post("/id/{book_id}", response_model=Response[None], status_code=status.HTTP_201_CREATED)
async def create_chapter(session: SessionDep, book_id: int, chapter_data: ChapterRegister, current_admin: CurrentAdmin) -> Response[None]:
    existing_book = await crud_book.get_book_by_id(session, book_id)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    existing_chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(
        session, existing_book.id, chapter_data.index
    )
    if existing_chapter:
        raise http_exc_400_chapter_bad_request(slug=chapter_data.index)
    chapter_in = ChapterCreate.model_validate(chapter_data, update={"book_id": existing_book.id, "creator_id": str(current_admin.id)})
    chapter = await crud_chapter.create_chapter(session, chapter_in)
    return Response(status_code=201, success=True, message="Chapter created successfully", data=None)

@router.delete("/id/{chapter_id}", response_model=Response[None])
async def delete_chapter(session: SessionDep, chapter_id: int, current_admin: CurrentAdmin) -> Response[None]:
    existing_chapter = await crud_chapter.get_chapter_by_id(session, chapter_id)
    if not existing_chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_id}")
    await crud_chapter.delete_chapter(session, chapter_id)
    return Response(status_code=200, success=True, message="Chapter deleted successfully", data=None)

@router.patch("/id/{chapter_id}", response_model=Response[ChapterPublic])
async def update_chapter(session: SessionDep, chapter_id: int, chapter_data: ChapterUpdate, current_admin: CurrentAdmin) -> Response[ChapterPublic]:
    existing_chapter = await crud_chapter.get_chapter_by_id(session, chapter_id)
    if not existing_chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_id}")
    updated_chapter = await crud_chapter.update_chapter(session, chapter_id, chapter_data)
    return Response(status_code=200, success=True, message="Chapter updated successfully", data=updated_chapter)

@router.get("/content/id/{book_id}/{index}", response_model=Response[ChapterContentPublic])
async def get_chapter_content(session: SessionDep, book_id: int, index: int) -> Response[ChapterContentPublic]:
    book = await crud_book.get_book_by_id(session, book_id)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(session, chapter.id)
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    return Response(status_code=200, success=True, message="Chapter content retrieved successfully", data=chapter_content)

@router.post("/content/id/{book_id}/{index}", response_model=Response[ChapterContentPublic], status_code=status.HTTP_201_CREATED)
async def create_chapter_content(session: SessionDep, book_id: int, index: int, chapter_content_data: ChapterContentRegister, current_admin: CurrentAdmin) -> Response[ChapterContentPublic]:
    book = await crud_book.get_book_by_id(session, book_id)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter.id}")
    chapter_content_existing = await crud_chapter.get_chapter_content_by_chapter_id(session, chapter.id)
    if chapter_content_existing:
        raise http_exc_400_chapter_content_bad_request(slug=f"{chapter.id}")
    chapter_content_in = ChapterContentCreate.model_validate(
        chapter_content_data,
        update={"chapter_id": chapter.id}
    )
    chapter_content = await crud_chapter.create_chapter_content(session, chapter_content_in)
    return Response(status_code=201, success=True, message="Chapter content created successfully", data=chapter_content)

@router.patch("/content/id/{book_id}/{index}", response_model=Response[ChapterContentPublic])
async def update_chapter_content(session: SessionDep, book_id: int, index: int, chapter_content_data: ChapterContentUpdate, current_admin: CurrentAdmin) -> Response[ChapterContentPublic]:
    book = await crud_book.get_book_by_id(session, book_id)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_id}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter.id}")
    chapter_content_in = ChapterContentUpdate.model_validate(chapter_content_data)
    updated_content = await crud_chapter.update_chapter_content(session, chapter.id, chapter_content_in)
    return Response(status_code=200, success=True, message="Chapter content updated successfully", data=updated_content)

@router.get("/slug/{book_slug}", response_model=ResponseList[ChapterPublic])
async def get_chapter(session: SessionDep, book_slug: str, index: int | None = None) -> ResponseList[ChapterPublic]:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    if index is not None:
        chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, existing_book.id, index)
        if not chapter:
            raise http_exc_404_chapter_not_found_request(string=f"{index}")
        return ResponseList(status_code=200, success=True, message="Chapter retrieved successfully", data=[chapter])
    chapters = await crud_chapter.get_chapters_by_book_id(session, existing_book.id)
    return ResponseList(status_code=200, success=True, message="Chapters retrieved successfully", data=chapters)

@router.delete("/slug/{chapter_id}", response_model=Response[None])
async def delete_chapter(session: SessionDep, chapter_id: int, current_admin: CurrentAdmin) -> Response[None]:
    existing_chapter = await crud_chapter.get_chapter_by_id(session, chapter_id)
    if not existing_chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter_id}")
    await crud_chapter.delete_chapter(session, chapter_id)
    return Response(status_code=200, success=True, message="Chapter deleted successfully", data=None)

@router.post("/slug/{book_slug}", response_model=Response[None], status_code=status.HTTP_201_CREATED)
async def create_chapter(session: SessionDep, book_slug: str, chapter_data: ChapterRegister, current_admin: CurrentAdmin) -> Response[None]:
    existing_book = await crud_book.get_book_by_slug(session, book_slug)
    if not existing_book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    existing_chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(
        session, existing_book.id, chapter_data.index
    )
    if existing_chapter:
        raise http_exc_400_chapter_bad_request(slug=chapter_data.index)
    chapter_in = ChapterCreate.model_validate(chapter_data, update={"book_id": existing_book.id, "creator_id": str(current_admin.id)})
    await crud_chapter.create_chapter(session, chapter_in)
    return Response(status_code=201, success=True, message="Chapter created successfully", data=None)

@router.get("/content/slug/{book_slug}/{index}", response_model=Response[ChapterContentPublic])
async def get_chapter_content(session: SessionDep, book_slug: str, index: int) -> Response[ChapterContentPublic]:
    book = await crud_book.get_book_by_slug(session, book_slug)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{index}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(session, chapter.id)
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    return Response(status_code=200, success=True, message="Chapter content retrieved successfully", data=chapter_content)

@router.post("/content/slug/{book_slug}/{index}", response_model=Response[ChapterContentPublic], status_code=status.HTTP_201_CREATED)
async def create_chapter_content(session: SessionDep, book_slug: str, index: int, chapter_content_data: ChapterContentRegister, current_admin: CurrentAdmin) -> Response[ChapterContentPublic]:
    book = await crud_book.get_book_by_slug(session, book_slug)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter.id}")
    chapter_content_existing = await crud_chapter.get_chapter_content_by_chapter_id(session, chapter.id)
    if chapter_content_existing:
        raise http_exc_400_chapter_content_bad_request(slug=f"{chapter.id}")
    chapter_content_in = ChapterContentCreate.model_validate(
        chapter_content_data,
        update={"chapter_id": chapter.id}
    )
    chapter_content = await crud_chapter.create_chapter_content(session, chapter_content_in)
    return Response(status_code=201, success=True, message="Chapter content created successfully", data=chapter_content)

@router.patch("/content/slug/{book_slug}/{index}", response_model=Response[ChapterContentPublic])
async def update_chapter_content(session: SessionDep, book_slug: str, index: int, chapter_content_data: ChapterContentUpdate, current_admin: CurrentAdmin) -> Response[ChapterContentPublic]:
    book = await crud_book.get_book_by_slug(session, book_slug)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter.id}")
    chapter_content_in = ChapterContentUpdate.model_validate(chapter_content_data)
    updated_content = await crud_chapter.update_chapter_content(session, chapter.id, chapter_content_in)
    return Response(status_code=200, success=True, message="Chapter content updated successfully", data=updated_content)

@router.delete("/content/slug/{book_slug}/{index}", response_model=Response[None])
async def delete_chapter_content(session: SessionDep, book_slug: str, index: int, current_admin: CurrentAdmin) -> Response[None]:
    book = await crud_book.get_book_by_slug(session, book_slug)
    if not book:
        raise http_exc_404_book_not_found_request(string=f"{book_slug}")
    chapter = await crud_chapter.get_chapter_by_book_id_and_chapter_index(session, book.id, index)
    if not chapter:
        raise http_exc_404_chapter_not_found_request(string=f"{chapter.id}")
    chapter_content = await crud_chapter.get_chapter_content_by_chapter_id(session, chapter.id)
    if not chapter_content:
        raise http_exc_404_chapter_content_not_found_request(string=f"{chapter.id}")
    await crud_chapter.delete_chapter_content(session, chapter_content.id)
    return Response(status_code=200, success=True, message="Chapter content deleted successfully", data=None)

