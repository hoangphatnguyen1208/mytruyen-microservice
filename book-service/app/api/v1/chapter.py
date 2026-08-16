from fastapi import APIRouter, status

from app.schema.chapter import (
    ChapterCreate,
    ChapterRegister,
    ChapterPublic,
    ChapterUpdate,
)
from app.schema.chapter import (
    ChapterContentCreate,
    ChapterContentRegister,
    ChapterContentPublic,
    ChapterContentUpdate,
)
from app.api.deps import CurrentAdmin, SessionDep
from app.schema.response import Response, ResponseList, ResponsePage

from app.service import chapter as service_chapter

router = APIRouter(prefix="/chapters", tags=["chapter"])


@router.get("", response_model=ResponsePage[ChapterPublic])
async def get_all_chapters(
    session: SessionDep, limit: int = 10, page: int = 1, sort: str | None = None
):
    chapters, pagination = await service_chapter.get_all_chapters(
        session, limit, page, sort
    )
    return ResponsePage(
        status_code=200,
        success=True,
        message="All chapters retrieved successfully",
        data=chapters,
        pagination=pagination,
    )


@router.get("/id/{book_id}", response_model=ResponsePage[ChapterPublic])
async def get_chapter(
    session: SessionDep,
    book_id: int,
    limit: int = 30,
    page: int = 1,
    sort: str | None = None,
) -> ResponsePage[ChapterPublic]:
    chapters, pagination = await service_chapter.get_chapters_by_book_id(
        session, book_id, limit, page, sort
    )
    return ResponsePage(
        status_code=200,
        success=True,
        message="Chapters retrieved successfully",
        data=chapters,
        pagination=pagination,
    )


@router.get("/id/{book_id}/{index}", response_model=Response[ChapterPublic])
async def get_chapter_by_book_id_and_index(
    session: SessionDep, book_id: int, index: int
):
    chapter = await service_chapter.get_chapter_by_book_id_and_index(
        session, book_id, index
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter retrieved successfully",
        data=chapter,
    )


@router.post(
    "/id/{book_id}", response_model=Response[None], status_code=status.HTTP_201_CREATED
)
async def create_chapter(
    session: SessionDep,
    book_id: int,
    chapter_data: ChapterRegister,
    current_admin: CurrentAdmin,
) -> Response[None]:
    chapter_in = ChapterCreate.model_validate(
        chapter_data, update={"book_id": book_id, "creator_id": str(current_admin.id)}
    )
    await service_chapter.create_chapter(session, chapter_in)
    return Response(
        status_code=201, success=True, message="Chapter created successfully", data=None
    )


@router.delete("/id/{chapter_id}", response_model=Response[None])
async def delete_chapter(
    session: SessionDep, chapter_id: int, current_admin: CurrentAdmin
) -> Response[None]:
    await service_chapter.delete_chapter(session, chapter_id)
    return Response(
        status_code=200, success=True, message="Chapter deleted successfully", data=None
    )


@router.patch("/id/{chapter_id}", response_model=Response[ChapterPublic])
async def update_chapter(
    session: SessionDep,
    chapter_id: int,
    chapter_data: ChapterUpdate,
    current_admin: CurrentAdmin,
) -> Response[ChapterPublic]:
    updated_chapter = await service_chapter.update_chapter(
        session, chapter_id, chapter_data
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter updated successfully",
        data=updated_chapter,
    )


@router.get(
    "/content/id/{book_id}/{index}", response_model=Response[ChapterContentPublic]
)
async def get_chapter_content(
    session: SessionDep, book_id: int, index: int
) -> Response[ChapterContentPublic]:
    chapter_content = await service_chapter.get_chapter_content_by_book_id_and_index(
        session, book_id, index
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter content retrieved successfully",
        data=chapter_content,
    )


@router.post(
    "/content/id/{book_id}/{index}",
    response_model=Response[ChapterContentPublic],
    status_code=status.HTTP_201_CREATED,
)
async def create_chapter_content(
    session: SessionDep,
    book_id: int,
    index: int,
    chapter_content_data: ChapterContentRegister,
    current_admin: CurrentAdmin,
) -> Response[ChapterContentPublic]:
    chapter_content = await service_chapter.create_chapter_content(
        session, book_id, index, str(current_admin.id), chapter_content_data
    )
    return Response(
        status_code=201,
        success=True,
        message="Chapter content created successfully",
        data=chapter_content,
    )


@router.patch(
    "/content/id/{book_id}/{index}", response_model=Response[ChapterContentPublic]
)
async def update_chapter_content(
    session: SessionDep,
    book_id: int,
    index: int,
    chapter_content_data: ChapterContentUpdate,
    current_admin: CurrentAdmin,
) -> Response[ChapterContentPublic]:
    updated_content = await service_chapter.update_chapter_content(
        session, book_id, index, chapter_content_data
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter content updated successfully",
        data=updated_content,
    )


@router.get("/slug/{book_slug}", response_model=ResponsePage[ChapterPublic])
async def get_chapter_by_slug(
    session: SessionDep,
    book_slug: str,
    limit: int = 10,
    page: int = 1,
    sort: str | None = None,
):
    chapters, pagination = await service_chapter.get_chapters_by_book_slug(
        session, book_slug, limit, page, sort
    )
    return ResponsePage(
        status_code=200,
        success=True,
        message="Chapters retrieved successfully",
        data=chapters,
        pagination=pagination,
    )


@router.get("/slug/{book_slug}/{index}", response_model=Response[ChapterPublic])
async def get_chapter_by_book_slug_and_index(
    session: SessionDep, book_slug: str, index: int
):
    chapter = await service_chapter.get_chapter_by_book_slug_and_index(
        session, book_slug, index
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter retrieved successfully",
        data=chapter,
    )


@router.delete("/slug/{chapter_id}", response_model=Response[None])
async def delete_chapter_by_slug(
    session: SessionDep, chapter_id: int, current_admin: CurrentAdmin
) -> Response[None]:
    await service_chapter.delete_chapter(session, chapter_id)
    return Response(
        status_code=200, success=True, message="Chapter deleted successfully", data=None
    )


@router.post(
    "/slug/{book_slug}",
    response_model=Response[None],
    status_code=status.HTTP_201_CREATED,
)
async def create_chapter_by_slug(
    session: SessionDep,
    book_slug: str,
    chapter_data: ChapterRegister,
    current_admin: CurrentAdmin,
) -> Response[None]:
    await service_chapter.create_chapter_by_slug(
        session, book_slug, str(current_admin.id), chapter_data
    )
    return Response(
        status_code=201, success=True, message="Chapter created successfully", data=None
    )


@router.get(
    "/content/slug/{book_slug}/{index}", response_model=Response[ChapterContentPublic]
)
async def get_chapter_content_by_slug(
    session: SessionDep, book_slug: str, index: int
) -> Response[ChapterContentPublic]:
    chapter_content = await service_chapter.get_chapter_content_by_book_slug_and_index(
        session, book_slug, index
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter content retrieved successfully",
        data=chapter_content,
    )


@router.post(
    "/content/slug/{book_slug}/{index}",
    response_model=Response[ChapterContentPublic],
    status_code=status.HTTP_201_CREATED,
)
async def create_chapter_content_by_slug(
    session: SessionDep,
    book_slug: str,
    index: int,
    chapter_content_data: ChapterContentRegister,
    current_admin: CurrentAdmin,
) -> Response[ChapterContentPublic]:
    chapter_content = await service_chapter.create_chapter_content_by_slug(
        session, book_slug, index, chapter_content_data
    )
    return Response(
        status_code=201,
        success=True,
        message="Chapter content created successfully",
        data=chapter_content,
    )


@router.patch(
    "/content/slug/{book_slug}/{index}", response_model=Response[ChapterContentPublic]
)
async def update_chapter_content_by_slug(
    session: SessionDep,
    book_slug: str,
    index: int,
    chapter_content_data: ChapterContentUpdate,
    current_admin: CurrentAdmin,
) -> Response[ChapterContentPublic]:
    updated_content = await service_chapter.update_chapter_content_by_slug(
        session, book_slug, index, chapter_content_data
    )
    return Response(
        status_code=200,
        success=True,
        message="Chapter content updated successfully",
        data=updated_content,
    )


@router.delete("/content/slug/{book_slug}/{index}", response_model=Response[None])
async def delete_chapter_content_by_slug(
    session: SessionDep, book_slug: str, index: int, current_admin: CurrentAdmin
) -> Response[None]:
    await service_chapter.delete_chapter_content_by_slug(session, book_slug, index)
    return Response(
        status_code=200,
        success=True,
        message="Chapter content deleted successfully",
        data=None,
    )
